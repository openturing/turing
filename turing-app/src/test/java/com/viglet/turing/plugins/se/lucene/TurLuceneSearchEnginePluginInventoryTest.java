/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.plugins.se.lucene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Optional;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.lucene.TurLucene;
import com.viglet.turing.lucene.TurLuceneInstance;
import com.viglet.turing.lucene.TurLuceneInstanceProcess;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;

/**
 * T1029 / §XX.16 — the Lucene inventory scan walks the {@code id} term
 * dictionary, so the keyset cursor and the source filter have to be exercised
 * against a real index; a mocked searcher would prove nothing about either.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurLuceneSearchEnginePluginInventoryTest {

    private static final long JAN = 1_700_000_000_000L;
    private static final long FEB = 1_700_900_000_000L;

    @Mock
    private TurLucene turLucene;
    @Mock
    private TurLuceneInstanceProcess turLuceneInstanceProcess;

    @InjectMocks
    private TurLuceneSearchEnginePlugin plugin;

    private TurSNSiteLocale givenIndex(TurLuceneInstance instance) {
        TurSNSiteLocale siteLocale = new TurSNSiteLocale();
        when(turLuceneInstanceProcess.initLuceneInstance(siteLocale))
                .thenReturn(Optional.of(instance));
        return siteLocale;
    }

    /** Three docs from two sources; ids chosen so lexicographic order is a-b-c. */
    private static TurLuceneInstance inMemoryInstance() throws Exception {
        Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()));
        addDoc(writer, "a-page", "Page", "aem", JAN, "sha256:aaa");
        addDoc(writer, "b-page", "Page", "aem", FEB, null);
        addDoc(writer, "c-asset", "Asset", "sharepoint", null, "sha256:ccc");
        writer.commit();
        return new TurLuceneInstance(writer, dir, Path.of("mem-inventory"), new FacetsConfig());
    }

    private static void addDoc(IndexWriter writer, String id, String type, String source,
            Long modifiedEpochMs, String checksum) throws Exception {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new StringField("type", type, Field.Store.YES));
        doc.add(new StringField("source_apps", source, Field.Store.YES));
        if (modifiedEpochMs != null) {
            // Mirrors TurLuceneDocumentHandler: DATE fields are stored as epoch millis.
            doc.add(new StoredField("modification_date", modifiedEpochMs));
        }
        if (checksum != null) {
            doc.add(new StringField("checksum", checksum, Field.Store.YES));
        }
        writer.addDocument(doc);
    }

    @Test
    @DisplayName("The whole index is enumerated in id order with normalised versions")
    void enumeratesEverythingInIdOrder() throws Exception {
        var siteLocale = givenIndex(inMemoryInstance());

        var page = plugin.retrieveInventoryPage(siteLocale, null, null, 10);

        assertThat(page.supported()).isTrue();
        assertThat(page.entries()).extracting("id").containsExactly("a-page", "b-page", "c-asset");
        assertThat(page.entries().get(0).version()).isEqualTo(JAN);
        assertThat(page.entries().get(0).versionField()).isEqualTo("modification_date");
        assertThat(page.entries().get(0).type()).isEqualTo("Page");
        assertThat(page.entries().get(2).version()).isNull();
        assertThat(page.remaining()).isEqualTo(3L);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isEqualTo("c-asset");
    }

    @Test
    @DisplayName("The source filter reports only that connector's documents")
    void filtersBySource() throws Exception {
        var siteLocale = givenIndex(inMemoryInstance());

        var page = plugin.retrieveInventoryPage(siteLocale, "aem", null, 10);

        assertThat(page.entries()).extracting("id").containsExactly("a-page", "b-page");
        assertThat(page.remaining()).isEqualTo(2L);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    @DisplayName("The cursor resumes strictly after the last id read, never repeating it")
    void cursorResumesWithoutRepeating() throws Exception {
        var siteLocale = givenIndex(inMemoryInstance());

        var first = plugin.retrieveInventoryPage(siteLocale, null, null, 2);
        assertThat(first.entries()).extracting("id").containsExactly("a-page", "b-page");
        assertThat(first.hasMore()).isTrue();
        assertThat(first.nextCursor()).isEqualTo("b-page");

        var second = plugin.retrieveInventoryPage(siteLocale, null, first.nextCursor(), 2);
        assertThat(second.entries()).extracting("id").containsExactly("c-asset");
        assertThat(second.remaining()).isEqualTo(1L);
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    @DisplayName("A cursor past the last id ends the sweep instead of wrapping around")
    void cursorPastTheEndTerminates() throws Exception {
        var siteLocale = givenIndex(inMemoryInstance());

        var page = plugin.retrieveInventoryPage(siteLocale, null, "z-last", 10);

        assertThat(page.entries()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("A cursor that is not itself an id still resumes at the next one")
    void cursorNeedNotBeAnExistingId() throws Exception {
        var siteLocale = givenIndex(inMemoryInstance());

        var page = plugin.retrieveInventoryPage(siteLocale, null, "b", 10);

        assertThat(page.entries()).extracting("id").containsExactly("b-page", "c-asset");
    }

    @Test
    @DisplayName("A deleted document drops out of the inventory, so an audit sees the delete")
    void deletedDocumentsAreNotReported() throws Exception {
        TurLuceneInstance instance = inMemoryInstance();
        instance.getWriter().deleteDocuments(new Term("id", "b-page"));
        instance.getWriter().commit();
        var siteLocale = givenIndex(instance);

        var page = plugin.retrieveInventoryPage(siteLocale, null, null, 10);

        assertThat(page.entries()).extracting("id").containsExactly("a-page", "c-asset");
        assertThat(page.remaining()).isEqualTo(2L);
    }

    // ---- T1034 / §XX.19 — the indexed checksum ------------------------------

    @Test
    @DisplayName("An indexed checksum is reported per document, and its absence is honest")
    void inventoryReportsTheChecksum() throws Exception {
        var siteLocale = givenIndex(inMemoryInstance());

        var page = plugin.retrieveInventoryPage(siteLocale, null, null, 10);

        assertThat(page.entries()).extracting("id", "checksum").containsExactly(
                org.assertj.core.groups.Tuple.tuple("a-page", "sha256:aaa"),
                // Indexed before T1034, or by a connector that computes none.
                org.assertj.core.groups.Tuple.tuple("b-page", null),
                org.assertj.core.groups.Tuple.tuple("c-asset", "sha256:ccc"));
    }

    @Test
    @DisplayName("A document with a checksum and no date is comparable on the checksum alone")
    void checksumCarriesADocumentWithNoDate() throws Exception {
        var siteLocale = givenIndex(inMemoryInstance());

        var page = plugin.retrieveInventoryPage(siteLocale, "sharepoint", null, 10);

        var entry = page.entries().get(0);
        assertThat(entry.id()).isEqualTo("c-asset");
        assertThat(entry.version()).isNull();
        assertThat(entry.checksum()).isEqualTo("sha256:ccc");
    }

    // ---- T1033 / §D.13 — cursor-paged export read ---------------------------

    @Test
    @DisplayName("The export page comes back id-ascending, so the last id is a valid cursor")
    void exportPageIsIdAscending() throws Exception {
        var siteLocale = givenIndex(inMemoryInstance());

        var page = plugin.retrieveDocumentPageAfter(siteLocale, java.util.Map.of(), null, 10);

        assertThat(page).extracting(doc -> doc.get("id"))
                .containsExactly("a-page", "b-page", "c-asset");
        assertThat(page.get(0)).containsEntry("type", "Page");
    }

    @Test
    @DisplayName("The cursor resumes strictly after the id it names")
    void exportCursorResumes() throws Exception {
        var siteLocale = givenIndex(inMemoryInstance());

        var first = plugin.retrieveDocumentPageAfter(siteLocale, java.util.Map.of(), null, 2);
        assertThat(first).extracting(doc -> doc.get("id")).containsExactly("a-page", "b-page");

        var second = plugin.retrieveDocumentPageAfter(siteLocale, java.util.Map.of(), "b-page", 2);
        assertThat(second).extracting(doc -> doc.get("id")).containsExactly("c-asset");

        assertThat(plugin.retrieveDocumentPageAfter(siteLocale, java.util.Map.of(), "c-asset", 2))
                .isEmpty();
    }

    @Test
    @DisplayName("Only the site's declared fields are projected, plus id and type")
    void exportPageProjectsDeclaredFieldsOnly() throws Exception {
        var siteLocale = givenIndex(inMemoryInstance());

        var page = plugin.retrieveDocumentPageAfter(siteLocale, java.util.Map.of(), null, 1);

        // source_apps is indexed but not declared on the site, so it is left out.
        assertThat(page.get(0)).containsOnlyKeys("id", "type");
    }

    @Test
    @DisplayName("Lucene advertises cursor paging, so the export never falls back to offsets")
    void luceneAdvertisesCursorPaging() {
        assertThat(plugin.supportsCursorPaging()).isTrue();
    }

    @Test
    @DisplayName("An empty index answers empty, and a missing one answers unsupported")
    void emptyAndMissingIndexesAreDistinct() throws Exception {
        Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()));
        writer.commit();
        var siteLocale = givenIndex(
                new TurLuceneInstance(writer, dir, Path.of("mem-empty"), new FacetsConfig()));

        var page = plugin.retrieveInventoryPage(siteLocale, null, null, 10);
        assertThat(page.entries()).isEmpty();
        assertThat(page.supported()).isTrue();

        var missing = new TurSNSiteLocale();
        when(turLuceneInstanceProcess.initLuceneInstance(missing)).thenReturn(Optional.empty());
        assertThat(plugin.retrieveInventoryPage(missing, null, null, 10).supported()).isFalse();
    }
}

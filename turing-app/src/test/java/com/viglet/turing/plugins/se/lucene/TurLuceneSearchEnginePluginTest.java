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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.lucene.index.IndexWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.lucene.TurLucene;
import com.viglet.turing.lucene.TurLuceneInstance;
import com.viglet.turing.lucene.TurLuceneInstanceProcess;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.se.result.TurSEResults;

/**
 * Tests for TurLuceneSearchEnginePlugin.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurLuceneSearchEnginePluginTest {

    @Mock
    private TurLucene turLucene;
    @Mock
    private TurLuceneInstanceProcess turLuceneInstanceProcess;

    @InjectMocks
    private TurLuceneSearchEnginePlugin plugin;

    private TurSNSiteSearchContext context;

    @BeforeEach
    void setUp() {
        TurSNConfig config = new TurSNConfig();
        TurSEParameters parameters = new TurSEParameters(new TurSNSearchParams());
        context = new TurSNSiteSearchContext("site-lc", config, parameters, Locale.ENGLISH,
                URI.create("http://localhost"));
    }

    // ---- getPluginType -------------------------------------------------------

    @Test
    void shouldExposePluginTypeAsLucene() {
        assertEquals("lucene", plugin.getPluginType());
    }

    // ---- retrieveSearchResults -----------------------------------------------

    @Test
    void shouldRetrieveSearchResults() {
        TurLuceneInstance instance = mock(TurLuceneInstance.class);
        TurSEResults results = TurSEResults.builder().queryString("q-lc").build();

        when(turLuceneInstanceProcess.initLuceneInstance("site-lc", Locale.ENGLISH))
                .thenReturn(Optional.of(instance));
        when(turLucene.retrieveLuceneFromSN(instance, context)).thenReturn(Optional.of(results));

        Optional<TurSEResults> response = plugin.retrieveSearchResults(context);

        assertTrue(response.isPresent());
        assertEquals("q-lc", response.get().getQueryString());
        verify(turLucene).retrieveLuceneFromSN(instance, context);
    }

    @Test
    void shouldReturnEmptyWhenInstanceCannotBeInitialized() {
        when(turLuceneInstanceProcess.initLuceneInstance("site-lc", Locale.ENGLISH))
                .thenReturn(Optional.empty());

        Optional<TurSEResults> response = plugin.retrieveSearchResults(context);

        assertFalse(response.isPresent());
    }

    // ---- retrieveFacetResults ------------------------------------------------

    @Test
    void shouldRetrieveFacetResults() {
        TurLuceneInstance instance = mock(TurLuceneInstance.class);
        TurSEResults results = TurSEResults.builder().queryString("facet-lc").build();

        when(turLuceneInstanceProcess.initLuceneInstance("site-lc", Locale.ENGLISH))
                .thenReturn(Optional.of(instance));
        when(turLucene.retrieveFacetLuceneFromSN(instance, context, "type"))
                .thenReturn(Optional.of(results));

        Optional<TurSEResults> response = plugin.retrieveFacetResults(context, "type");

        assertTrue(response.isPresent());
        assertEquals("facet-lc", response.get().getQueryString());
    }

    @Test
    void shouldReturnEmptyFacetResultsWhenInstanceUnavailable() {
        when(turLuceneInstanceProcess.initLuceneInstance("site-lc", Locale.ENGLISH))
                .thenReturn(Optional.empty());

        Optional<TurSEResults> response = plugin.retrieveFacetResults(context, "type");

        assertFalse(response.isPresent());
    }

    // ---- indexDocument -------------------------------------------------------

    @Test
    void shouldIndexDocumentSuccessfully() {
        TurLuceneInstance instance = mock(TurLuceneInstance.class);
        TurSNSite site = new TurSNSite();
        site.setName("site-lc");
        Map<String, Object> attrs = Map.of("title", "doc1");

        when(turLuceneInstanceProcess.initLuceneInstance("site-lc", Locale.ENGLISH))
                .thenReturn(Optional.of(instance));

        boolean result = plugin.indexDocument(site, Locale.ENGLISH, attrs);

        assertTrue(result);
        verify(turLucene).indexing(instance, site, attrs);
    }

    @Test
    void shouldReturnFalseWhenIndexDocumentInstanceUnavailable() {
        TurSNSite site = new TurSNSite();
        site.setName("site-lc");

        when(turLuceneInstanceProcess.initLuceneInstance("site-lc", Locale.ENGLISH))
                .thenReturn(Optional.empty());

        boolean result = plugin.indexDocument(site, Locale.ENGLISH, Collections.emptyMap());

        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenIndexingThrowsNonDocValuesException() {
        TurLuceneInstance instance = mock(TurLuceneInstance.class);
        TurSNSite site = new TurSNSite();
        site.setName("site-lc");
        Map<String, Object> attrs = Map.of("title", "doc1");

        when(turLuceneInstanceProcess.initLuceneInstance("site-lc", Locale.ENGLISH))
                .thenReturn(Optional.of(instance));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("some other error"))
                .when(turLucene).indexing(instance, site, attrs);

        boolean result = plugin.indexDocument(site, Locale.ENGLISH, attrs);

        assertFalse(result);
    }

    @Test
    void shouldRetryWhenDocValuesConflictDetected() {
        TurLuceneInstance instance = mock(TurLuceneInstance.class);
        when(instance.getIndexPath()).thenReturn(Path.of("/tmp/test-index"));

        TurLuceneInstance freshInstance = mock(TurLuceneInstance.class);
        TurSNSite site = new TurSNSite();
        site.setName("site-lc");
        Map<String, Object> attrs = Map.of("title", "doc1");

        when(turLuceneInstanceProcess.initLuceneInstance("site-lc", Locale.ENGLISH))
                .thenReturn(Optional.of(instance))
                .thenReturn(Optional.of(freshInstance));

        org.mockito.Mockito.doThrow(new IllegalArgumentException("doc values type mismatch"))
                .when(turLucene).indexing(instance, site, attrs);

        boolean result = plugin.indexDocument(site, Locale.ENGLISH, attrs);

        assertTrue(result);
        verify(turLuceneInstanceProcess).resetInstance(Path.of("/tmp/test-index"));
        verify(turLucene).indexing(freshInstance, site, attrs);
    }

    // ---- deIndex --------------------------------------------------------------

    @Test
    void shouldDeIndexDocumentSuccessfully() {
        TurLuceneInstance instance = mock(TurLuceneInstance.class);
        TurSNSite site = new TurSNSite();
        site.setName("site-lc");

        when(turLuceneInstanceProcess.initLuceneInstance("site-lc", Locale.ENGLISH))
                .thenReturn(Optional.of(instance));

        boolean result = plugin.deIndex(site, Locale.ENGLISH, "doc-789");

        assertTrue(result);
        verify(turLucene).deIndexing(instance, "doc-789");
    }

    @Test
    void shouldReturnFalseWhenDeIndexInstanceUnavailable() {
        TurSNSite site = new TurSNSite();
        site.setName("site-lc");

        when(turLuceneInstanceProcess.initLuceneInstance("site-lc", Locale.ENGLISH))
                .thenReturn(Optional.empty());

        boolean result = plugin.deIndex(site, Locale.ENGLISH, "doc-789");

        assertFalse(result);
    }

    // ---- deIndexByType -------------------------------------------------------

    @Test
    void shouldDeIndexByTypeSuccessfully() {
        TurLuceneInstance instance = mock(TurLuceneInstance.class);
        TurSNSite site = new TurSNSite();
        site.setName("site-lc");

        when(turLuceneInstanceProcess.initLuceneInstance("site-lc", Locale.ENGLISH))
                .thenReturn(Optional.of(instance));

        boolean result = plugin.deIndexByType(site, Locale.ENGLISH, "news");

        assertTrue(result);
        verify(turLucene).deIndexingByType(instance, "news");
    }

    @Test
    void shouldReturnFalseWhenDeIndexByTypeInstanceUnavailable() {
        TurSNSite site = new TurSNSite();
        site.setName("site-lc");

        when(turLuceneInstanceProcess.initLuceneInstance("site-lc", Locale.ENGLISH))
                .thenReturn(Optional.empty());

        boolean result = plugin.deIndexByType(site, Locale.ENGLISH, "news");

        assertFalse(result);
    }

    // ---- commit --------------------------------------------------------------

    @Test
    void shouldReturnTrueForCommit() {
        TurSNSite site = new TurSNSite();
        site.setName("site-lc");

        assertTrue(plugin.commit(site, Locale.ENGLISH));
    }

    // ---- getDocumentTotal ----------------------------------------------------

    @Test
    void shouldReturnDocumentTotal() throws Exception {
        TurLuceneInstance instance = mock(TurLuceneInstance.class);
        IndexWriter writer = mock(IndexWriter.class);
        TurSNSiteLocale siteLocale = new TurSNSiteLocale();

        when(turLuceneInstanceProcess.initLuceneInstance(siteLocale))
                .thenReturn(Optional.of(instance));
        when(instance.getWriter()).thenReturn(writer);
        var ctor = org.apache.lucene.index.IndexWriter.DocStats.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        var docStats = (org.apache.lucene.index.IndexWriter.DocStats) ctor.newInstance(0, 55);
        when(writer.getDocStats()).thenReturn(docStats);

        long total = plugin.getDocumentTotal(siteLocale);

        assertEquals(55L, total);
    }

    @Test
    void shouldReturnZeroWhenDocumentTotalInstanceUnavailable() {
        TurSNSiteLocale siteLocale = new TurSNSiteLocale();

        when(turLuceneInstanceProcess.initLuceneInstance(siteLocale))
                .thenReturn(Optional.empty());

        long total = plugin.getDocumentTotal(siteLocale);

        assertEquals(0L, total);
    }

    @Test
    void shouldReturnZeroWhenGetDocumentTotalThrows() {
        TurLuceneInstance instance = mock(TurLuceneInstance.class);
        TurSNSiteLocale siteLocale = new TurSNSiteLocale();

        when(turLuceneInstanceProcess.initLuceneInstance(siteLocale))
                .thenReturn(Optional.of(instance));
        when(instance.getWriter()).thenThrow(new RuntimeException("index closed"));

        long total = plugin.getDocumentTotal(siteLocale);

        assertEquals(0L, total);
    }

    // ---- Schema management (schema-less no-ops) ------------------------------

    @Test
    void shouldNoOpForAddOrUpdateField() {
        TurSEInstance seInstance = new TurSEInstance();
        assertDoesNotThrow(() ->
                plugin.addOrUpdateField(seInstance, "idx", "title", TurSEFieldType.TEXT, true, false, true));
    }

    @Test
    void shouldNoOpForDeleteField() {
        TurSEInstance seInstance = new TurSEInstance();
        assertDoesNotThrow(() ->
                plugin.deleteField(seInstance, "idx", "title", TurSEFieldType.TEXT));
    }

    @Test
    void shouldReturnFalseForFieldExists() {
        TurSEInstance seInstance = new TurSEInstance();
        assertFalse(plugin.fieldExists(seInstance, "idx", "title"));
    }

    // ---- getSystemInfo -------------------------------------------------------

    @Test
    void shouldReturnSystemInfoWithLuceneEngine() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("/data/lucene");

        Map<String, String> info = plugin.getSystemInfo(seInstance);

        assertEquals("Apache Lucene (embedded)", info.get("engine"));
        assertEquals("UP", info.get("status"));
        assertNotNull(info.get("version"));
        assertEquals("/data/lucene", info.get("indexPath"));
    }

    // ---- createIndex / deleteIndex / clearIndex / indexExists / listIndexes ---

    @Test
    void shouldDelegateCreateIndex() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("/tmp/lucene-test");
        TurSNSiteLocale siteLocale = new TurSNSiteLocale();
        siteLocale.setLanguage(Locale.ENGLISH);

        // Static calls to TurLuceneUtils - just ensure no exception
        assertDoesNotThrow(() ->
                plugin.createIndex(seInstance, siteLocale, "my-core", Collections.emptyMap()));
    }

    @Test
    void shouldDelegateDeleteIndex() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("/tmp/lucene-test");

        assertDoesNotThrow(() -> plugin.deleteIndex(seInstance, "my-core"));
    }

    @Test
    void shouldDelegateClearIndex() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("/tmp/lucene-test");

        assertDoesNotThrow(() -> plugin.clearIndex(seInstance, "my-core"));
    }

    @Test
    void shouldDelegateIndexExists() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("/tmp/lucene-test-nonexistent");

        boolean exists = plugin.indexExists(seInstance, "my-core");

        assertFalse(exists);
    }

    @Test
    void shouldDelegateListIndexes() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("/tmp/lucene-test-nonexistent");

        var indexes = plugin.listIndexes(seInstance);

        assertNotNull(indexes);
    }
}

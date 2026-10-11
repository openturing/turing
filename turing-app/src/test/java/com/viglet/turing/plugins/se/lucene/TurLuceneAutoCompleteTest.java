package com.viglet.turing.plugins.se.lucene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.lucene.TurLuceneInstance;
import com.viglet.turing.lucene.TurLuceneInstanceProcess;
import com.viglet.turing.lucene.TurLucenePerFieldAnalyzer;
import com.viglet.turing.lucene.TurLuceneQueryBuilder;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;

/**
 * Autocomplete on the Lucene engine draws its suggestions only from the
 * site's free-text fields (T1076): a slug, URL or id stored on the matched
 * document is never offered.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurLuceneAutoCompleteTest {

    private Directory directory;
    private TurLuceneInstance instance;
    private TurLuceneSearchEnginePlugin plugin;
    private TurSNSiteFieldExtRepository fieldRepository;
    private TurSNSite site;

    @BeforeEach
    void setUp() throws Exception {
        directory = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(directory,
                new IndexWriterConfig(new TurLucenePerFieldAnalyzer()));
        Document doc = new Document();
        doc.add(new StringField("id", "motorista-entregas-123", Field.Store.YES));
        doc.add(new TextField("title", "Motorista de entregas", Field.Store.YES));
        doc.add(new TextField("title_ac", "Motorista de entregas", Field.Store.NO));
        doc.add(new StringField("occupation", "motoristas-de-veiculos", Field.Store.YES));
        doc.add(new TextField("occupation_ac", "motoristas-de-veiculos", Field.Store.NO));
        doc.add(new StringField("url", "https://example.com/motorista-entregas", Field.Store.YES));
        writer.addDocument(doc);
        writer.commit();
        instance = new TurLuceneInstance(writer, directory, Path.of("ac-index"), new FacetsConfig());

        site = new TurSNSite();
        site.setName("vagas");
        fieldRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        when(siteRepository.findByNameIgnoreCase("vagas")).thenReturn(Optional.of(site));
        TurLuceneInstanceProcess instanceProcess = mock(TurLuceneInstanceProcess.class);
        when(instanceProcess.initLuceneInstance(any(String.class), any(Locale.class))).thenReturn(Optional.of(instance));

        TurLuceneQueryBuilder queryBuilder = new TurLuceneQueryBuilder(fieldRepository, null, null,
                null, null);
        plugin = new TurLuceneSearchEnginePlugin(null, instanceProcess, queryBuilder, siteRepository,
                fieldRepository, null, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        instance.close();
    }

    private static TurSNSiteFieldExt field(String name, TurSEFieldType type) {
        TurSNSiteFieldExt ext = new TurSNSiteFieldExt();
        ext.setName(name);
        ext.setType(type);
        return ext;
    }

    @Test
    void suggestsOnlyWordsOfTextFields() {
        when(fieldRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(
                field("id", TurSEFieldType.STRING),
                field("title", TurSEFieldType.TEXT),
                field("occupation", TurSEFieldType.STRING),
                field("url", TurSEFieldType.STRING)));

        assertEquals(List.of("motorista"), plugin.autoComplete("vagas", "moto", Locale.of("pt", "BR"), 10));
    }

    @Test
    void fallsBackToTitleWhenTheSiteHasNoTextField() {
        when(fieldRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(
                field("occupation", TurSEFieldType.STRING)));

        assertEquals(List.of("motorista"), plugin.autoComplete("vagas", "moto", Locale.of("pt", "BR"), 10));
    }
}

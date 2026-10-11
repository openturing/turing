package com.viglet.turing.plugins.se.lucene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.lucene.TurLuceneInstance;
import com.viglet.turing.lucene.TurLuceneInstanceProcess;
import com.viglet.turing.lucene.TurLucenePerFieldAnalyzer;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;

/**
 * T1077 — a document read by id on the Lucene engine carries every stored
 * field, whole, in the order the ids were asked.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurLuceneStoredDocumentsTest {

    private static final String LONG_TEXT = "Entregas de mercadorias na região de Campinas com veículo "
            + "da empresa. CNH categoria B.";

    private TurLuceneInstance instance;
    private TurLuceneSearchEnginePlugin plugin;
    private final TurSNSiteLocale siteLocale = new TurSNSiteLocale();

    @BeforeEach
    void setUp() throws Exception {
        ByteBuffersDirectory directory = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new TurLucenePerFieldAnalyzer()));
        writer.addDocument(job("fx-motorista-campinas", LONG_TEXT, 3200));
        writer.addDocument(job("entregador-sp", "Entregador em São Paulo.", 2100));
        writer.commit();
        instance = new TurLuceneInstance(writer, directory, Path.of("stored-index"), new FacetsConfig());

        TurLuceneInstanceProcess instanceProcess = mock(TurLuceneInstanceProcess.class);
        when(instanceProcess.initLuceneInstance(any(TurSNSiteLocale.class))).thenReturn(Optional.of(instance));
        plugin = new TurLuceneSearchEnginePlugin(null, instanceProcess, null, null, null, null, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        instance.close();
    }

    private static Document job(String id, String text, int salary) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new TextField("text", text, Field.Store.YES));
        doc.add(new StringField("tag", "cnh-b", Field.Store.YES));
        doc.add(new StringField("tag", "veiculo", Field.Store.YES));
        doc.add(new StoredField("salary", salary));
        doc.add(new StringField("$facets", "internal", Field.Store.YES));
        return doc;
    }

    @Test
    void returnsEveryStoredFieldWhole() {
        List<Map<String, Object>> docs = plugin.getStoredDocumentsByIds(siteLocale,
                List.of("fx-motorista-campinas"));

        assertEquals(1, docs.size());
        Map<String, Object> doc = docs.getFirst();
        assertEquals(LONG_TEXT, doc.get("text"));
        assertEquals(3200, doc.get("salary"));
        assertEquals(List.of("cnh-b", "veiculo"), doc.get("tag"));
        assertFalse(doc.containsKey("$facets"));
    }

    @Test
    void keepsTheAskedOrderAndDropsUnknownIds() {
        List<Map<String, Object>> docs = plugin.getStoredDocumentsByIds(siteLocale,
                List.of("missing", "entregador-sp", "fx-motorista-campinas"));

        assertEquals(List.of("entregador-sp", "fx-motorista-campinas"),
                docs.stream().map(d -> d.get("id")).toList());
    }
}

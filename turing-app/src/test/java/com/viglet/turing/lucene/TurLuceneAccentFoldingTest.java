package com.viglet.turing.lucene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;

/**
 * T1080 — an accented and an unaccented spelling match each other on the
 * Lucene engine, and an index written before folding keeps answering the
 * accented spelling.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurLuceneAccentFoldingTest {

    private final TurSNSiteFieldExtRepository fieldRepository = mock(TurSNSiteFieldExtRepository.class);
    private final TurLuceneQueryBuilder queryBuilder =
            new TurLuceneQueryBuilder(fieldRepository, null, null, null, null);
    private final TurSNSite site = new TurSNSite();

    TurLuceneAccentFoldingTest() {
        TurSNSiteFieldExt text = new TurSNSiteFieldExt();
        text.setName("text");
        text.setType(TurSEFieldType.TEXT);
        when(fieldRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(text));
    }

    private Set<String> search(Analyzer indexAnalyzer, String q) throws Exception {
        try (Directory directory = new ByteBuffersDirectory();
                IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(indexAnalyzer))) {
            add(writer, "saude", "Hospital Boa Saúde contrata técnico de enfermagem");
            add(writer, "logistica", "Analista de logística em São Paulo");
            writer.commit();
            try (DirectoryReader reader = DirectoryReader.open(writer)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                Set<String> ids = new TreeSet<>();
                for (ScoreDoc hit : searcher.search(query(q), 10).scoreDocs) {
                    ids.add(searcher.storedFields().document(hit.doc).get("id"));
                }
                return ids;
            }
        }
    }

    private Query query(String q) {
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ(q);
        params.setRows(10);
        params.setP(1);
        return queryBuilder.buildQuery(site, new TurSEParameters(params));
    }

    private static void add(IndexWriter writer, String id, String text) throws Exception {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new SortedSetDocValuesField("id", new BytesRef(id)));
        doc.add(new TextField("text", text, Field.Store.YES));
        writer.addDocument(doc);
    }

    @Test
    void unaccentedQueryFindsTheAccentedSpelling() throws Exception {
        TurLucenePerFieldAnalyzer analyzer = new TurLucenePerFieldAnalyzer();
        assertEquals(Set.of("saude"), search(analyzer, "saude"));
        assertEquals(Set.of("saude"), search(analyzer, "boa saude"));
        assertEquals(Set.of("saude"), search(analyzer, "tecnico"));
        assertEquals(Set.of("logistica"), search(analyzer, "logistica sao paulo"));
    }

    @Test
    void accentedQueryStillFindsTheAccentedSpelling() throws Exception {
        assertEquals(Set.of("saude"), search(new TurLucenePerFieldAnalyzer(), "saúde"));
    }

    @Test
    void anIndexWrittenBeforeFoldingKeepsAnsweringTheAccentedSpelling() throws Exception {
        // Not re-indexed yet: only the accented terms are in it.
        assertEquals(Set.of("saude"), search(new StandardAnalyzer(), "saúde"));
        assertEquals(Set.of("logistica"), search(new StandardAnalyzer(), "logística"));
    }

    @Test
    void highlightingMarksTheStoredSpellingForAnUnaccentedQuery() throws Exception {
        try (Directory directory = new ByteBuffersDirectory();
                IndexWriter writer = new IndexWriter(directory,
                        new IndexWriterConfig(new TurLucenePerFieldAnalyzer()))) {
            add(writer, "saude", "Hospital Boa Saúde contrata");
            writer.commit();
            try (DirectoryReader reader = DirectoryReader.open(writer)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                Query q = query("saude");
                TopDocs topDocs = searcher.search(q, 10);
                String[] snippets = new UnifiedHighlighter.Builder(searcher, new TurLuceneFoldingAnalyzer())
                        .build().highlight("text", q, topDocs, 1);
                assertTrue(snippets[0].contains("<b>Saúde</b>"), snippets[0]);
            }
        }
    }
}

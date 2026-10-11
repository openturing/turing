package com.viglet.turing.lucene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;

/**
 * T1065 — a field's search weight lets a title match outrank the same word in
 * body text on the embedded Lucene engine.
 */
@ExtendWith(MockitoExtension.class)
class TurLuceneFieldWeightTest {

    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    @InjectMocks
    private TurLuceneQueryBuilder queryBuilder;

    private final TurSNSite site = new TurSNSite();

    private void fields(Float titleWeight) {
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(
                TurSNSiteFieldExt.builder().name("title").type(TurSEFieldType.TEXT)
                        .searchWeight(titleWeight).build(),
                TurSNSiteFieldExt.builder().name("text").type(TurSEFieldType.TEXT).build()));
    }

    private static Document doc(String id, String title, String text) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new TextField("text", text, Field.Store.YES));
        return doc;
    }

    private List<String> search(String q) throws Exception {
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ(q);
        Query query = queryBuilder.buildQuery(site, new TurSEParameters(params, new TurSNSitePostParamsBean()));
        try (Directory dir = new ByteBuffersDirectory()) {
            try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
                // The body names java three times; the other mentions it once, in the title.
                writer.addDocument(doc("in-body", "backend developer opening",
                        "java java java services for the platform team"));
                writer.addDocument(doc("in-title", "java developer opening",
                        "services for the platform team, building and running the product every day"));
            }
            try (DirectoryReader reader = DirectoryReader.open(dir)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                List<String> ids = new ArrayList<>();
                for (ScoreDoc hit : searcher.search(query, 10).scoreDocs) {
                    ids.add(searcher.storedFields().document(hit.doc).get("id"));
                }
                return ids;
            }
        }
    }

    @Test
    void withoutWeightTheBodyHeavyDocumentWins() throws Exception {
        fields(null);

        assertEquals(List.of("in-body", "in-title"), search("java"));
    }

    @Test
    void titleWeightRanksTheTitleMatchFirst() throws Exception {
        fields(5f);

        assertEquals(List.of("in-title", "in-body"), search("java"));
    }
}

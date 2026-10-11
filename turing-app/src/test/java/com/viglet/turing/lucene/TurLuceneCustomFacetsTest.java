package com.viglet.turing.lucene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;

/**
 * T1083 — a field's custom facet (salary bands over an INT field) is counted on
 * the Lucene engine, and clicking one of its items filters by that item's range,
 * as on Solr.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurLuceneCustomFacetsTest {

    private final TurSNSiteFieldExtRepository fieldRepository = mock(TurSNSiteFieldExtRepository.class);
    private final TurSNSiteCustomFacetRepository customFacetRepository = mock(TurSNSiteCustomFacetRepository.class);
    private final TurLuceneCustomFacets customFacets = new TurLuceneCustomFacets(fieldRepository,
            customFacetRepository);
    private final TurSNSite site = new TurSNSite();

    private Directory directory;
    private IndexWriter writer;
    private DirectoryReader reader;
    private IndexSearcher searcher;

    @BeforeEach
    void setUp() throws Exception {
        TurSNSiteFieldExt salary = new TurSNSiteFieldExt();
        salary.setId("f-salary");
        salary.setName("salary");
        salary.setType(TurSEFieldType.INT);
        when(fieldRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(salary));

        TurSNSiteCustomFacet bands = new TurSNSiteCustomFacet();
        bands.setId("cf-1");
        bands.setName("salaryBand");
        bands.setTurSNSiteFieldExt(salary);
        bands.getItems().add(item("up to 3000", 1, TurSNSiteCustomFacetOperatorEnum.LESS_THAN_OR_EQUAL,
                null, 3000, Map.of("pt_BR", "até 3000")));
        bands.getItems().add(item("over 3000", 2, TurSNSiteCustomFacetOperatorEnum.GREATER_THAN,
                3000, null, Map.of()));
        when(customFacetRepository.findByFieldExtsWithDetails(anyList())).thenReturn(List.of(bands));

        directory = new ByteBuffersDirectory();
        writer = new IndexWriter(directory, new IndexWriterConfig(new TurLucenePerFieldAnalyzer()));
        add("a", 2500);
        add("b", 3000);
        add("c", 4200);
        writer.commit();
        reader = DirectoryReader.open(writer);
        searcher = new IndexSearcher(reader);
    }

    @AfterEach
    void tearDown() throws Exception {
        reader.close();
        writer.close();
        directory.close();
    }

    private static TurSNSiteCustomFacetItem item(String label, int position,
            TurSNSiteCustomFacetOperatorEnum operator, Integer start, Integer end, Map<String, String> labels) {
        TurSNSiteCustomFacetItem item = new TurSNSiteCustomFacetItem();
        item.setLabel(label);
        item.setPosition(position);
        item.setOperator(operator);
        item.setRangeStart(start == null ? null : BigDecimal.valueOf(start));
        item.setRangeEnd(end == null ? null : BigDecimal.valueOf(end));
        item.setLabels(new LinkedHashMap<>(labels));
        return item;
    }

    private void add(String id, int salary) throws Exception {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new SortedSetDocValuesField("id", new BytesRef(id)));
        doc.add(new IntPoint("salary", salary));
        writer.addDocument(doc);
    }

    @Test
    void countsEachItemOverTheMatchingDocuments() {
        List<TurSEFacetResult> results = customFacets.count(searcher, MatchAllDocsQuery.INSTANCE, site,
                Locale.of("pt", "BR"), null);

        assertEquals(1, results.size());
        TurSEFacetResult bands = results.getFirst();
        assertEquals("salaryBand", bands.getFacet());
        Map<String, TurSEFacetResultAttr> items = bands.getTurSEFacetResultAttr();
        assertEquals(List.of("up to 3000", "over 3000"), List.copyOf(items.keySet()));
        assertEquals(2, items.get("up to 3000").getCount());
        assertEquals(1, items.get("over 3000").getCount());
        assertEquals("até 3000", items.get("up to 3000").getDisplayLabel());
    }

    @Test
    void theDrillDownCountsOnlyTheNamedFacet() {
        assertTrue(customFacets.count(searcher, MatchAllDocsQuery.INSTANCE, site, null, "brand").isEmpty());
        assertEquals(1, customFacets.count(searcher, MatchAllDocsQuery.INSTANCE, site, null, "salaryBand")
                .size());
    }

    @Test
    void clickingAnItemFiltersByItsRange() throws Exception {
        TurLuceneQueryBuilder queryBuilder = new TurLuceneQueryBuilder(fieldRepository, null, null, null, null,
                customFacets);
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ("*");
        params.setRows(10);
        params.setP(1);
        params.setFq(List.of("salaryBand:over 3000"));

        Query query = queryBuilder.buildQuery(site, new TurSEParameters(params));

        Set<String> ids = new TreeSet<>();
        for (ScoreDoc hit : searcher.search(query, 10).scoreDocs) {
            ids.add(searcher.storedFields().document(hit.doc).get("id"));
        }
        assertEquals(Set.of("c"), ids);
    }
}

package com.viglet.turing.lucene;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;

/**
 * Tests for TurLuceneQueryBuilder.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurLuceneQueryBuilderTest {

    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    @Mock
    private TurLuceneRankingExpressions rankingExpressions;

    @InjectMocks
    private TurLuceneQueryBuilder queryBuilder;

    // ---- T1064: ranking expressions boost, they never filter ----

    @Test
    void buildQueryAddsRankingExpressionBoostsAsShouldClauses() {
        TurSNSite site = mock(TurSNSite.class);
        Query boost = new org.apache.lucene.search.BoostQuery(
                new org.apache.lucene.search.TermQuery(new org.apache.lucene.index.Term("category", "it")), 5f);
        when(rankingExpressions.boostQueries(site)).thenReturn(List.of(boost));

        BooleanQuery query = (BooleanQuery) queryBuilder.buildQuery(site, createParams("*"));

        assertTrue(query.clauses().stream().anyMatch(clause -> clause.query() == boost
                && clause.occur() == BooleanClause.Occur.SHOULD));
        assertEquals(BooleanClause.Occur.MUST, query.clauses().getFirst().occur());
    }

    // ---- T707: reserved id/url fields are never highlightable ----

    @Test
    void getHLFieldsExcludesReservedIdAndUrlFields() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt idField = mock(TurSNSiteFieldExt.class);
        when(idField.getName()).thenReturn("id");
        TurSNSiteFieldExt urlField = mock(TurSNSiteFieldExt.class);
        when(urlField.getName()).thenReturn("url");
        TurSNSiteFieldExt titleField = mock(TurSNSiteFieldExt.class);
        when(titleField.getName()).thenReturn("title");
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndHlAndEnabled(site, 1, 1))
                .thenReturn(List.of(idField, urlField, titleField));

        List<TurSNSiteFieldExt> hlFields = queryBuilder.getHLFields(site);

        // Only the genuine text field survives — a <mark> in `id` would corrupt the
        // value the SDK feeds back to /search/similar and silently break "Related".
        assertEquals(1, hlFields.size());
        assertEquals("title", hlFields.getFirst().getName());
    }

    // ---- buildQuery: wildcard / match-all inputs ----

    @ParameterizedTest
    @ValueSource(strings = {"*", "*:*"})
    void buildQueryShouldReturnMatchAllDocsForWildcardInputs(String queryStr) {
        TurSNSite site = mock(TurSNSite.class);
        TurSEParameters params = createParams(queryStr);
        Query query = queryBuilder.buildQuery(site, params);
        assertNotNull(query);
        assertInstanceOf(FieldExistsQuery.class, extractMainClause(query));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void buildQueryShouldReturnMatchAllDocsForBlankOrNullQuery(String queryStr) {
        TurSNSite site = mock(TurSNSite.class);
        TurSEParameters params = createParams(queryStr == null ? "" : queryStr);
        Query query = queryBuilder.buildQuery(site, params);
        assertNotNull(query);
    }

    // ---- buildQuery: text search with fields ----

    @Test
    void buildQueryWithTextFieldsShouldReturnNonNull() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt textField = mock(TurSNSiteFieldExt.class);
        when(textField.getType()).thenReturn(TurSEFieldType.TEXT);
        when(textField.getName()).thenReturn("title");
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(textField));

        TurSEParameters params = createParams("hello world");
        Query query = queryBuilder.buildQuery(site, params);
        assertNotNull(query);
    }

    @Test
    void buildQueryWithStringFieldsShouldReturnNonNull() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt stringField = mock(TurSNSiteFieldExt.class);
        when(stringField.getType()).thenReturn(TurSEFieldType.STRING);
        when(stringField.getName()).thenReturn("category");
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(stringField));

        TurSEParameters params = createParams("books");
        Query query = queryBuilder.buildQuery(site, params);
        assertNotNull(query);
    }

    @Test
    void buildQueryWithArrayFieldsShouldReturnNonNull() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt arrayField = mock(TurSNSiteFieldExt.class);
        when(arrayField.getType()).thenReturn(TurSEFieldType.ARRAY);
        when(arrayField.getName()).thenReturn("tags");
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(arrayField));

        TurSEParameters params = createParams("java");
        Query query = queryBuilder.buildQuery(site, params);
        assertNotNull(query);
    }

    @Test
    void buildQueryShouldExcludeNonTextFieldTypes() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt intField = mock(TurSNSiteFieldExt.class);
        when(intField.getType()).thenReturn(TurSEFieldType.INT);
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(intField));

        TurSEParameters params = createParams("42");
        Query query = queryBuilder.buildQuery(site, params);
        assertNotNull(query);
    }

    @Test
    void buildQueryWithNullTypeFieldShouldBeExcluded() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt nullTypeField = mock(TurSNSiteFieldExt.class);
        when(nullTypeField.getType()).thenReturn(null);
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(nullTypeField));

        TurSEParameters params = createParams("test");
        Query query = queryBuilder.buildQuery(site, params);
        assertNotNull(query);
    }

    @Test
    void buildQueryWithNoFieldsShouldFallbackToIdField() {
        TurSNSite site = mock(TurSNSite.class);
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(Collections.emptyList());

        TurSEParameters params = createParams("test");
        Query query = queryBuilder.buildQuery(site, params);
        assertNotNull(query);
    }

    // ---- buildQuery: filter queries ----

    @Test
    void buildQueryWithFilterQueriesShouldIncludeFilters() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt textField = mock(TurSNSiteFieldExt.class);
        when(textField.getType()).thenReturn(TurSEFieldType.TEXT);
        when(textField.getName()).thenReturn("title");
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(textField));

        TurSEParameters params = createParamsWithFilters("hello", List.of("category:books"));
        Query query = queryBuilder.buildQuery(site, params);
        assertNotNull(query);
        assertInstanceOf(BooleanQuery.class, query);
        BooleanQuery bq = (BooleanQuery) query;
        // MUST clause (main query) + FILTER clause
        assertTrue(bq.clauses().size() >= 2);
    }

    @Test
    void buildQueryWithEmptyFilterQueryShouldSkipIt() {
        TurSNSite site = mock(TurSNSite.class);
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(Collections.emptyList());

        TurSEParameters params = createParamsWithFilters("test", List.of("", "  "));
        Query query = queryBuilder.buildQuery(site, params);
        assertNotNull(query);
    }

    @Test
    void buildQueryWithInvalidFilterQueryShouldSkipIt() {
        TurSNSite site = mock(TurSNSite.class);
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(Collections.emptyList());

        // Malformed query that may cause ParseException
        TurSEParameters params = createParamsWithFilters("test", List.of("field:[invalid"));
        Query query = queryBuilder.buildQuery(site, params);
        assertNotNull(query);
    }

    // ---- buildQuery: multi-select (OR) facet grouping ----

    @Test
    void buildQuerySameFieldOrFacetShouldOrValues() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt category = mock(TurSNSiteFieldExt.class);
        when(category.getName()).thenReturn("category");
        when(category.getFacetItemType()).thenReturn(TurSNSiteFacetFieldEnum.OR);
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(category));

        // Two values of the same OR facet must union (OR), not intersect (AND) —
        // otherwise mutually-exclusive categories collapse to zero hits.
        TurSEParameters params = createParamsWithFilters("*",
                List.of("category:Search", "category:Integrations"));
        Query query = queryBuilder.buildQuery(site, params);

        BooleanQuery bq = (BooleanQuery) query;
        BooleanQuery filter = (BooleanQuery) filterClause(bq);
        long shoulds = filter.clauses().stream()
                .filter(c -> c.occur() == BooleanClause.Occur.SHOULD)
                .count();
        assertEquals(2, shoulds, "same-field OR facet values must be OR'd (SHOULD)");
        assertEquals(1, filter.getMinimumNumberShouldMatch());
    }

    @Test
    void buildQuerySameFieldDefaultFacetShouldAndValues() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt tags = mock(TurSNSiteFieldExt.class);
        when(tags.getName()).thenReturn("tags");
        when(tags.getFacetItemType()).thenReturn(TurSNSiteFacetFieldEnum.DEFAULT);
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(tags));

        // Default (non-OR) facet keeps the historic AND (intersect) semantics.
        TurSEParameters params = createParamsWithFilters("*",
                List.of("tags:java", "tags:spring"));
        Query query = queryBuilder.buildQuery(site, params);

        BooleanQuery bq = (BooleanQuery) query;
        BooleanQuery filter = (BooleanQuery) filterClause(bq);
        long musts = filter.clauses().stream()
                .filter(c -> c.occur() == BooleanClause.Occur.MUST)
                .count();
        assertEquals(2, musts, "default facet values must AND (MUST)");
    }

    @Test
    void buildQueryDateRangeFilterShouldBeAPointRange() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt publishedAt = mock(TurSNSiteFieldExt.class);
        when(publishedAt.getName()).thenReturn("publishedAt");
        when(publishedAt.getType()).thenReturn(com.viglet.turing.commons.se.field.TurSEFieldType.DATE);
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(publishedAt));

        // T1075 — a DATE field is indexed as a LongPoint, so a range must be a
        // point range query, not a TermQuery on the literal "[NOW-7DAYS TO *]".
        TurSEParameters params = createParamsWithFilters("*",
                List.of("publishedAt:[NOW-7DAYS TO *]"));
        Query filter = filterClause((BooleanQuery) queryBuilder.buildQuery(site, params));

        assertInstanceOf(org.apache.lucene.search.PointRangeQuery.class, filter);
    }

    private static Query filterClause(BooleanQuery bq) {
        return bq.clauses().stream()
                .filter(c -> c.occur() == BooleanClause.Occur.FILTER)
                .map(BooleanClause::query)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no FILTER clause present"));
    }

    // ---- getFacetFields ----

    @Test
    void getFacetFieldsShouldReturnOnlyFacetEnabledFields() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt faceted = mock(TurSNSiteFieldExt.class);
        when(faceted.getFacet()).thenReturn(1);
        when(faceted.getName()).thenReturn("category");

        TurSNSiteFieldExt nonFaceted = mock(TurSNSiteFieldExt.class);
        when(nonFaceted.getFacet()).thenReturn(0);

        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(faceted, nonFaceted));

        List<TurSNSiteFieldExt> result = queryBuilder.getFacetFields(site);
        assertEquals(1, result.size());
        assertEquals("category", result.get(0).getName());
    }

    @Test
    void getFacetFieldsShouldReturnEmptyListWhenNoFacets() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt nonFaceted = mock(TurSNSiteFieldExt.class);
        when(nonFaceted.getFacet()).thenReturn(0);
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(nonFaceted));

        List<TurSNSiteFieldExt> result = queryBuilder.getFacetFields(site);
        assertTrue(result.isEmpty());
    }

    @Test
    void getFacetFieldsShouldSortByFacetPosition() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt f1 = mock(TurSNSiteFieldExt.class);
        when(f1.getFacet()).thenReturn(1);
        when(f1.getFacetPosition()).thenReturn(2);
        when(f1.getName()).thenReturn("second");

        TurSNSiteFieldExt f2 = mock(TurSNSiteFieldExt.class);
        when(f2.getFacet()).thenReturn(1);
        when(f2.getFacetPosition()).thenReturn(1);
        when(f2.getName()).thenReturn("first");

        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(f1, f2));

        List<TurSNSiteFieldExt> result = queryBuilder.getFacetFields(site);
        assertEquals(2, result.size());
        assertEquals("first", result.get(0).getName());
        assertEquals("second", result.get(1).getName());
    }

    @Test
    void getFacetFieldsShouldHandleNullFacetPosition() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt f1 = mock(TurSNSiteFieldExt.class);
        when(f1.getFacet()).thenReturn(1);
        when(f1.getFacetPosition()).thenReturn(null);
        when(f1.getName()).thenReturn("noposition");

        TurSNSiteFieldExt f2 = mock(TurSNSiteFieldExt.class);
        when(f2.getFacet()).thenReturn(1);
        when(f2.getFacetPosition()).thenReturn(1);
        when(f2.getName()).thenReturn("positioned");

        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(f1, f2));

        List<TurSNSiteFieldExt> result = queryBuilder.getFacetFields(site);
        assertEquals(2, result.size());
        assertEquals("positioned", result.get(0).getName());
        assertEquals("noposition", result.get(1).getName());
    }

    // ---- getFacetFields(site, facetName) ----

    @Test
    void getFacetFieldsByNameShouldFilterByName() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt f1 = mock(TurSNSiteFieldExt.class);
        when(f1.getFacet()).thenReturn(1);
        when(f1.getFacetPosition()).thenReturn(1);
        when(f1.getName()).thenReturn("category");
        TurSNSiteFieldExt f2 = mock(TurSNSiteFieldExt.class);
        when(f2.getFacet()).thenReturn(1);
        when(f2.getFacetPosition()).thenReturn(2);
        when(f2.getName()).thenReturn("type");

        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(f1, f2));

        List<TurSNSiteFieldExt> result = queryBuilder.getFacetFields(site, "category");
        assertEquals(1, result.size());
        assertEquals("category", result.get(0).getName());
    }

    @Test
    void getFacetFieldsByNameShouldReturnEmptyForNonExistentName() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt f1 = mock(TurSNSiteFieldExt.class);
        when(f1.getFacet()).thenReturn(1);
        when(f1.getName()).thenReturn("category");

        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(f1));

        List<TurSNSiteFieldExt> result = queryBuilder.getFacetFields(site, "nonexistent");
        assertTrue(result.isEmpty());
    }

    // ---- buildWildcardQuery ----

    @Test
    void buildWildcardQueryShouldAppendWildcard() {
        TurSNSite site = mock(TurSNSite.class);
        TurSEParameters params = createParams("test");
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(Collections.emptyList());
        queryBuilder.buildWildcardQuery(site, params);
        assertTrue(params.getQuery().endsWith("*"));
    }

    @Test
    void buildWildcardQueryShouldTrimBeforeAppending() {
        TurSNSite site = mock(TurSNSite.class);
        TurSEParameters params = createParams("  test  ");
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(Collections.emptyList());
        queryBuilder.buildWildcardQuery(site, params);
        assertEquals("test*", params.getQuery());
    }

    @Test
    void buildWildcardQueryShouldReturnNonNull() {
        TurSNSite site = mock(TurSNSite.class);
        TurSEParameters params = createParams("search");
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(Collections.emptyList());
        Query result = queryBuilder.buildWildcardQuery(site, params);
        assertNotNull(result);
    }

    // ---- buildQuery: duplicate field names ----

    @Test
    void buildQueryShouldDeduplicateFieldNames() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt f1 = mock(TurSNSiteFieldExt.class);
        when(f1.getType()).thenReturn(TurSEFieldType.TEXT);
        when(f1.getName()).thenReturn("title");
        TurSNSiteFieldExt f2 = mock(TurSNSiteFieldExt.class);
        when(f2.getType()).thenReturn(TurSEFieldType.TEXT);
        when(f2.getName()).thenReturn("title");
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(f1, f2));

        TurSEParameters params = createParams("test");
        Query query = queryBuilder.buildQuery(site, params);
        assertNotNull(query);
    }

    // ---- Helpers ----

    private Query extractMainClause(Query query) {
        if (query instanceof BooleanQuery bq && !bq.clauses().isEmpty()) {
            return bq.clauses().get(0).query();
        }
        return query;
    }

    private TurSEParameters createParams(String query) {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ(query);
        searchParams.setRows(10);
        searchParams.setP(1);
        return new TurSEParameters(searchParams);
    }

    // ---- buildQuery: request-level fqOr / fqAnd / operators (T1077) ----

    private java.util.Set<String> matchingIds(TurSNSite site, TurSNSearchParams searchParams)
            throws Exception {
        org.apache.lucene.store.Directory directory = new org.apache.lucene.store.ByteBuffersDirectory();
        try (org.apache.lucene.index.IndexWriter writer = new org.apache.lucene.index.IndexWriter(directory,
                new org.apache.lucene.index.IndexWriterConfig(
                        new org.apache.lucene.analysis.standard.StandardAnalyzer()))) {
            for (String[] row : new String[][] { { "a", "x" }, { "b", "y" }, { "c", "y" } }) {
                org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
                doc.add(new org.apache.lucene.document.StringField("id", row[0],
                        org.apache.lucene.document.Field.Store.YES));
                // as TurLuceneDocumentHandler indexes it: the "*" query is FieldExistsQuery(id)
                doc.add(new org.apache.lucene.document.SortedSetDocValuesField("id",
                        new org.apache.lucene.util.BytesRef(row[0])));
                doc.add(new org.apache.lucene.document.StringField("category", row[1],
                        org.apache.lucene.document.Field.Store.YES));
                writer.addDocument(doc);
            }
            writer.commit();
            try (org.apache.lucene.index.DirectoryReader reader =
                    org.apache.lucene.index.DirectoryReader.open(writer)) {
                org.apache.lucene.search.IndexSearcher searcher = new org.apache.lucene.search.IndexSearcher(reader);
                Query query = queryBuilder.buildQuery(site, new TurSEParameters(searchParams));
                java.util.Set<String> ids = new java.util.TreeSet<>();
                for (org.apache.lucene.search.ScoreDoc hit : searcher.search(query, 10).scoreDocs) {
                    ids.add(searcher.storedFields().document(hit.doc).get("id"));
                }
                return ids;
            }
        }
    }

    private TurSNSite siteWithIdAndCategory() {
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteFieldExt id = mock(TurSNSiteFieldExt.class);
        when(id.getName()).thenReturn("id");
        TurSNSiteFieldExt category = mock(TurSNSiteFieldExt.class);
        when(category.getName()).thenReturn("category");
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(id, category));
        return site;
    }

    private static TurSNSearchParams searchAll() {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("*");
        searchParams.setRows(10);
        searchParams.setP(1);
        return searchParams;
    }

    @Test
    void fqItemOperatorOrShouldUnionTheValuesOfOneField() throws Exception {
        TurSNSearchParams searchParams = searchAll();
        searchParams.setFq(List.of("id:a", "id:b"));
        searchParams.setFqiOp(com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator.OR);

        assertEquals(java.util.Set.of("a", "b"), matchingIds(siteWithIdAndCategory(), searchParams));
    }

    @Test
    void withoutOperatorsTheValuesOfOneFieldStillIntersect() throws Exception {
        TurSNSearchParams searchParams = searchAll();
        searchParams.setFq(List.of("id:a", "id:b"));

        assertEquals(java.util.Set.of(), matchingIds(siteWithIdAndCategory(), searchParams));
    }

    @Test
    void fqOrShouldBeOrdWithTheIntersectedFilters() throws Exception {
        TurSNSearchParams searchParams = searchAll();
        searchParams.setFq(List.of("category:x"));
        searchParams.setFqOr(List.of("id:c"));

        // Solr's shape: (category:x) OR (id:c)
        assertEquals(java.util.Set.of("a", "c"), matchingIds(siteWithIdAndCategory(), searchParams));
    }

    @Test
    void fqOperatorOrShouldUnionTheFqFields() throws Exception {
        TurSNSearchParams searchParams = searchAll();
        searchParams.setFq(List.of("category:x", "id:b"));
        searchParams.setFqOp(com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator.OR);

        assertEquals(java.util.Set.of("a", "b"), matchingIds(siteWithIdAndCategory(), searchParams));
    }

    @Test
    void fqAndShouldIntersectEvenUnderAnOrOperator() throws Exception {
        TurSNSearchParams searchParams = searchAll();
        searchParams.setFqAnd(List.of("category:y", "id:c"));
        searchParams.setFqOp(com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator.OR);

        assertEquals(java.util.Set.of("c"), matchingIds(siteWithIdAndCategory(), searchParams));
    }

    private TurSEParameters createParamsWithFilters(String query, List<String> filters) {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ(query);
        searchParams.setRows(10);
        searchParams.setP(1);
        searchParams.setFq(filters);
        return new TurSEParameters(searchParams);
    }
}

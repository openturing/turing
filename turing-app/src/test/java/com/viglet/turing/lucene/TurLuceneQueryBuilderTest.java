package com.viglet.turing.lucene;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

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

    @InjectMocks
    private TurLuceneQueryBuilder queryBuilder;

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

    private TurSEParameters createParamsWithFilters(String query, List<String> filters) {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ(query);
        searchParams.setRows(10);
        searchParams.setP(1);
        searchParams.setFq(filters);
        return new TurSEParameters(searchParams);
    }
}

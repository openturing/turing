package com.viglet.turing.lucene;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.apache.lucene.search.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;

@ExtendWith(MockitoExtension.class)
class TurLuceneQueryBuilderTest {

    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    @InjectMocks
    private TurLuceneQueryBuilder queryBuilder;

    @Test
    void buildQueryShouldReturnNonNullForWildcard() {
        TurSNSite site = mock(TurSNSite.class);
        TurSEParameters params = createParams("*");
        Query query = queryBuilder.buildQuery(site, params);
        assertNotNull(query);
    }

    @Test
    void buildQueryShouldReturnNonNullForStarColonStar() {
        TurSNSite site = mock(TurSNSite.class);
        TurSEParameters params = createParams("*:*");
        Query query = queryBuilder.buildQuery(site, params);
        assertNotNull(query);
    }

    @Test
    void buildQueryShouldReturnNonNullForEmptyQuery() {
        TurSNSite site = mock(TurSNSite.class);
        TurSEParameters params = createParams("");
        Query query = queryBuilder.buildQuery(site, params);
        assertNotNull(query);
    }

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
    void buildWildcardQueryShouldAppendWildcard() {
        TurSNSite site = mock(TurSNSite.class);
        TurSEParameters params = createParams("test");
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(Collections.emptyList());
        queryBuilder.buildWildcardQuery(site, params);
        assertTrue(params.getQuery().endsWith("*"));
    }

    private TurSEParameters createParams(String query) {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ(query);
        searchParams.setRows(10);
        searchParams.setP(1);
        return new TurSEParameters(searchParams);
    }
}

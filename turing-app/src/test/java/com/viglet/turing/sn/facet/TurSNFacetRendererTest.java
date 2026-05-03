/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.sn.facet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.bean.TurSNFilterParams;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetBean;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.solr.TurSolrQueryBuilder;

/**
 * Unit tests for {@link TurSNFacetRenderer}, focused on the visibility rules
 * (main vs. secondary), early bail-outs (facet disabled / empty results) and
 * the de-duplicated implementation behind both public entry points.
 */
@ExtendWith(MockitoExtension.class)
class TurSNFacetRendererTest {

    @Mock
    private TurSearchEnginePluginFactory searchEnginePluginFactory;

    @Mock
    private TurSolrQueryBuilder turSolrQueryBuilder;

    @InjectMocks
    private TurSNFacetRenderer renderer;

    private TurSNSite site;

    @BeforeEach
    void setUp() {
        site = new TurSNSite();
        site.setName("site");
        site.setFacet(1);
    }

    @Test
    void responseFacet_returnsEmptyWhenSiteFacetDisabled() {
        site.setFacet(0);

        List<TurSNSiteSearchFacetBean> result = renderer.responseFacet(
                context(), site, List.of(), Map.of(), resultsWithFacet("category", "books", 5));

        assertThat(result).isEmpty();
    }

    @Test
    void responseFacet_returnsEmptyWhenNoFacetResults() {
        TurSEResults empty = TurSEResults.builder()
                .facetResults(Collections.emptyList())
                .build();

        List<TurSNSiteSearchFacetBean> result = renderer.responseFacet(
                context(), site, List.of(), Map.of(), empty);

        assertThat(result).isEmpty();
    }

    @Test
    void responseFacet_includesMainFacetsAndExcludesSecondary() {
        TurSEResults results = resultsWithFacet("category", "books", 5);
        Map<String, TurSNSiteFieldExtDto> facetMap = singleFacetMap("category", false);
        lenient().when(turSolrQueryBuilder.getFqFields(any(TurSNFilterParams.class)))
                .thenReturn(Collections.emptyList());

        List<TurSNSiteSearchFacetBean> mainResult = renderer.responseFacet(
                context(), site, List.of(), facetMap, results);
        List<TurSNSiteSearchFacetBean> secondaryResult = renderer.responseSecondaryFacet(
                context(), site, List.of(), facetMap, results);

        assertThat(mainResult).hasSize(1);
        assertThat(mainResult.getFirst().getName()).isEqualTo("category");
        assertThat(secondaryResult).isEmpty();
    }

    @Test
    void responseSecondaryFacet_includesSecondaryAndExcludesMain() {
        TurSEResults results = resultsWithFacet("category", "books", 5);
        Map<String, TurSNSiteFieldExtDto> facetMap = singleFacetMap("category", true);
        lenient().when(turSolrQueryBuilder.getFqFields(any(TurSNFilterParams.class)))
                .thenReturn(Collections.emptyList());

        List<TurSNSiteSearchFacetBean> mainResult = renderer.responseFacet(
                context(), site, List.of(), facetMap, results);
        List<TurSNSiteSearchFacetBean> secondaryResult = renderer.responseSecondaryFacet(
                context(), site, List.of(), facetMap, results);

        assertThat(mainResult).isEmpty();
        assertThat(secondaryResult).hasSize(1);
        assertThat(secondaryResult.getFirst().getName()).isEqualTo("category");
    }

    @Test
    void responseFacetToRemove_returnsEmptyBeanWhenNoActiveFilters() {
        TurSNSiteSearchFacetBean bean = renderer.responseFacetToRemove(context(), site);

        assertThat(bean.getFacets()).isNullOrEmpty();
        verify(turSolrQueryBuilder, never()).getFacetFieldsInFilterQuery(any());
    }

    @Test
    void responseFacetToRemove_emitsBeanWhenFiltersMatchKnownFacets() {
        TurSNSiteSearchContext ctx = contextWithFilters(List.of("category:books"));
        when(turSolrQueryBuilder.getFacetFieldsInFilterQuery(any()))
                .thenReturn(List.of("category"));

        TurSNSiteSearchFacetBean bean = renderer.responseFacetToRemove(ctx, site);

        assertThat(bean.getFacets()).hasSize(1);
        assertThat(bean.getFacets().getFirst().isSelected()).isTrue();
        assertThat(bean.getFacets().getFirst().getFilterQuery()).isEqualTo("category:books");
    }

    @Test
    void responseFacetToRemove_skipsFiltersOnUnknownFacets() {
        TurSNSiteSearchContext ctx = contextWithFilters(List.of("category:books"));
        when(turSolrQueryBuilder.getFacetFieldsInFilterQuery(any()))
                .thenReturn(List.of("color"));

        TurSNSiteSearchFacetBean bean = renderer.responseFacetToRemove(ctx, site);

        assertThat(bean.getFacets()).isNullOrEmpty();
    }

    @Test
    void responseFacet_skipsItemsWithZeroCountByDefault() {
        TurSEResults results = resultsWithFacetMulti("category",
                Map.of("books", 5, "movies", 0));
        Map<String, TurSNSiteFieldExtDto> facetMap = singleFacetMap("category", false);
        // showAllFacetItems defaults to null/false; zero-count items must be filtered out.
        lenient().when(turSolrQueryBuilder.getFqFields(any(TurSNFilterParams.class)))
                .thenReturn(Collections.emptyList());

        List<TurSNSiteSearchFacetBean> result = renderer.responseFacet(
                context(), site, List.of(), facetMap, results);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getFacets()).hasSize(1);
        assertThat(result.getFirst().getFacets().getFirst().getLabel()).isEqualTo("books");
    }

    // ---- helpers ---------------------------------------------------------

    private TurSNSiteSearchContext context() {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("*");
        searchParams.setLocale(Locale.US);
        TurSNSitePostParamsBean post = new TurSNSitePostParamsBean();
        return new TurSNSiteSearchContext("site", new TurSNConfig(),
                new TurSEParameters(searchParams, post), Locale.US,
                URI.create("http://localhost/search"), post);
    }

    private TurSNSiteSearchContext contextWithFilters(List<String> filters) {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("*");
        searchParams.setLocale(Locale.US);
        TurSNSitePostParamsBean post = new TurSNSitePostParamsBean();
        TurSEParameters params = new TurSEParameters(searchParams, post);
        params.getTurSNFilterParams().setDefaultValues(filters);
        return new TurSNSiteSearchContext("site", new TurSNConfig(), params, Locale.US,
                URI.create("http://localhost/search"), post);
    }

    private Map<String, TurSNSiteFieldExtDto> singleFacetMap(String name, boolean secondary) {
        TurSNSiteFieldExtDto dto = new TurSNSiteFieldExtDto();
        dto.setName(name);
        dto.setFacetName(name);
        dto.setSecondaryFacet(secondary);
        dto.setFacetLocales(Collections.emptySet());
        Map<String, TurSNSiteFieldExtDto> map = new HashMap<>();
        map.put(name, dto);
        return map;
    }

    private TurSEResults resultsWithFacet(String facetName, String value, int count) {
        return resultsWithFacetMulti(facetName, Map.of(value, count));
    }

    private TurSEResults resultsWithFacetMulti(String facetName, Map<String, Integer> items) {
        TurSEFacetResult facet = new TurSEFacetResult();
        facet.setFacet(facetName);
        items.forEach((value, count) -> facet.add(value, new TurSEFacetResultAttr(value, count)));
        List<TurSEFacetResult> list = new ArrayList<>();
        list.add(facet);
        return TurSEResults.builder().facetResults(list).build();
    }
}

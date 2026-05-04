/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.sn.querycontext;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchQueryContextBean;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.se.result.TurSEResults;

/**
 * Unit tests for {@link TurSNQueryContextBuilder}, covering page bound
 * computation, default-field projection, and facet-type fallback to
 * {@link TurSNSiteFacetFieldEnum#AND} when the site value is null.
 */
class TurSNQueryContextBuilderTest {

    private TurSNQueryContextBuilder builder;
    private TurSNSite site;

    @BeforeEach
    void setUp() {
        builder = new TurSNQueryContextBuilder();
        site = new TurSNSite();
        site.setName("site");
    }

    private TurSEResults results(long numFound, long start, int limit, int currentPage, int pageCount) {
        return TurSEResults.builder()
                .numFound(numFound)
                .start(start)
                .limit(limit)
                .currentPage(currentPage)
                .pageCount(pageCount)
                .queryString("java")
                .elapsedTime(15L)
                .build();
    }

    @Test
    void buildEchoesQueryAndPaginationFields() {
        TurSNSiteSearchQueryContextBean ctx = builder.build(site, results(40, 10, 10, 2, 4),
                Locale.US);

        assertThat(ctx.getCount()).isEqualTo(40);
        assertThat(ctx.getPage()).isEqualTo(2);
        assertThat(ctx.getPageCount()).isEqualTo(4);
        assertThat(ctx.getLimit()).isEqualTo(10);
        assertThat(ctx.getOffset()).isZero();
        assertThat(ctx.getResponseTime()).isEqualTo(15L);
        assertThat(ctx.getIndex()).isEqualTo("site");
        assertThat(ctx.getQuery().getQueryString()).isEqualTo("java");
        assertThat(ctx.getQuery().getLocale()).isEqualTo(Locale.US);
    }

    @Test
    void buildPageBoundsForFullPageInTheMiddle() {
        // start 10, limit 10, count 40 -> pageStart 11, pageEnd 20
        TurSNSiteSearchQueryContextBean ctx = builder.build(site, results(40, 10, 10, 2, 4),
                Locale.US);

        assertThat(ctx.getPageStart()).isEqualTo(11);
        assertThat(ctx.getPageEnd()).isEqualTo(20);
    }

    @Test
    void buildPageBoundsClampToCountForPartialLastPage() {
        // start 30, limit 10, count 35 -> pageEnd capped at 35, pageStart 31
        TurSNSiteSearchQueryContextBean ctx = builder.build(site, results(35, 30, 10, 4, 4),
                Locale.US);

        assertThat(ctx.getPageStart()).isEqualTo(31);
        assertThat(ctx.getPageEnd()).isEqualTo(35);
    }

    @Test
    void buildPageBoundsForEmptyResultsCollapsePageStartToZero() {
        // count 0 -> pageEnd Math.min(start+limit, 0) = 0 -> pageStart Math.min(start+1, 0) = 0
        TurSNSiteSearchQueryContextBean ctx = builder.build(site, results(0, 0, 10, 1, 0),
                Locale.US);

        assertThat(ctx.getCount()).isZero();
        assertThat(ctx.getPageEnd()).isZero();
        assertThat(ctx.getPageStart()).isZero();
    }

    @Test
    void buildEmitsConfiguredFacetAndFacetItemTypes() {
        site.setFacetType(TurSNSiteFacetFieldEnum.OR);
        site.setFacetItemType(TurSNSiteFacetFieldEnum.AND);

        TurSNSiteSearchQueryContextBean ctx = builder.build(site, results(0, 0, 10, 1, 0),
                Locale.US);

        assertThat(ctx.getFacetType()).isEqualTo("OR");
        assertThat(ctx.getFacetItemType()).isEqualTo("AND");
    }

    @Test
    void buildFallsBackToAndWhenSiteFacetTypesAreNull() {
        site.setFacetType(null);
        site.setFacetItemType(null);

        TurSNSiteSearchQueryContextBean ctx = builder.build(site, results(0, 0, 10, 1, 0),
                Locale.US);

        assertThat(ctx.getFacetType()).isEqualTo("AND");
        assertThat(ctx.getFacetItemType()).isEqualTo("AND");
    }

    @Test
    void buildProjectsDefaultFieldNamesFromSite() {
        site.setDefaultDateField("date");
        site.setDefaultDescriptionField("desc");
        site.setDefaultImageField("image");
        site.setDefaultTextField("text");
        site.setDefaultTitleField("title");
        site.setDefaultURLField("url");

        TurSNSiteSearchQueryContextBean ctx = builder.build(site, results(0, 0, 10, 1, 0),
                Locale.US);

        assertThat(ctx.getDefaultFields().getDate()).isEqualTo("date");
        assertThat(ctx.getDefaultFields().getDescription()).isEqualTo("desc");
        assertThat(ctx.getDefaultFields().getImage()).isEqualTo("image");
        assertThat(ctx.getDefaultFields().getText()).isEqualTo("text");
        assertThat(ctx.getDefaultFields().getTitle()).isEqualTo("title");
        assertThat(ctx.getDefaultFields().getUrl()).isEqualTo("url");
    }
}

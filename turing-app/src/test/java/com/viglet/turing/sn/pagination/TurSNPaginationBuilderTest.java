/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.sn.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchPaginationBean;
import com.viglet.turing.commons.sn.pagination.TurSNPaginationType;
import com.viglet.turing.se.result.TurSEGenericResults;

/**
 * Unit tests for {@link TurSNPaginationBuilder}, covering first/previous/next/
 * last visibility, the page window centred on the current page, and the
 * current-page marker.
 */
class TurSNPaginationBuilderTest {

    private TurSNPaginationBuilder builder;
    private URI uri;

    @BeforeEach
    void setUp() {
        builder = new TurSNPaginationBuilder();
        uri = URI.create("http://localhost/search?q=java");
    }

    private TurSEGenericResults results(int currentPage, int pageCount) {
        return new TurSEGenericResults(0L, 0L, 10, pageCount, currentPage, Collections.emptyList());
    }

    @Test
    void buildOnSinglePageReturnsOnlyCurrent() {
        List<TurSNSiteSearchPaginationBean> pagination = builder.build(uri, results(1, 1));

        assertThat(pagination).hasSize(1);
        assertThat(pagination.getFirst().getType()).isEqualTo(TurSNPaginationType.CURRENT);
        assertThat(pagination.getFirst().getPage()).isEqualTo(1);
    }

    @Test
    void buildOnFirstPageWithMultiplePagesIncludesNextAndLastButNotPreviousOrFirst() {
        List<TurSNSiteSearchPaginationBean> pagination = builder.build(uri, results(1, 5));

        List<TurSNPaginationType> types = pagination.stream()
                .map(TurSNSiteSearchPaginationBean::getType).toList();
        assertThat(types).doesNotContain(TurSNPaginationType.FIRST, TurSNPaginationType.PREVIOUS);
        assertThat(types).contains(TurSNPaginationType.NEXT, TurSNPaginationType.LAST);
        assertThat(pagination).filteredOn(p -> p.getType() == TurSNPaginationType.CURRENT)
                .singleElement().extracting(TurSNSiteSearchPaginationBean::getPage).isEqualTo(1);
    }

    @Test
    void buildOnLastPageIncludesFirstAndPreviousButNotNextOrLast() {
        List<TurSNSiteSearchPaginationBean> pagination = builder.build(uri, results(5, 5));

        List<TurSNPaginationType> types = pagination.stream()
                .map(TurSNSiteSearchPaginationBean::getType).toList();
        assertThat(types).contains(TurSNPaginationType.FIRST, TurSNPaginationType.PREVIOUS);
        assertThat(types).doesNotContain(TurSNPaginationType.NEXT, TurSNPaginationType.LAST);
        assertThat(pagination).filteredOn(p -> p.getType() == TurSNPaginationType.CURRENT)
                .singleElement().extracting(TurSNSiteSearchPaginationBean::getPage).isEqualTo(5);
    }

    @Test
    void buildOnMiddlePageHasFirstPreviousNextLastAndCurrentMarker() {
        List<TurSNSiteSearchPaginationBean> pagination = builder.build(uri, results(5, 10));

        List<TurSNPaginationType> types = pagination.stream()
                .map(TurSNSiteSearchPaginationBean::getType).toList();
        assertThat(types).contains(TurSNPaginationType.FIRST, TurSNPaginationType.PREVIOUS,
                TurSNPaginationType.NEXT, TurSNPaginationType.LAST);
        assertThat(pagination).filteredOn(p -> p.getType() == TurSNPaginationType.CURRENT)
                .singleElement().extracting(TurSNSiteSearchPaginationBean::getPage).isEqualTo(5);
    }

    @Test
    void buildPageWindowSpansThreeBeforeAndThreeAfterCurrent() {
        // currentPage 5, pageCount 10 -> window is [2..8]
        List<Integer> pageNumbers = builder.build(uri, results(5, 10)).stream()
                .filter(p -> p.getType() == TurSNPaginationType.PAGE
                        || p.getType() == TurSNPaginationType.CURRENT)
                .map(TurSNSiteSearchPaginationBean::getPage)
                .toList();

        assertThat(pageNumbers).containsExactly(2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void buildPageWindowClampsToFirstPageWhenNearStart() {
        // currentPage 2, pageCount 10 -> window cannot go below 1 -> [1..5]
        List<Integer> pageNumbers = builder.build(uri, results(2, 10)).stream()
                .filter(p -> p.getType() == TurSNPaginationType.PAGE
                        || p.getType() == TurSNPaginationType.CURRENT)
                .map(TurSNSiteSearchPaginationBean::getPage)
                .toList();

        assertThat(pageNumbers).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void buildPageWindowClampsToPageCountWhenNearEnd() {
        // currentPage 9, pageCount 10 -> window cannot go above 10 -> [6..10]
        List<Integer> pageNumbers = builder.build(uri, results(9, 10)).stream()
                .filter(p -> p.getType() == TurSNPaginationType.PAGE
                        || p.getType() == TurSNPaginationType.CURRENT)
                .map(TurSNSiteSearchPaginationBean::getPage)
                .toList();

        assertThat(pageNumbers).containsExactly(6, 7, 8, 9, 10);
    }

    @Test
    void buildSetsPageHrefAndTextForEachEntry() {
        List<TurSNSiteSearchPaginationBean> pagination = builder.build(uri, results(3, 5));

        for (TurSNSiteSearchPaginationBean entry : pagination) {
            assertThat(entry.getHref()).isNotBlank().contains("p=" + entry.getPage());
        }
        assertThat(pagination).filteredOn(p -> p.getType() == TurSNPaginationType.FIRST)
                .singleElement().extracting(TurSNSiteSearchPaginationBean::getText)
                .isEqualTo(TurSNPaginationBuilder.FIRST);
        assertThat(pagination).filteredOn(p -> p.getType() == TurSNPaginationType.LAST)
                .singleElement().extracting(TurSNSiteSearchPaginationBean::getText)
                .isEqualTo(TurSNPaginationBuilder.LAST);
        assertThat(pagination).filteredOn(p -> p.getType() == TurSNPaginationType.PREVIOUS)
                .singleElement().extracting(TurSNSiteSearchPaginationBean::getText)
                .isEqualTo(TurSNPaginationBuilder.PREVIOUS);
        assertThat(pagination).filteredOn(p -> p.getType() == TurSNPaginationType.NEXT)
                .singleElement().extracting(TurSNSiteSearchPaginationBean::getText)
                .isEqualTo(TurSNPaginationBuilder.NEXT);
    }

    @Test
    void buildPreviousAndNextReferenceAdjacentPages() {
        List<TurSNSiteSearchPaginationBean> pagination = builder.build(uri, results(4, 10));

        assertThat(pagination).filteredOn(p -> p.getType() == TurSNPaginationType.PREVIOUS)
                .singleElement().extracting(TurSNSiteSearchPaginationBean::getPage).isEqualTo(3);
        assertThat(pagination).filteredOn(p -> p.getType() == TurSNPaginationType.NEXT)
                .singleElement().extracting(TurSNSiteSearchPaginationBean::getPage).isEqualTo(5);
        assertThat(pagination).filteredOn(p -> p.getType() == TurSNPaginationType.LAST)
                .singleElement().extracting(TurSNSiteSearchPaginationBean::getPage).isEqualTo(10);
    }

    @Test
    void buildBeyondLastPageOmitsPreviousAndNextButStillEmitsFirstAndLast() {
        // Defensive: currentPage > pageCount (shouldn't happen in practice).
        // The previous inline implementation gated PREVIOUS/NEXT on currentPage <= pageCount,
        // but LAST was added unconditionally once isNotLastPage held; preserve that behaviour.
        List<TurSNSiteSearchPaginationBean> pagination = builder.build(uri, results(11, 10));

        List<TurSNPaginationType> types = pagination.stream()
                .map(TurSNSiteSearchPaginationBean::getType).toList();
        assertThat(types).contains(TurSNPaginationType.FIRST, TurSNPaginationType.LAST);
        assertThat(types).doesNotContain(TurSNPaginationType.PREVIOUS, TurSNPaginationType.NEXT);
    }
}

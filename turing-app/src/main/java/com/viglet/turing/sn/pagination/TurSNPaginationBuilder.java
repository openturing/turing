/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.sn.pagination;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchPaginationBean;
import com.viglet.turing.commons.sn.pagination.TurSNPaginationType;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.se.result.TurSEGenericResults;

/**
 * Builds the list of {@link TurSNSiteSearchPaginationBean} entries for a search
 * response — first/previous/page-window/current/next/last. Extracted from
 * {@code TurSNSearchProcess} to keep the orchestrator slim and let the
 * pagination logic be unit-tested in isolation.
 *
 * <p>Behaviour is intentionally identical to the previous inline implementation.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSNPaginationBuilder {

    public static final String FIRST = "First";
    public static final String PREVIOUS = "Previous";
    public static final String NEXT = "Next";
    public static final String LAST = "Last";

    private static final int PAGE_WINDOW = 3;

    /**
     * Builds the pagination list for the given URI and search results.
     *
     * @param uri          the request URI used to derive page links
     * @param turSEResults the search results carrying current page and total page count
     * @return ordered list of pagination entries (may include first/previous, the
     *         page window centred on the current page, and next/last)
     */
    public List<TurSNSiteSearchPaginationBean> build(URI uri, TurSEGenericResults turSEResults) {
        List<TurSNSiteSearchPaginationBean> pagination = new ArrayList<>();
        if (turSEResults.getCurrentPage() > 1) {
            pagination.add(firstPage(uri));
            if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
                pagination.add(previousPage(uri, turSEResults));
            }
        }
        IntStream.rangeClosed(firstPagination(turSEResults), lastPagination(turSEResults))
                .forEach(page -> pagination.add(isCurrentPage(turSEResults, page)
                        ? currentPage(uri, page)
                        : otherPages(uri, page)));
        if (isNotLastPage(turSEResults)) {
            if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
                pagination.add(nextPage(uri, turSEResults));
            }
            pagination.add(lastPage(uri, turSEResults));
        }
        return pagination;
    }

    private static boolean isCurrentPage(TurSEGenericResults turSEResults, int page) {
        return page == turSEResults.getCurrentPage();
    }

    private static boolean isNotLastPage(TurSEGenericResults turSEResults) {
        return turSEResults.getCurrentPage() != turSEResults.getPageCount()
                && turSEResults.getPageCount() > 1;
    }

    private static int firstPagination(TurSEGenericResults turSEResults) {
        return turSEResults.getCurrentPage() - PAGE_WINDOW > 0
                ? turSEResults.getCurrentPage() - PAGE_WINDOW
                : 1;
    }

    private static int lastPagination(TurSEGenericResults turSEResults) {
        return Math.min(turSEResults.getCurrentPage() + PAGE_WINDOW, turSEResults.getPageCount());
    }

    private TurSNSiteSearchPaginationBean firstPage(URI uri) {
        return new TurSNSiteSearchPaginationBean().setType(TurSNPaginationType.FIRST)
                .setHref(pageHref(uri, 1))
                .setText(FIRST).setPage(1);
    }

    private TurSNSiteSearchPaginationBean previousPage(URI uri, TurSEGenericResults turSEResults) {
        int page = turSEResults.getCurrentPage() - 1;
        return new TurSNSiteSearchPaginationBean().setType(TurSNPaginationType.PREVIOUS)
                .setHref(pageHref(uri, page))
                .setText(PREVIOUS).setPage(page);
    }

    private TurSNSiteSearchPaginationBean nextPage(URI uri, TurSEGenericResults turSEResults) {
        int page = turSEResults.getCurrentPage() + 1;
        return new TurSNSiteSearchPaginationBean().setType(TurSNPaginationType.NEXT)
                .setHref(pageHref(uri, page))
                .setText(NEXT).setPage(page);
    }

    private TurSNSiteSearchPaginationBean lastPage(URI uri, TurSEGenericResults turSEResults) {
        int page = turSEResults.getPageCount();
        return new TurSNSiteSearchPaginationBean().setType(TurSNPaginationType.LAST)
                .setHref(pageHref(uri, page))
                .setText(LAST).setPage(page);
    }

    private TurSNSiteSearchPaginationBean otherPages(URI uri, int page) {
        return genericPage(uri, page, TurSNPaginationType.PAGE);
    }

    private TurSNSiteSearchPaginationBean currentPage(URI uri, int page) {
        return genericPage(uri, page, TurSNPaginationType.CURRENT);
    }

    private TurSNSiteSearchPaginationBean genericPage(URI uri, int page, TurSNPaginationType type) {
        return new TurSNSiteSearchPaginationBean()
                .setHref(pageHref(uri, page))
                .setText(Integer.toString(page)).setType(type).setPage(page);
    }

    private String pageHref(URI uri, int page) {
        return TurCommonsUtils
                .addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(page), true)
                .toString();
    }
}

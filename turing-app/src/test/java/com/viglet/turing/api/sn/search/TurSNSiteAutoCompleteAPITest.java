/*
 * Copyright (C) 2016-2025 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.api.sn.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSearchRequest;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.sn.ac.TurSNAutoComplete;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit tests for TurSNSiteAutoCompleteAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteAutoCompleteAPITest {

    @Test
    void testAutoCompleteUsesRegularSearchWhenFiltersPresent() {
        TurSNAutoComplete turSNAutoComplete = mock(TurSNAutoComplete.class);
        TurSNSiteAutoCompleteAPI api = new TurSNSiteAutoCompleteAPI(turSNAutoComplete,
                mock(TurSNSiteSearchService.class));
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ("hello");
        params.setRows(5);
        HttpServletRequest request = mock(HttpServletRequest.class);
        List<String> expected = List.of("one", "two");
        List<String> filterQueriesDefault = List.of("type:doc");

        when(turSNAutoComplete.autoCompleteWithRegularSearch("site", params, request))
                .thenReturn(expected);

        List<String> result = api.turSNSiteAutoComplete("site", params, filterQueriesDefault,
                null, null, TurSNFilterQueryOperator.NONE, TurSNFilterQueryOperator.NONE, "en_US",
                request);

        assertThat(result).isEqualTo(expected);
        assertThat(params.getFq()).isEqualTo(filterQueriesDefault);
        verify(turSNAutoComplete).autoCompleteWithRegularSearch("site", params, request);
    }

    @Test
    void testAutoCompleteDefaultsRowsWhenNegativeAndNoFilters() {
        TurSNAutoComplete turSNAutoComplete = mock(TurSNAutoComplete.class);
        TurSNSiteAutoCompleteAPI api = new TurSNSiteAutoCompleteAPI(turSNAutoComplete,
                mock(TurSNSiteSearchService.class));
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ("hello");
        params.setRows(-1);
        HttpServletRequest request = mock(HttpServletRequest.class);
        List<String> expected = List.of("one");

        when(turSNAutoComplete.autoComplete(eq("site"), eq("hello"), any(Locale.class), eq(20L)))
                .thenReturn(expected);

        List<String> result = api.turSNSiteAutoComplete("site", params, null, null, null,
                TurSNFilterQueryOperator.NONE, TurSNFilterQueryOperator.NONE, "en_US", request);

        assertThat(result).isEqualTo(expected);
        assertThat(params.getRows()).isEqualTo(20);
        verify(turSNAutoComplete).autoComplete(eq("site"), eq("hello"), any(Locale.class), eq(20L));
    }

    @Test
    void testAutoCompleteUsesLocaleAndRowsWhenNoFilters() {
        TurSNAutoComplete turSNAutoComplete = mock(TurSNAutoComplete.class);
        TurSNSiteAutoCompleteAPI api = new TurSNSiteAutoCompleteAPI(turSNAutoComplete,
                mock(TurSNSiteSearchService.class));
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ("world");
        params.setRows(7);
        HttpServletRequest request = mock(HttpServletRequest.class);
        List<String> expected = List.of("world");

        when(turSNAutoComplete.autoComplete("site", "world", Locale.CANADA, 7L)).thenReturn(expected);

        List<String> result = api.turSNSiteAutoComplete("site", params, null, null, null,
                TurSNFilterQueryOperator.NONE, TurSNFilterQueryOperator.NONE, "en_CA", request);

        assertThat(result).isEqualTo(expected);
        assertThat(params.getLocale()).isEqualTo(Locale.CANADA);
    }

    /**
     * T712 / §XLV — the POST variant must reach the same regular-search path when
     * the body carries filter queries, and it must hand the collaborator a
     * canonical URI on the <em>GET</em> {@code /ac} path (not {@code /ac/query}),
     * plus a cache key derived from the body rather than from the — empty — POST
     * query string.
     */
    @Test
    void testAutoCompletePostUsesRegularSearchAndBodyDerivedCacheKey() {
        TurSNAutoComplete turSNAutoComplete = mock(TurSNAutoComplete.class);
        TurSNSiteSearchService searchService = mock(TurSNSiteSearchService.class);
        TurSNSiteAutoCompleteAPI api =
                new TurSNSiteAutoCompleteAPI(turSNAutoComplete, searchService);
        TurSNSearchRequest searchRequest = new TurSNSearchRequest();
        searchRequest.setQ("chat");
        searchRequest.setLocale("en_US");
        searchRequest.setFq(List.of("section:RAG & Chat"));
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/sn/site/ac/query");
        when(turSNAutoComplete.autoCompleteWithRegularSearch(eq("site"),
                any(TurSNSearchParams.class), any(URI.class), anyString()))
                        .thenReturn(List.of("chatbot"));

        List<String> result = api.turSNSiteAutoCompleteQueryPost("site", searchRequest, request);

        assertThat(result).containsExactly("chatbot");
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        ArgumentCaptor<String> cacheKey = ArgumentCaptor.forClass(String.class);
        verify(turSNAutoComplete).autoCompleteWithRegularSearch(eq("site"),
                any(TurSNSearchParams.class), uri.capture(), cacheKey.capture());
        assertThat(uri.getValue().getRawPath()).isEqualTo("/api/sn/site/ac");
        assertThat(uri.getValue().getRawQuery()).contains("fq[]=section:RAG%20%26%20Chat");
        assertThat(cacheKey.getValue()).startsWith("site_").contains("q=chat");
    }

    /**
     * T712 / §XLV — without filter queries the POST variant takes the suggester
     * path, exactly like the GET mapping, and applies the same default rows.
     */
    @Test
    void testAutoCompletePostFallsBackToSuggesterAndDefaultsRows() {
        TurSNAutoComplete turSNAutoComplete = mock(TurSNAutoComplete.class);
        TurSNSiteSearchService searchService = mock(TurSNSiteSearchService.class);
        TurSNSiteAutoCompleteAPI api =
                new TurSNSiteAutoCompleteAPI(turSNAutoComplete, searchService);
        TurSNSearchRequest searchRequest = new TurSNSearchRequest();
        searchRequest.setQ("world");
        searchRequest.setLocale("en_CA");
        when(turSNAutoComplete.autoComplete("site", "world", Locale.CANADA, 20L))
                .thenReturn(List.of("world"));

        List<String> result = api.turSNSiteAutoCompleteQueryPost("site", searchRequest,
                new MockHttpServletRequest("POST", "/api/sn/site/ac/query"));

        assertThat(result).containsExactly("world");
        verify(turSNAutoComplete).autoComplete("site", "world", Locale.CANADA, 20L);
    }

    /**
     * T712 / §XLV — a body without a locale falls back to the site's default,
     * mirroring {@code POST /search/query}.
     */
    @Test
    void testAutoCompletePostResolvesDefaultLocaleWhenBodyOmitsIt() {
        TurSNAutoComplete turSNAutoComplete = mock(TurSNAutoComplete.class);
        TurSNSiteSearchService searchService = mock(TurSNSiteSearchService.class);
        TurSNSiteAutoCompleteAPI api =
                new TurSNSiteAutoCompleteAPI(turSNAutoComplete, searchService);
        TurSNSearchRequest searchRequest = new TurSNSearchRequest();
        searchRequest.setQ("world");
        when(searchService.resolveDefaultLocale("site")).thenReturn(Locale.CANADA);
        when(turSNAutoComplete.autoComplete("site", "world", Locale.CANADA, 20L))
                .thenReturn(List.of("world"));

        List<String> result = api.turSNSiteAutoCompleteQueryPost("site", searchRequest,
                new MockHttpServletRequest("POST", "/api/sn/site/ac/query"));

        assertThat(result).containsExactly("world");
        verify(searchService).resolveDefaultLocale("site");
    }
}

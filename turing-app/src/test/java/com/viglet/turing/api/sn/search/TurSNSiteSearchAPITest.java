/*
 * Copyright (C) 2016-2026 the original author or authors.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSearchRequest;
import com.viglet.turing.commons.sn.bean.TurSNSearchResponse;
import com.viglet.turing.commons.sn.bean.TurSNSiteLocaleBean;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.bean.TurSNSortOptionBean;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredSearchBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNResponseFormat;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.sn.TurSNDuplicateClusteringService;
import com.viglet.turing.sn.TurSNSearchProcess;
import com.viglet.turing.sn.TurSNSimilarDocumentsService;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshot;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshotService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit tests for TurSNSiteSearchAPI. The controller delegates site
 * resolution to {@link TurSNSiteSearchSnapshotService}, so the tests work
 * against a fake snapshot rather than a TurSNSite domain object.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteSearchAPITest {

    private static TurSNSiteSearchSnapshot snapshot(String siteName) {
        TurSNSite site = new TurSNSite();
        site.setId("site-id");
        site.setName(siteName);
        return new TurSNSiteSearchSnapshot(site, null, "solr", false,
                List.of(), Set.of(), Map.of(), Map.of(), List.of(), List.of());
    }

    @Test
    void testSearchPostReturnsUnauthorizedWhenPrincipalMissing() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));

        ResponseEntity<TurSNSiteSearchBean> response = api.turSNSiteSearchSelectPost("site",
                new TurSNSearchParams(), TurSNFilterQueryOperator.NONE,
                TurSNFilterQueryOperator.NONE, Locale.US, new TurSNSitePostParamsBean(), null,
                mock(HttpServletRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(searchProcess);
        verifyNoInteractions(snapshots);
    }

    @Test
    void testSearchGetReturnsNotFoundWhenLanguageMissing() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        TurSNSearchParams params = new TurSNSearchParams();
        ResponseEntity<TurSNSiteSearchBean> notFound = ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        when(searchProcess.existsByTurSNSiteAndLanguage("site", params.getLocale())).thenReturn(false);
        org.mockito.Mockito.doReturn(notFound).when(service).notFoundResponse();

        ResponseEntity<TurSNSearchResponse> response = api.turSNSiteSearchSelectGet("site", params,
                TurSNFilterQueryOperator.NONE, TurSNFilterQueryOperator.NONE,
                Locale.US, null, false, mock(HttpServletRequest.class));

        assertThat(response).isSameAs(notFound);
        verify(service).setSearchParams(params, null, null, null, TurSNFilterQueryOperator.NONE,
                TurSNFilterQueryOperator.NONE, Locale.US);
    }

    /**
     * T856 / §LXV.1 — the GET mapping keeps returning the legacy link-bearing bean
     * <em>untouched</em> when the flag is absent, which is what every existing
     * caller sends.
     */
    @Test
    void testSearchGetKeepsTheLegacyBodyWhenNoResponseFormatIsAsked() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        TurSNSearchParams params = new TurSNSearchParams();
        HttpServletRequest request = mock(HttpServletRequest.class);
        TurSNSiteSearchBean legacyBody = new TurSNSiteSearchBean();

        when(searchProcess.existsByTurSNSiteAndLanguage("site", params.getLocale())).thenReturn(true);
        when(service.executeGetSearch(params, request, "site"))
                .thenReturn(new ResponseEntity<>(legacyBody, HttpStatus.OK));

        ResponseEntity<TurSNSearchResponse> response = api.turSNSiteSearchSelectGet("site", params,
                TurSNFilterQueryOperator.NONE, TurSNFilterQueryOperator.NONE, Locale.US, null,
                false, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(legacyBody);
    }

    /**
     * T856 / §LXV.1 — the structured shape is now reachable over GET, so the two
     * shapes can be A/B'd against one query on either transport. The value is
     * parsed case-insensitively because it is typed into a URL.
     */
    @Test
    void testSearchGetReturnsTheStructuredShapeWhenAsked() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ("guitar");
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(searchProcess.existsByTurSNSiteAndLanguage("site", params.getLocale())).thenReturn(true);
        when(service.executeGetSearch(params, request, "site"))
                .thenReturn(new ResponseEntity<>(new TurSNSiteSearchBean(), HttpStatus.OK));

        ResponseEntity<TurSNSearchResponse> response = api.turSNSiteSearchSelectGet("site", params,
                TurSNFilterQueryOperator.NONE, TurSNFilterQueryOperator.NONE, Locale.US,
                "structured", false, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(TurSNStructuredSearchBean.class);
        TurSNStructuredSearchBean structured = (TurSNStructuredSearchBean) response.getBody();
        assertThat(structured.getRequest().getQ()).isEqualTo("guitar");
        assertThat(structured.getRequest().getLocale()).isEqualTo("en_US");
        assertThat(structured.getRequest().getResponseFormat())
                .isEqualTo(TurSNResponseFormat.STRUCTURED);
    }

    /**
     * T856 — an unknown value is a client error, not a silent fall back to the
     * legacy shape: a typo'd A/B URL must not quietly return the wrong body.
     */
    @Test
    void testSearchGetRejectsAnUnknownResponseFormat() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        TurSNSearchParams params = new TurSNSearchParams();
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertThatThrownBy(() -> api.turSNSiteSearchSelectGet("site", params,
                TurSNFilterQueryOperator.NONE, TurSNFilterQueryOperator.NONE, Locale.US,
                "compact", false, request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(searchProcess);
    }

    @Test
    void testSearchListReturnsOkWhenSiteExists() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        TurSNSearchParams params = new TurSNSearchParams();
        HttpServletRequest request = mock(HttpServletRequest.class);
        com.viglet.turing.commons.sn.search.TurSNSiteSearchContext context =
                mock(com.viglet.turing.commons.sn.search.TurSNSiteSearchContext.class);
        List<Object> expected = List.of("one");

        when(searchProcess.existsByTurSNSiteAndLanguage("site", params.getLocale())).thenReturn(true);
        when(service.getTurSNSiteSearchContext(params, request, "site")).thenReturn(context);
        when(searchProcess.searchList(context)).thenReturn(expected);

        ResponseEntity<List<Object>> response = api.turSNSiteSearchSelectListGet("site", params,
                TurSNFilterQueryOperator.NONE, TurSNFilterQueryOperator.NONE, Locale.US, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(service).setSearchParams(params, null, null, null, TurSNFilterQueryOperator.NONE,
                TurSNFilterQueryOperator.NONE, Locale.US);
    }

    @Test
    void testSearchPostReturnsOkWhenPrincipalPresent() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        TurSNSearchParams params = new TurSNSearchParams();
        TurSNSitePostParamsBean postParams = new TurSNSitePostParamsBean();
        postParams.setLocale("pt_BR");
        Principal principal = () -> "user";
        HttpServletRequest request = mock(HttpServletRequest.class);
        ResponseEntity<TurSNSiteSearchBean> expected = new ResponseEntity<>(new TurSNSiteSearchBean(), HttpStatus.OK);

        when(service.determineLocale(postParams, Locale.US)).thenReturn(Locale.CANADA);
        when(searchProcess.existsByTurSNSiteAndLanguage("site", params.getLocale())).thenReturn(true);
        when(service.executePostSearch(params, postParams, request, "site")).thenReturn(expected);

        ResponseEntity<TurSNSiteSearchBean> response = api.turSNSiteSearchSelectPost("site", params,
                TurSNFilterQueryOperator.NONE, TurSNFilterQueryOperator.NONE, Locale.US,
                postParams, principal, request);

        assertThat(response).isSameAs(expected);
        verify(service).setSearchParams(params, null, null, null, TurSNFilterQueryOperator.NONE,
                TurSNFilterQueryOperator.NONE, Locale.CANADA);
    }

    /**
     * T707 — the public POST contract must serve an anonymous caller (no
     * {@link Principal}), unlike the impersonation POST above.
     */
    @Test
    void testSearchQueryPostServesAnonymousCallers() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        TurSNSearchRequest searchRequest = new TurSNSearchRequest();
        searchRequest.setLocale("en_US");
        searchRequest.setFq(List.of("section:RAG & Chat"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        ResponseEntity<TurSNSiteSearchBean> expected =
                new ResponseEntity<>(new TurSNSiteSearchBean(), HttpStatus.OK);

        when(searchProcess.existsByTurSNSiteAndLanguage(eq("site"), eq(Locale.US))).thenReturn(true);
        when(service.executePublicPostSearch(eq(searchRequest), any(), eq(request), eq("site")))
                .thenReturn(expected);

        ResponseEntity<TurSNSearchResponse> response =
                api.turSNSiteSearchQueryPost("site", searchRequest, request);

        // Default format is LINKS, so the legacy body is handed back untouched.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected.getBody());
        verifyNoInteractions(snapshots);
    }

    @Test
    void testSearchQueryPostFallsBackToTheSiteDefaultLocale() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        HttpServletRequest request = mock(HttpServletRequest.class);
        ResponseEntity<TurSNSiteSearchBean> notFound =
                ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        when(service.resolveDefaultLocale("site")).thenReturn(Locale.CANADA);
        when(searchProcess.existsByTurSNSiteAndLanguage("site", Locale.CANADA)).thenReturn(false);
        org.mockito.Mockito.doReturn(notFound).when(service).notFoundResponse();

        ResponseEntity<TurSNSearchResponse> response =
                api.turSNSiteSearchQueryPost("site", new TurSNSearchRequest(), request);

        assertThat(response).isSameAs(notFound);
    }

    /**
     * T712 — the select-list POST variant serves anonymous callers over the same
     * JSON body, and hands the request straight to the list pipeline.
     */
    @Test
    void testSearchListQueryPostServesAnonymousCallers() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        TurSNSearchRequest searchRequest = new TurSNSearchRequest();
        searchRequest.setLocale("en_US");
        searchRequest.setFq(List.of("section:RAG & Chat"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        ResponseEntity<List<Object>> expected =
                new ResponseEntity<>(List.of("term"), HttpStatus.OK);

        when(searchProcess.existsByTurSNSiteAndLanguage(eq("site"), eq(Locale.US))).thenReturn(true);
        when(service.executePublicPostSearchList(eq(searchRequest), any(), eq(request), eq("site")))
                .thenReturn(expected);

        ResponseEntity<List<Object>> response =
                api.turSNSiteSearchSelectListQueryPost("site", searchRequest, request);

        assertThat(response).isSameAs(expected);
        verifyNoInteractions(snapshots);
    }

    /**
     * T712 — an unknown site/locale pair 404s on the list POST just like it does
     * on the GET mapping and on {@code /search/query}.
     */
    @Test
    void testSearchListQueryPostNotFoundForUnknownLocale() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        HttpServletRequest request = mock(HttpServletRequest.class);
        ResponseEntity<List<Object>> notFound = ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        when(service.resolveDefaultLocale("site")).thenReturn(Locale.CANADA);
        when(searchProcess.existsByTurSNSiteAndLanguage("site", Locale.CANADA)).thenReturn(false);
        org.mockito.Mockito.doReturn(notFound).when(service).notFoundResponse();

        ResponseEntity<List<Object>> response =
                api.turSNSiteSearchSelectListQueryPost("site", new TurSNSearchRequest(), request);

        assertThat(response).isSameAs(notFound);
    }

    /**
     * T708 — asking for STRUCTURED swaps the response shape without touching the
     * search itself.
     */
    @Test
    void testSearchQueryPostReturnsTheStructuredShapeWhenAsked() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        TurSNSearchRequest searchRequest = new TurSNSearchRequest();
        searchRequest.setLocale("en_US");
        searchRequest.setResponseFormat(TurSNResponseFormat.STRUCTURED);
        HttpServletRequest request = mock(HttpServletRequest.class);
        TurSNSiteSearchBean legacyBody = new TurSNSiteSearchBean();

        when(searchProcess.existsByTurSNSiteAndLanguage(eq("site"), eq(Locale.US))).thenReturn(true);
        when(service.executePublicPostSearch(eq(searchRequest), any(), eq(request), eq("site")))
                .thenReturn(new ResponseEntity<>(legacyBody, HttpStatus.OK));

        ResponseEntity<TurSNSearchResponse> response =
                api.turSNSiteSearchQueryPost("site", searchRequest, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(TurSNStructuredSearchBean.class);
        TurSNStructuredSearchBean structured = (TurSNStructuredSearchBean) response.getBody();
        assertThat(structured.getRequest().getLocale()).isEqualTo("en_US");
        assertThat(structured.getRequest().getQ()).isEqualTo("*");
    }

    /**
     * T857 / §LXV.2 — the default structured body stays lean: no sort options
     * unless they were asked for.
     */
    @Test
    void testStructuredResponseOmitsSortOptionsByDefault() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        TurSNSearchRequest searchRequest = new TurSNSearchRequest();
        searchRequest.setLocale("en_US");
        searchRequest.setResponseFormat(TurSNResponseFormat.STRUCTURED);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(searchProcess.existsByTurSNSiteAndLanguage(eq("site"), eq(Locale.US))).thenReturn(true);
        when(service.executePublicPostSearch(eq(searchRequest), any(), eq(request), eq("site")))
                .thenReturn(new ResponseEntity<>(new TurSNSiteSearchBean(), HttpStatus.OK));

        TurSNStructuredSearchBean structured = (TurSNStructuredSearchBean) api
                .turSNSiteSearchQueryPost("site", searchRequest, request).getBody();

        assertThat(structured.getSortOptions()).isNull();
        verifyNoInteractions(snapshots);
    }

    /**
     * T857 / §LXV.2 — asking for them collapses the sort control to one
     * round-trip, and the inlined list is the one the dedicated endpoint serves.
     */
    @Test
    void testStructuredResponseInlinesSortOptionsWhenAsked() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        TurSNSearchRequest searchRequest = new TurSNSearchRequest();
        searchRequest.setLocale("en_US");
        searchRequest.setResponseFormat(TurSNResponseFormat.STRUCTURED);
        searchRequest.setIncludeSortOptions(true);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(searchProcess.existsByTurSNSiteAndLanguage(eq("site"), eq(Locale.US))).thenReturn(true);
        when(service.executePublicPostSearch(eq(searchRequest), any(), eq(request), eq("site")))
                .thenReturn(new ResponseEntity<>(new TurSNSiteSearchBean(), HttpStatus.OK));
        when(snapshots.getSnapshot("site", null)).thenReturn(Optional.of(snapshot("site")));

        TurSNStructuredSearchBean structured = (TurSNStructuredSearchBean) api
                .turSNSiteSearchQueryPost("site", searchRequest, request).getBody();

        assertThat(structured.getSortOptions()).extracting(TurSNSortOptionBean::value)
                .containsExactly("relevance", "newest", "oldest");
        // The echo does not carry the flag: the list is a first-frame need, so
        // re-sending the echoed request asks for results, not for the list again.
        assertThat(structured.getRequest().isIncludeSortOptions()).isFalse();
    }

    /**
     * T857 — the flag only makes sense with a structured body; a {@code LINKS}
     * response is frozen and must stay byte-identical.
     */
    @Test
    void testSortOptionsAreNotInlinedIntoTheLegacyResponse() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        TurSNSearchRequest searchRequest = new TurSNSearchRequest();
        searchRequest.setLocale("en_US");
        searchRequest.setIncludeSortOptions(true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        TurSNSiteSearchBean legacyBody = new TurSNSiteSearchBean();

        when(searchProcess.existsByTurSNSiteAndLanguage(eq("site"), eq(Locale.US))).thenReturn(true);
        when(service.executePublicPostSearch(eq(searchRequest), any(), eq(request), eq("site")))
                .thenReturn(new ResponseEntity<>(legacyBody, HttpStatus.OK));

        assertThat(api.turSNSiteSearchQueryPost("site", searchRequest, request).getBody())
                .isSameAs(legacyBody);
        verifyNoInteractions(snapshots);
    }

    /**
     * T857 — an unknown site cannot produce an option list; the search response
     * still stands rather than failing over a decoration.
     */
    @Test
    void testInlineSortOptionsAreOmittedWhenTheSnapshotIsMissing() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        TurSNSearchRequest searchRequest = new TurSNSearchRequest();
        searchRequest.setLocale("en_US");
        searchRequest.setResponseFormat(TurSNResponseFormat.STRUCTURED);
        searchRequest.setIncludeSortOptions(true);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(searchProcess.existsByTurSNSiteAndLanguage(eq("site"), eq(Locale.US))).thenReturn(true);
        when(service.executePublicPostSearch(eq(searchRequest), any(), eq(request), eq("site")))
                .thenReturn(new ResponseEntity<>(new TurSNSiteSearchBean(), HttpStatus.OK));
        when(snapshots.getSnapshot("site", null)).thenReturn(Optional.empty());

        TurSNStructuredSearchBean structured = (TurSNStructuredSearchBean) api
                .turSNSiteSearchQueryPost("site", searchRequest, request).getBody();

        assertThat(structured).isNotNull();
        assertThat(structured.getSortOptions()).isNull();
    }

    /**
     * T857 — reachable over GET too, on the same terms as the T856 flag.
     */
    @Test
    void testSearchGetInlinesSortOptionsWhenAsked() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        TurSNSearchParams params = new TurSNSearchParams();
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(searchProcess.existsByTurSNSiteAndLanguage("site", params.getLocale())).thenReturn(true);
        when(service.executeGetSearch(params, request, "site"))
                .thenReturn(new ResponseEntity<>(new TurSNSiteSearchBean(), HttpStatus.OK));
        when(snapshots.getSnapshot("site", null)).thenReturn(Optional.of(snapshot("site")));

        TurSNStructuredSearchBean structured = (TurSNStructuredSearchBean) api
                .turSNSiteSearchSelectGet("site", params, TurSNFilterQueryOperator.NONE,
                        TurSNFilterQueryOperator.NONE, Locale.US, "STRUCTURED", true, request)
                .getBody();

        assertThat(structured.getSortOptions()).hasSize(3);
    }

    @Test
    void testLatestReturnsUnauthorizedWhenPrincipalMissing() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));

        ResponseEntity<List<String>> response = api.turSNSiteSearchLatestImpersonate("site", 5, "en_US",
                Optional.empty(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testLocalesReturnsResponseWhenSiteExists() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        List<TurSNSiteLocaleBean> locales = List.of(new TurSNSiteLocaleBean());

        TurSNSiteSearchSnapshot snap = snapshot("site");
        when(snapshots.getSnapshot("site", null)).thenReturn(Optional.of(snap));
        when(searchProcess.responseLocales(eq(snap), any())).thenReturn(locales);

        List<TurSNSiteLocaleBean> response = api.turSNSiteSearchLocale("site");

        assertThat(response).isEqualTo(locales);
    }

    @Test
    void testLocalesReturnsEmptyWhenSiteMissing() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));

        when(snapshots.getSnapshot("site", null)).thenReturn(Optional.empty());

        assertThat(api.turSNSiteSearchLocale("site")).isEqualTo(Collections.emptyList());
        verifyNoInteractions(searchProcess);
    }

    @Test
    void testSortOptionsReturnsBuiltInPlusCustom() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        TurSNSiteSearchSnapshot snap = snapshot("site");
        when(snapshots.getSnapshot("site", null)).thenReturn(Optional.of(snap));

        ResponseEntity<List<TurSNSortOptionBean>> response =
                api.turSNSiteSearchSortOptions("site");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(TurSNSortOptionBean::value)
                .contains("relevance", "newest", "oldest");
    }

    @Test
    void testSortOptionsReturnsNotFoundWhenSiteMissing() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots,
                mock(TurSNSimilarDocumentsService.class),
                mock(TurSNDuplicateClusteringService.class));
        when(snapshots.getSnapshot("site", null)).thenReturn(Optional.empty());

        ResponseEntity<List<TurSNSortOptionBean>> response =
                api.turSNSiteSearchSortOptions("site");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

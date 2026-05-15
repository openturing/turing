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
import com.viglet.turing.commons.sn.bean.TurSNSiteLocaleBean;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.sn.TurSNSearchProcess;
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
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots);

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
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots);
        TurSNSearchParams params = new TurSNSearchParams();
        ResponseEntity<TurSNSiteSearchBean> notFound = ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        when(searchProcess.existsByTurSNSiteAndLanguage("site", params.getLocale())).thenReturn(false);
        org.mockito.Mockito.doReturn(notFound).when(service).notFoundResponse();

        ResponseEntity<TurSNSiteSearchBean> response = api.turSNSiteSearchSelectGet("site", params,
                TurSNFilterQueryOperator.NONE, TurSNFilterQueryOperator.NONE,
                Locale.US, mock(HttpServletRequest.class));

        assertThat(response).isSameAs(notFound);
        verify(service).setSearchParams(params, null, null, null, TurSNFilterQueryOperator.NONE,
                TurSNFilterQueryOperator.NONE, Locale.US);
    }

    @Test
    void testSearchListReturnsOkWhenSiteExists() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots);
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
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots);
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

    @Test
    void testLatestReturnsUnauthorizedWhenPrincipalMissing() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots);

        ResponseEntity<List<String>> response = api.turSNSiteSearchLatestImpersonate("site", 5, "en_US",
                Optional.empty(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testLocalesReturnsResponseWhenSiteExists() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots);
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
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots);

        when(snapshots.getSnapshot("site", null)).thenReturn(Optional.empty());

        assertThat(api.turSNSiteSearchLocale("site")).isEqualTo(Collections.emptyList());
        verifyNoInteractions(searchProcess);
    }

    @Test
    void testSortOptionsReturnsBuiltInPlusCustom() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots);
        TurSNSiteSearchSnapshot snap = snapshot("site");
        when(snapshots.getSnapshot("site", null)).thenReturn(Optional.of(snap));

        ResponseEntity<List<TurSNSiteSearchAPI.TurSNSiteSortOptionBean>> response =
                api.turSNSiteSearchSortOptions("site");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(TurSNSiteSearchAPI.TurSNSiteSortOptionBean::value)
                .contains("relevance", "newest", "oldest");
    }

    @Test
    void testSortOptionsReturnsNotFoundWhenSiteMissing() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchAPI api = new TurSNSiteSearchAPI(service, searchProcess, snapshots);
        when(snapshots.getSnapshot("site", null)).thenReturn(Optional.empty());

        ResponseEntity<List<TurSNSiteSearchAPI.TurSNSiteSortOptionBean>> response =
                api.turSNSiteSearchSortOptions("site");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

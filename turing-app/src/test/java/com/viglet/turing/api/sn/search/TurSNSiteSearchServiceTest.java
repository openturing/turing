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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.net.URI;
import java.security.Principal;
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
import org.springframework.mock.web.MockHttpServletRequest;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchLatestRequestBean;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.sn.TurSNSearchProcess;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshot;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshotService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit tests for TurSNSiteSearchService. The service now reads site
 * configuration through {@link TurSNSiteSearchSnapshotService}, so all DB
 * dependencies collapse to one mock that returns a pre-built snapshot.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteSearchServiceTest {

    private static TurSNSiteSearchSnapshot snapshot(String name, boolean hasHl,
            List<TurSNSiteLocale> locales) {
        TurSNSite site = new TurSNSite();
        site.setId("site-id");
        site.setName(name);
        site.setHl(1);
        return new TurSNSiteSearchSnapshot(site, null, "solr", hasHl,
                List.of(), Set.of(), Map.of(), Map.of(), locales, List.of());
    }

    private static TurSNSiteLocale locale(Locale language, int position) {
        TurSNSiteLocale l = new TurSNSiteLocale();
        l.setLanguage(language);
        l.setCore("core");
        l.setPosition(position);
        return l;
    }

    @Test
    void testExecuteSearchReturnsOkResponse() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchCachedAPI cacheApi = mock(TurSNSiteSearchCachedAPI.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchService service = new TurSNSiteSearchService(searchProcess, cacheApi, snapshots);
        TurSNSiteSearchContext context = mock(TurSNSiteSearchContext.class);
        TurSNSiteSearchBean expected = new TurSNSiteSearchBean();

        when(searchProcess.search(context)).thenReturn(expected);

        ResponseEntity<TurSNSiteSearchBean> response = service.executeSearch(context);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(searchProcess).search(context);
    }

    @Test
    void testExecuteSearchWithCacheUsesCacheKey() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchCachedAPI cacheApi = mock(TurSNSiteSearchCachedAPI.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchService service = new TurSNSiteSearchService(searchProcess, cacheApi, snapshots);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn("q=java");

        TurSNConfig config = new TurSNConfig();
        TurSNSearchParams params = new TurSNSearchParams();
        params.setLocale(Locale.US);
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", config,
                new TurSEParameters(params), Locale.US, URI.create("http://example.com"));
        TurSNSiteSearchBean expected = new TurSNSiteSearchBean();

        when(cacheApi.searchCached("site_q=java", context)).thenReturn(expected);

        ResponseEntity<TurSNSiteSearchBean> response = service.executeSearchWithCache(request, context);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(cacheApi).searchCached("site_q=java", context);
    }

    @Test
    void testSetSearchParamsAppliesNonNullValues() {
        TurSNSiteSearchService service = new TurSNSiteSearchService(mock(TurSNSearchProcess.class),
                mock(TurSNSiteSearchCachedAPI.class), mock(TurSNSiteSearchSnapshotService.class));
        TurSNSearchParams params = new TurSNSearchParams();

        service.setSearchParams(params, List.of("fq"), List.of("fqAnd"), List.of("fqOr"),
                TurSNFilterQueryOperator.AND, TurSNFilterQueryOperator.OR, Locale.CANADA);

        assertThat(params.getFq()).containsExactly("fq");
        assertThat(params.getFqAnd()).containsExactly("fqAnd");
        assertThat(params.getFqOr()).containsExactly("fqOr");
        assertThat(params.getFqOp()).isEqualTo(TurSNFilterQueryOperator.AND);
        assertThat(params.getFqiOp()).isEqualTo(TurSNFilterQueryOperator.OR);
        assertThat(params.getLocale()).isEqualTo(Locale.CANADA);
    }

    @Test
    void testDetermineLocalePrefersPostLocale() {
        TurSNSiteSearchService service = new TurSNSiteSearchService(mock(TurSNSearchProcess.class),
                mock(TurSNSiteSearchCachedAPI.class), mock(TurSNSiteSearchSnapshotService.class));
        TurSNSitePostParamsBean postParams = new TurSNSitePostParamsBean();
        postParams.setLocale("pt_BR");

        assertThat(service.determineLocale(postParams, Locale.US)).hasToString("pt_BR");
    }

    @Test
    void testDetermineLocaleFallsBackToRequestLocale() {
        TurSNSiteSearchService service = new TurSNSiteSearchService(mock(TurSNSearchProcess.class),
                mock(TurSNSiteSearchCachedAPI.class), mock(TurSNSiteSearchSnapshotService.class));
        TurSNSitePostParamsBean postParams = new TurSNSitePostParamsBean();

        assertThat(service.determineLocale(postParams, Locale.UK)).isEqualTo(Locale.UK);
    }

    @Test
    void testIsLatestImpersonateReturnsRequestUserId() {
        TurSNSiteSearchService service = new TurSNSiteSearchService(mock(TurSNSearchProcess.class),
                mock(TurSNSiteSearchCachedAPI.class), mock(TurSNSiteSearchSnapshotService.class));
        TurSNSearchLatestRequestBean bean = new TurSNSearchLatestRequestBean();
        bean.setUserId("impersonated");
        Principal principal = () -> "principal";

        assertThat(service.isLatestImpersonate(Optional.of(bean), principal)).isEqualTo("impersonated");
    }

    @Test
    void testIsLatestImpersonateFallsBackToPrincipal() {
        TurSNSiteSearchService service = new TurSNSiteSearchService(mock(TurSNSearchProcess.class),
                mock(TurSNSiteSearchCachedAPI.class), mock(TurSNSiteSearchSnapshotService.class));
        Principal principal = () -> "principal";

        assertThat(service.isLatestImpersonate(Optional.empty(), principal)).isEqualTo("principal");
    }

    @Test
    void testExecuteGetSearchUsesCacheWhenEnabled() throws Exception {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchCachedAPI cacheApi = mock(TurSNSiteSearchCachedAPI.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchService service = new TurSNSiteSearchService(searchProcess, cacheApi, snapshots);
        Field field = TurSNSiteSearchService.class.getDeclaredField("searchCacheEnabled");
        field.setAccessible(true);
        field.set(service, true);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn("q=hello");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://example.com/api"));

        TurSNSearchParams params = new TurSNSearchParams();
        params.setLocale(Locale.US);
        when(snapshots.getSnapshot("site", null)).thenReturn(Optional.of(snapshot("site", false, List.of())));
        TurSNSiteSearchBean expected = new TurSNSiteSearchBean();
        when(cacheApi.searchCached(eq("site_q=hello"), any(TurSNSiteSearchContext.class)))
                .thenReturn(expected);

        ResponseEntity<TurSNSiteSearchBean> response = service.executeGetSearch(params, request, "site");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(cacheApi).searchCached(eq("site_q=hello"), any(TurSNSiteSearchContext.class));
    }

    @Test
    void testNotFoundResponseReturns404() {
        TurSNSiteSearchService service = new TurSNSiteSearchService(mock(TurSNSearchProcess.class),
                mock(TurSNSiteSearchCachedAPI.class), mock(TurSNSiteSearchSnapshotService.class));

        ResponseEntity<Object> response = service.notFoundResponse();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void testHasSiteHLFieldsReturnsTrueWhenSnapshotReportsTrue() {
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchService service = new TurSNSiteSearchService(mock(TurSNSearchProcess.class),
                mock(TurSNSiteSearchCachedAPI.class), snapshots);
        when(snapshots.getSnapshot("site", null)).thenReturn(Optional.of(snapshot("site", true, List.of())));

        assertThat(service.hasSiteHLFields("site")).isTrue();
    }

    @Test
    void testHasSiteHLFieldsReturnsFalseWhenSnapshotReportsFalseOrMissing() {
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchService service = new TurSNSiteSearchService(mock(TurSNSearchProcess.class),
                mock(TurSNSiteSearchCachedAPI.class), snapshots);
        when(snapshots.getSnapshot("missing", null)).thenReturn(Optional.empty());

        assertThat(service.hasSiteHLFields("missing")).isFalse();
    }

    @Test
    void testResolveDefaultLocaleReturnsFirstLocale() {
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchService service = new TurSNSiteSearchService(mock(TurSNSearchProcess.class),
                mock(TurSNSiteSearchCachedAPI.class), snapshots);
        when(snapshots.getSnapshot("site", null)).thenReturn(Optional.of(
                snapshot("site", false, List.of(locale(Locale.US, 0), locale(Locale.CANADA, 1)))));

        assertThat(service.resolveDefaultLocale("site")).isEqualTo(Locale.US);
    }

    @Test
    void testResolveDefaultLocaleReturnsNullWhenSiteMissing() {
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchService service = new TurSNSiteSearchService(mock(TurSNSearchProcess.class),
                mock(TurSNSiteSearchCachedAPI.class), snapshots);
        when(snapshots.getSnapshot("missing", null)).thenReturn(Optional.empty());

        assertThat(service.resolveDefaultLocale("missing")).isNull();
    }

    @Test
    void testGetTurSNConfigEnablesHlOnlyWhenSnapshotHasHlFields() {
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchService service = new TurSNSiteSearchService(mock(TurSNSearchProcess.class),
                mock(TurSNSiteSearchCachedAPI.class), snapshots);
        when(snapshots.getSnapshot("site", null)).thenReturn(Optional.of(snapshot("site", true, List.of())));

        TurSNConfig config = service.getTurSNConfig("site");

        assertThat(config.isHlEnabled()).isTrue();
    }

    @Test
    void testExecutePostSearchUsesCacheAndUpdatesLocale() throws Exception {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteSearchCachedAPI cacheApi = mock(TurSNSiteSearchCachedAPI.class);
        TurSNSiteSearchSnapshotService snapshots = mock(TurSNSiteSearchSnapshotService.class);
        TurSNSiteSearchService service = new TurSNSiteSearchService(searchProcess, cacheApi, snapshots);
        Field field = TurSNSiteSearchService.class.getDeclaredField("searchCacheEnabled");
        field.setAccessible(true);
        field.set(service, true);

        TurSNSearchParams params = new TurSNSearchParams();
        TurSNSitePostParamsBean postParams = new TurSNSitePostParamsBean();
        postParams.setLocale("pt_BR");
        postParams.setTargetingRules(List.of("role:admin"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/sn/site/search");
        when(snapshots.getSnapshot("site", null)).thenReturn(Optional.of(snapshot("site", false, List.of())));
        ResponseEntity<TurSNSiteSearchBean> expected = new ResponseEntity<>(new TurSNSiteSearchBean(), HttpStatus.OK);

        when(searchProcess.requestTargetingRules(postParams.getTargetingRules()))
                .thenReturn(List.of("role:\"admin\""));
        TurSNSiteSearchService spyService = org.mockito.Mockito.spy(service);
        doReturn(expected).when(spyService).executeSearchWithCache(eq(request), any(TurSNSiteSearchContext.class));

        ResponseEntity<TurSNSiteSearchBean> response = spyService.executePostSearch(params, postParams, request, "site");

        assertThat(response).isSameAs(expected);
        assertThat(params.getLocale()).hasToString("pt_BR");
        assertThat(postParams.getTargetingRules()).containsExactly("role:\"admin\"");
        verify(searchProcess).requestTargetingRules(List.of("role:admin"));
    }
}

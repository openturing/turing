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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSiteLocaleBean;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.TurSNSearchProcess;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit tests for TurSNSiteSearchV2API.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteSearchV2APITest {

    @Test
    void testSearchPostReturnsUnauthorizedWhenPrincipalMissing() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository repository = mock(TurSNSiteRepository.class);
        TurSNSiteSearchV2API api = new TurSNSiteSearchV2API(service, searchProcess, repository);

        ResponseEntity<TurSNSiteSearchBean> response = api.turSNSiteSearchSelectPost("site",
                new TurSNSearchParams(), new TurSNSitePostParamsBean(), null, mock(HttpServletRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(searchProcess);
        verifyNoInteractions(repository);
    }

    @Test
    void testSearchGetReturnsNotFoundWhenLanguageMissing() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository repository = mock(TurSNSiteRepository.class);
        TurSNSiteSearchV2API api = new TurSNSiteSearchV2API(service, searchProcess, repository);
        TurSNSearchParams params = new TurSNSearchParams();
        ResponseEntity<TurSNSiteSearchBean> notFound = ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        when(searchProcess.existsByTurSNSiteAndLanguage("site", params.getLocale())).thenReturn(false);
        org.mockito.Mockito.doReturn(notFound).when(service).notFoundResponse();

        ResponseEntity<TurSNSiteSearchBean> response = api.turSNSiteSearchSelectGet("site", params,
                mock(HttpServletRequest.class));

        assertThat(response).isSameAs(notFound);
    }

    @Test
    void testSearchListReturnsOkWhenSiteExists() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository repository = mock(TurSNSiteRepository.class);
        TurSNSiteSearchV2API api = new TurSNSiteSearchV2API(service, searchProcess, repository);
        TurSNSearchParams params = new TurSNSearchParams();
        HttpServletRequest request = mock(HttpServletRequest.class);
        TurSNSite site = mock(TurSNSite.class);
        TurSNSiteSearchContext context = mock(TurSNSiteSearchContext.class);
        List<Object> expected = List.of("one");

        when(searchProcess.existsByTurSNSiteAndLanguage("site", params.getLocale())).thenReturn(true);
        when(repository.findByName("site")).thenReturn(Optional.of(site));
        when(service.getTurSNSiteSearchContext(params, request, site)).thenReturn(context);
        when(searchProcess.searchList(context)).thenReturn(expected);

        ResponseEntity<List<Object>> response = api.turSNSiteSearchSelectListGet("site", params, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void testSearchGetReturnsNotFoundWhenSiteMissing() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository repository = mock(TurSNSiteRepository.class);
        TurSNSiteSearchV2API api = new TurSNSiteSearchV2API(service, searchProcess, repository);
        TurSNSearchParams params = new TurSNSearchParams();
        ResponseEntity<TurSNSiteSearchBean> notFound = ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        when(searchProcess.existsByTurSNSiteAndLanguage("site", params.getLocale())).thenReturn(true);
        when(repository.findByName("site")).thenReturn(Optional.empty());
        org.mockito.Mockito.doReturn(notFound).when(service).notFoundResponse();

        ResponseEntity<TurSNSiteSearchBean> response = api.turSNSiteSearchSelectGet("site", params,
                mock(HttpServletRequest.class));

        assertThat(response).isSameAs(notFound);
    }

    @Test
    void testSearchListReturnsNotFoundWhenSiteMissing() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository repository = mock(TurSNSiteRepository.class);
        TurSNSiteSearchV2API api = new TurSNSiteSearchV2API(service, searchProcess, repository);
        TurSNSearchParams params = new TurSNSearchParams();
        ResponseEntity<TurSNSiteSearchBean> notFound = ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        when(searchProcess.existsByTurSNSiteAndLanguage("site", params.getLocale())).thenReturn(true);
        when(repository.findByName("site")).thenReturn(Optional.empty());
        org.mockito.Mockito.doReturn(notFound).when(service).notFoundResponse();

        ResponseEntity<List<Object>> response = api.turSNSiteSearchSelectListGet("site", params,
                mock(HttpServletRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testSearchPostReturnsOkWhenPrincipalPresent() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository repository = mock(TurSNSiteRepository.class);
        TurSNSiteSearchV2API api = new TurSNSiteSearchV2API(service, searchProcess, repository);
        TurSNSearchParams params = new TurSNSearchParams();
        TurSNSitePostParamsBean postParams = new TurSNSitePostParamsBean();
        TurSNSite site = mock(TurSNSite.class);
        Principal principal = () -> "user";
        HttpServletRequest request = mock(HttpServletRequest.class);
        ResponseEntity<TurSNSiteSearchBean> expected = new ResponseEntity<>(new TurSNSiteSearchBean(), HttpStatus.OK);

        when(searchProcess.existsByTurSNSiteAndLanguage("site", params.getLocale())).thenReturn(true);
        when(repository.findByName("site")).thenReturn(Optional.of(site));
        when(service.executePostSearch(params, postParams, request, site)).thenReturn(expected);

        ResponseEntity<TurSNSiteSearchBean> response = api.turSNSiteSearchSelectPost("site", params,
                postParams, principal, request);

        assertThat(response).isSameAs(expected);
    }

    @Test
    void testLatestReturnsUnauthorizedWhenPrincipalMissing() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository repository = mock(TurSNSiteRepository.class);
        TurSNSiteSearchV2API api = new TurSNSiteSearchV2API(service, searchProcess, repository);

        ResponseEntity<List<String>> response = api.turSNSiteSearchLatestImpersonate("site", 5, "en_US",
                Optional.empty(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testLocalesReturnsResponseWhenSiteExists() {
        TurSNSiteSearchService service = mock(TurSNSiteSearchService.class);
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository repository = mock(TurSNSiteRepository.class);
        TurSNSiteSearchV2API api = new TurSNSiteSearchV2API(service, searchProcess, repository);
        TurSNSite site = mock(TurSNSite.class);
        List<TurSNSiteLocaleBean> locales = List.of(new TurSNSiteLocaleBean());

        when(repository.findByName("site")).thenReturn(Optional.of(site));
        when(searchProcess.responseLocales(any(TurSNSite.class), any())).thenReturn(locales);

        List<TurSNSiteLocaleBean> response = api.turSNSiteSearchLocale("site");

        assertThat(response).isEqualTo(locales);
    }
}

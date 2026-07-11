/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.spring.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.turing.persistence.model.dev.token.TurDevToken;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteApiAuthMode;
import com.viglet.turing.persistence.repository.dev.token.TurDevTokenRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Unit tests for {@link TurSNSiteApiKeyGateFilter} (T233 / §VII.6.h) — pins the
 * per-site API-key gate decisions without booting Spring Security: non-SN paths
 * and PUBLIC/unknown sites pass straight through, API_KEY sites require a valid
 * Dev Token (header, query param, or an already-authenticated context) and
 * otherwise get HTTP 401.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteApiKeyGateFilterTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurDevTokenRepository turDevTokenRepository;
    @InjectMocks
    private TurSNSiteApiKeyGateFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static HttpServletRequest request(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getRequestURI()).thenReturn(uri);
        lenient().when(request.getContextPath()).thenReturn("");
        return request;
    }

    private void siteWithMode(String name, TurSNSiteApiAuthMode mode) {
        TurSNSite site = new TurSNSite();
        site.setApiAuthMode(mode);
        when(turSNSiteRepository.findByName(name)).thenReturn(Optional.of(site));
    }

    @Test
    void nonSnPath_passesThrough() throws Exception {
        HttpServletRequest request = request("/api/admin/whatever");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void publicSite_passesThrough() throws Exception {
        siteWithMode("mysite", TurSNSiteApiAuthMode.PUBLIC);
        HttpServletRequest request = request("/api/sn/mysite/search");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void unknownSite_passesThrough() throws Exception {
        when(turSNSiteRepository.findByName("ghost")).thenReturn(Optional.empty());
        HttpServletRequest request = request("/api/sn/ghost/search");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void apiKeySite_noKey_rejectsWith401() throws Exception {
        siteWithMode("secure", TurSNSiteApiAuthMode.API_KEY);
        HttpServletRequest request = request("/api/sn/secure/chat");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        assertThat(body.toString()).contains("Unauthorized");
    }

    @Test
    void apiKeySite_validHeaderKey_passesThrough() throws Exception {
        siteWithMode("secure", TurSNSiteApiAuthMode.API_KEY);
        HttpServletRequest request = request("/api/sn/secure/search");
        when(request.getHeader(TurAuthTokenHeaderFilter.KEY)).thenReturn("good-token");
        when(turDevTokenRepository.findByToken("good-token")).thenReturn(Optional.of(new TurDevToken()));
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void apiKeySite_queryParamKey_isRejected() throws Exception {
        // T646 / §XXXVII.8 — the `?apiKey=` query-param path was removed; a key
        // supplied only in the URL no longer authenticates.
        siteWithMode("secure", TurSNSiteApiAuthMode.API_KEY);
        HttpServletRequest request = request("/api/sn/secure/ac");
        when(request.getHeader(TurAuthTokenHeaderFilter.KEY)).thenReturn(null);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void apiKeySite_disabledOrExpiredToken_rejectsWith401() throws Exception {
        // T646 / §XXXVII.8 — a found-but-unusable token (disabled) is rejected.
        siteWithMode("secure", TurSNSiteApiAuthMode.API_KEY);
        HttpServletRequest request = request("/api/sn/secure/search");
        when(request.getHeader(TurAuthTokenHeaderFilter.KEY)).thenReturn("stale");
        TurDevToken disabled = new TurDevToken();
        disabled.setEnabled(false);
        when(turDevTokenRepository.findByToken("stale")).thenReturn(Optional.of(disabled));
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void apiKeySite_invalidKey_rejectsWith401() throws Exception {
        siteWithMode("secure", TurSNSiteApiAuthMode.API_KEY);
        HttpServletRequest request = request("/api/sn/secure/query");
        when(request.getHeader(TurAuthTokenHeaderFilter.KEY)).thenReturn("nope");
        when(turDevTokenRepository.findByToken("nope")).thenReturn(Optional.empty());
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void apiKeySite_authenticatedSession_passesThrough() throws Exception {
        siteWithMode("secure", TurSNSiteApiAuthMode.API_KEY);
        // An admin browser session (or an upstream-validated Key header) already
        // populated the SecurityContext — the gate must let it through.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, java.util.List.of()));
        HttpServletRequest request = request("/api/sn/secure/search");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void spellCheckPath_apiKeySite_noKey_rejectsWith401() throws Exception {
        siteWithMode("secure", TurSNSiteApiAuthMode.API_KEY);
        HttpServletRequest request = request("/api/sn/secure/en_US/spell-check");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
    }
}

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

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteApiAuthMode;
import com.viglet.turing.persistence.repository.dev.token.TurDevTokenRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * T233 / §VII.6.h — per-site API-key gate for the visitor-facing Semantic
 * Navigation surface. The public SN endpoints (search / autocomplete / query /
 * click / chat / spell-check) are declared {@code permitAll} in
 * {@code TurSecurityConfigProduction}; this filter re-checks the resolved site:
 * when its {@link TurSNSite#apiAuthMode} is {@link
 * TurSNSiteApiAuthMode#API_KEY} the request must carry a valid Turing-registered
 * API key (a Dev Token, reused) in the {@code Key} header or {@code apiKey}
 * query parameter — otherwise it is rejected with HTTP 401.
 *
 * <p>Sites left in the default {@link TurSNSiteApiAuthMode#PUBLIC} mode are
 * untouched, so existing deployments behave exactly as before. An
 * already-authenticated request (admin session, or a valid {@code Key} header
 * that {@link TurAuthTokenHeaderFilter} resolved upstream) also passes — the
 * explicit key check only adds the {@code apiKey} query-parameter convenience
 * for plain {@code <script>} / EDS GET usage.
 *
 * <p>The {@code findByName} lookup is {@code @Cacheable} and is the same call
 * the controllers make to resolve the site, so this adds no extra DB round-trip
 * on a warm cache.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurSNSiteApiKeyGateFilter extends OncePerRequestFilter {

    /** Query-parameter alternative to the {@code Key} header for simple GET clients. */
    private static final String API_KEY_PARAM = "apiKey";

    /**
     * Public SN endpoints keyed by site NAME: {@code /api/sn/{site}/(ac|search|
     * _search|click|query|chat)...}. Mirrors the {@code permitAll} matchers in
     * the security config. Admin endpoints (resolved by id, or {@code names} /
     * {@code console}) never match a real site name and so are never gated.
     */
    private static final Pattern PUBLIC_SN_PATH = Pattern.compile(
            "^/api/sn/([^/]+)/(?:ac|search|_search|click|query|chat)(?:/.*)?$");

    /** Spell-check carries an extra locale segment: {@code /api/sn/{site}/{locale}/spell-check}. */
    private static final Pattern SPELL_CHECK_PATH = Pattern.compile(
            "^/api/sn/([^/]+)/[^/]+/spell-check$");

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurDevTokenRepository turDevTokenRepository;

    public TurSNSiteApiKeyGateFilter(TurSNSiteRepository turSNSiteRepository,
            TurDevTokenRepository turDevTokenRepository) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turDevTokenRepository = turDevTokenRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain) throws ServletException, IOException {
        String siteName = resolveSiteName(request);
        if (siteName == null) {
            filterChain.doFilter(request, response);
            return;
        }

        TurSNSiteApiAuthMode authMode = turSNSiteRepository.findByName(siteName)
                .map(TurSNSite::getApiAuthMode)
                .orElse(null);
        // Unknown site, or an open (PUBLIC / null) site → behave exactly as before.
        if (authMode != TurSNSiteApiAuthMode.API_KEY) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isAuthenticated() || hasValidApiKey(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("[SNApiKeyGate] Rejecting unauthenticated request to protected site '{}' ({})",
                siteName, request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
    }

    /**
     * Returns the site name when the request targets a gated public SN endpoint,
     * or {@code null} otherwise. The path is taken relative to the context path.
     */
    private static String resolveSiteName(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        Matcher matcher = PUBLIC_SN_PATH.matcher(uri);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        matcher = SPELL_CHECK_PATH.matcher(uri);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    private static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private boolean hasValidApiKey(HttpServletRequest request) {
        String key = request.getHeader(TurAuthTokenHeaderFilter.KEY);
        if (key == null || key.isBlank()) {
            key = request.getParameter(API_KEY_PARAM);
        }
        return key != null && !key.isBlank()
                && turDevTokenRepository.findByToken(key.trim()).isPresent();
    }
}

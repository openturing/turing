/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.mcp.server;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.viglet.turing.persistence.model.dev.token.TurDevToken;
import com.viglet.turing.persistence.repository.dev.token.TurDevTokenRepository;
import com.viglet.turing.spring.security.auth.TurAuthTokenHeaderFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * T146 / §X.5.c — accepts a Turing <em>API key</em> ({@link TurDevToken}) as a
 * credential on the public {@code /mcp} surface, so an MCP-compatible client
 * (Claude Desktop, Cursor, an OpenAI Responses agent) can consume Turing's read
 * tools with a static key instead of running a full OAuth flow.
 *
 * <p>The key is read from the {@code Key} header (Turing's existing API-key
 * convention, shared with {@link TurAuthTokenHeaderFilter} and the public SN
 * gate) or the {@code apiKey} query parameter. Using a dedicated header — rather
 * than {@code Authorization: Bearer} — deliberately avoids colliding with the
 * OAuth 2.1 resource server (T246), which owns {@code Authorization: Bearer} for
 * JWTs: the two credential schemes coexist on the same endpoint, each on its own
 * header.
 *
 * <p>An authenticated API-key principal is granted <b>read-only</b> MCP access
 * ({@code SCOPE_mcp:read}). It deliberately does <em>not</em> carry the
 * {@code mcp:write} scope, so the gated ingestion tools stay reachable only via
 * an OAuth token that holds {@code turing.mcp-server.write-scope} — a public key
 * can never mutate the index. The filter is a no-op when the request already
 * carries an authentication (e.g. a valid OAuth bearer) or no key is present, so
 * it composes cleanly with the JWT chain.
 *
 * <p>Only wired into the {@code /mcp} {@link TurMcpSecurityConfig} chain, and only
 * when {@code turing.mcp-server.api-key-enabled=true} together with
 * {@code require-auth=true}. The main application chain is untouched.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
public class TurMcpApiKeyAuthenticationFilter extends OncePerRequestFilter {

    /** Read-only authority granted to an API-key MCP principal. */
    static final String SCOPE_MCP_READ = "SCOPE_mcp:read";

    private final TurDevTokenRepository turDevTokenRepository;

    public TurMcpApiKeyAuthenticationFilter(TurDevTokenRepository turDevTokenRepository) {
        this.turDevTokenRepository = turDevTokenRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String key = resolveKey(request);
            if (StringUtils.hasText(key)) {
                // T646 / §XXXVII.8 — reject disabled/expired tokens.
                turDevTokenRepository.findByToken(key.trim())
                        .filter(TurDevToken::isUsable)
                        .ifPresent(token -> authenticate(token, request));
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(TurDevToken token, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "mcp-apikey:" + token.getId(), null,
                List.of(new SimpleGrantedAuthority(SCOPE_MCP_READ)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("[MCP] /mcp authenticated via API key '{}' (read-only)", token.getId());
    }

    private static String resolveKey(HttpServletRequest request) {
        // T646 / §XXXVII.8 — header only; the `?apiKey=` query param was dropped
        // (secrets in the URL leak into logs / Referer / history).
        return request.getHeader(TurAuthTokenHeaderFilter.KEY);
    }
}

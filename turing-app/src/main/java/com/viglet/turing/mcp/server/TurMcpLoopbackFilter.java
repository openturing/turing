/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.mcp.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * T245 / §XIII.8 — the loopback-first trust boundary for the Turing MCP server.
 *
 * <p>Block I ships the MCP transport <em>before</em> the OAuth 2.1
 * resource-server gate (T246). To make sure that intermediate state can never
 * leak the tool catalog to a remote client, this filter rejects any
 * {@code /mcp} request whose remote address is not the loopback interface with
 * HTTP 403, as long as {@code turing.mcp-server.loopback-only=true} (the
 * default). A remote deployment must explicitly flip that flag off — and is
 * expected to do so only after wiring the T246 authentication.
 *
 * <p>The filter is registered (via {@link TurMcpServerConfig}) only when the
 * MCP server itself is enabled ({@code spring.ai.mcp.server.enabled=true}), and
 * is scoped to the {@code /mcp} path so it is a no-op for every other request.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class TurMcpLoopbackFilter extends OncePerRequestFilter {

    private final boolean loopbackOnly;

    public TurMcpLoopbackFilter(boolean loopbackOnly) {
        this.loopbackOnly = loopbackOnly;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (loopbackOnly && !isLoopback(request.getRemoteAddr())) {
            log.warn("[MCP] Rejected non-loopback request to {} from {} (turing.mcp-server.loopback-only=true)",
                    request.getRequestURI(), request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Turing MCP server is bound to loopback only");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isLoopback(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        try {
            return InetAddress.getByName(remoteAddr).isLoopbackAddress();
        } catch (UnknownHostException e) {
            log.debug("[MCP] Could not resolve remote address '{}' for loopback check", remoteAddr, e);
            return false;
        }
    }
}

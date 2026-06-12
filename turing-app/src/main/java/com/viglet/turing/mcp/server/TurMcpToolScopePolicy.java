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

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import com.viglet.turing.properties.TurConfigProperties;

/**
 * T246 / §XIII.6 — server-side authorization policy for MCP tool calls.
 *
 * <p>The MCP trust boundary is enforced in two layers: the {@code /mcp}
 * SecurityFilterChain rejects requests without a valid token (when
 * {@code turing.mcp-server.require-auth=true}), and this policy — applied
 * <em>inside</em> each tool call via {@link TurMcpScopeToolCallback} — decides
 * whether the authenticated caller may run a given tool. Enforcing it at the
 * callback is the "defense in depth" the design calls for: even a future bug
 * that lists a tool to a client that should not see it cannot let that client
 * <em>execute</em> it.
 *
 * <p>Posture (read-only by default): read tools are allowed for any caller that
 * cleared the endpoint gate; <em>write</em> tools (the gated ingestion tools
 * arrive in T253) additionally require the {@code writeScope} authority
 * ({@code SCOPE_mcp:write} by default). The set of write tools is empty today —
 * T253 registers its tool names here.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurMcpToolScopePolicy {

    /**
     * Names of MCP tools that mutate state and therefore require the write
     * scope (T253 ingestion tools).
     */
    static final Set<String> DEFAULT_WRITE_TOOLS = Set.of(
            "index_document", "deindex_document", "reindex_site");

    private final TurConfigProperties configProperties;
    private final Set<String> writeTools;

    @Autowired
    public TurMcpToolScopePolicy(TurConfigProperties configProperties) {
        this(configProperties, DEFAULT_WRITE_TOOLS);
    }

    /** Test/extension seam: lets T253 (and unit tests) supply the write-tool set. */
    TurMcpToolScopePolicy(TurConfigProperties configProperties, Set<String> writeTools) {
        this.configProperties = configProperties;
        this.writeTools = writeTools;
    }

    /**
     * @return {@code true} if {@code toolName} mutates state and so requires the
     *         write scope.
     */
    public boolean isWriteTool(String toolName) {
        return toolName != null && writeTools.contains(toolName);
    }

    /**
     * Decide whether {@code authentication} may invoke {@code toolName}.
     *
     * @param toolName       the MCP tool the caller is trying to run
     * @param authentication the current security context principal, or
     *                       {@code null} when running on the loopback/no-auth path
     * @return {@code true} if the call is permitted
     */
    public boolean isAllowed(String toolName, Authentication authentication) {
        boolean requireAuth = configProperties.getMcpServer().isRequireAuth();
        boolean authenticated = authentication != null && authentication.isAuthenticated();

        if (isWriteTool(toolName)) {
            // Write tools always require an authenticated caller holding the write scope.
            return authenticated && hasAuthority(authentication, configProperties.getMcpServer().getWriteScope());
        }
        // Read tools: allowed for any authenticated caller; when auth is not
        // required (loopback dev posture), allowed unconditionally.
        return !requireAuth || authenticated;
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        if (authority == null || authority.isBlank()) {
            return true;
        }
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}

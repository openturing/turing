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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.viglet.turing.properties.TurConfigProperties;

/**
 * Unit tests for {@link TurMcpToolScopePolicy} — the T246 server-side MCP tool
 * authorization policy.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurMcpToolScopePolicyTest {

    private static final String READ_TOOL = "dsl_search";
    private static final String WRITE_TOOL = "index_document";

    private TurConfigProperties props(boolean requireAuth) {
        TurConfigProperties p = new TurConfigProperties();
        p.getMcpServer().setRequireAuth(requireAuth);
        return p;
    }

    private Authentication authenticated(String... authorities) {
        // TestingAuthenticationToken with authorities is authenticated by default.
        return new TestingAuthenticationToken("user", "n/a", authorities);
    }

    @Test
    void readTool_noAuthRequired_allowsAnonymousLoopbackCaller() {
        TurMcpToolScopePolicy policy = new TurMcpToolScopePolicy(props(false));
        assertTrue(policy.isAllowed(READ_TOOL, null));
    }

    @Test
    void readTool_authRequired_deniesUnauthenticated() {
        TurMcpToolScopePolicy policy = new TurMcpToolScopePolicy(props(true));
        assertFalse(policy.isAllowed(READ_TOOL, null));
    }

    @Test
    void readTool_authRequired_allowsAuthenticated() {
        TurMcpToolScopePolicy policy = new TurMcpToolScopePolicy(props(true));
        assertTrue(policy.isAllowed(READ_TOOL, authenticated("ROLE_USER")));
    }

    @Test
    void writeTool_deniedWithoutWriteScope() {
        TurMcpToolScopePolicy policy = new TurMcpToolScopePolicy(props(true), Set.of(WRITE_TOOL));
        assertTrue(policy.isWriteTool(WRITE_TOOL));
        assertFalse(policy.isAllowed(WRITE_TOOL, authenticated("ROLE_USER")));
    }

    @Test
    void writeTool_allowedWithWriteScope() {
        TurMcpToolScopePolicy policy = new TurMcpToolScopePolicy(props(true), Set.of(WRITE_TOOL));
        assertTrue(policy.isAllowed(WRITE_TOOL, authenticated("ROLE_USER", "SCOPE_mcp:write")));
    }

    @Test
    void readTool_notClassifiedAsWrite() {
        TurMcpToolScopePolicy policy = new TurMcpToolScopePolicy(props(true), Set.of(WRITE_TOOL));
        assertFalse(policy.isWriteTool(READ_TOOL));
    }

    @Test
    void defaultWriteToolSet_classifiesIngestionTools() {
        // The production default set (T253) treats the ingestion tools as writes.
        TurMcpToolScopePolicy policy = new TurMcpToolScopePolicy(props(true));
        assertTrue(policy.isWriteTool("index_document"));
        assertTrue(policy.isWriteTool("deindex_document"));
        assertTrue(policy.isWriteTool("reindex_site"));
        assertFalse(policy.isWriteTool("search_site"));
    }
}

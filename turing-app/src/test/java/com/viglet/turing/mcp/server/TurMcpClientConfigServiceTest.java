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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.viglet.turing.mcp.server.TurMcpClientConfigService.McpClientConfig;

/**
 * Unit tests for {@link TurMcpClientConfigService} — the T255 mcp.json snippet
 * generator.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurMcpClientConfigServiceTest {

    private final TurMcpClientConfigService service = new TurMcpClientConfigService();

    @Test
    void build_returnsSnippetsForEachClient_noAuth() {
        McpClientConfig config = service.build(true, "https://search.acme.com/mcp", false, "turing-es");

        assertTrue(config.enabled());
        assertEquals("https://search.acme.com/mcp", config.endpoint());
        assertFalse(config.requireAuth());
        assertTrue(config.snippets().containsKey("claude-code"));
        assertTrue(config.snippets().containsKey("cursor"));
        assertTrue(config.snippets().containsKey("claude-desktop"));

        String claudeCode = config.snippets().get("claude-code");
        assertTrue(claudeCode.contains("\"mcpServers\""), claudeCode);
        assertTrue(claudeCode.contains("turing-es"), claudeCode);
        assertTrue(claudeCode.contains("https://search.acme.com/mcp"), claudeCode);
        assertTrue(claudeCode.contains("\"type\" : \"http\"") || claudeCode.contains("\"type\":\"http\""),
                claudeCode);
        // No auth → no Authorization header.
        assertFalse(claudeCode.contains("Authorization"), claudeCode);

        String desktop = config.snippets().get("claude-desktop");
        assertTrue(desktop.contains("npx"), desktop);
        assertTrue(desktop.contains("mcp-remote"), desktop);
        assertTrue(desktop.contains("https://search.acme.com/mcp"), desktop);
    }

    @Test
    void build_includesAuthHeaderPlaceholder_whenAuthRequired() {
        McpClientConfig config = service.build(true, "https://search.acme.com/mcp", true, "turing-es");

        assertTrue(config.requireAuth());
        assertTrue(config.snippets().get("claude-code").contains("Authorization"),
                config.snippets().get("claude-code"));
        assertTrue(config.snippets().get("claude-code").contains("Bearer"),
                config.snippets().get("claude-code"));
        assertTrue(config.snippets().get("claude-desktop").contains("Authorization"),
                config.snippets().get("claude-desktop"));
    }

    @Test
    void build_reportsDisabledServer() {
        McpClientConfig config = service.build(false, "http://localhost:2700/mcp", false, "turing-es");
        assertFalse(config.enabled());
        // Snippets are still generated so the admin can pre-stage the client config.
        assertFalse(config.snippets().isEmpty());
    }
}

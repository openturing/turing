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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T255 / §XIII.7 — builds ready-to-paste {@code mcp.json} client configuration
 * snippets so the gap between "we bought Turing" and "my Claude can search it"
 * is a copy-paste, not a docs safari.
 *
 * <p>Produces a snippet per popular client: Claude Code / Cursor (native remote
 * Streamable-HTTP MCP via a {@code url}) and Claude Desktop (which bridges HTTP
 * through {@code npx mcp-remote}). When the endpoint requires auth (T246), the
 * snippet carries an {@code Authorization: Bearer} header placeholder.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurMcpClientConfigService {

    private static final String AUTH_PLACEHOLDER = "Bearer <YOUR_ACCESS_TOKEN>";

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .configure(tools.jackson.databind.SerializationFeature.INDENT_OUTPUT, true)
            .build();

    /**
     * The generated client configuration: the resolved endpoint, the server's
     * posture flags, and a {@code clientName → mcp.json snippet} map.
     */
    public record McpClientConfig(boolean enabled, String endpoint, boolean requireAuth,
            String serverName, Map<String, String> snippets) {
    }

    public McpClientConfig build(boolean enabled, String endpoint, boolean requireAuth, String serverName) {
        Map<String, String> snippets = new LinkedHashMap<>();
        snippets.put("claude-code", remoteUrlSnippet(serverName, endpoint, requireAuth));
        snippets.put("cursor", remoteUrlSnippet(serverName, endpoint, requireAuth));
        snippets.put("claude-desktop", mcpRemoteBridgeSnippet(serverName, endpoint, requireAuth));
        return new McpClientConfig(enabled, endpoint, requireAuth, serverName, snippets);
    }

    /** Native remote MCP (Claude Code, Cursor): a Streamable-HTTP server by URL. */
    private String remoteUrlSnippet(String serverName, String endpoint, boolean requireAuth) {
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("type", "http");
        server.put("url", endpoint);
        if (requireAuth) {
            server.put("headers", Map.of("Authorization", AUTH_PLACEHOLDER));
        }
        return toJson(Map.of("mcpServers", Map.of(serverName, server)));
    }

    /** Claude Desktop bridges remote HTTP MCP through the {@code mcp-remote} npm helper. */
    private String mcpRemoteBridgeSnippet(String serverName, String endpoint, boolean requireAuth) {
        List<String> args = requireAuth
                ? List.of("-y", "mcp-remote", endpoint, "--header", "Authorization:" + AUTH_PLACEHOLDER)
                : List.of("-y", "mcp-remote", endpoint);
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("command", "npx");
        server.put("args", args);
        return toJson(Map.of("mcpServers", Map.of(serverName, server)));
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.warn("[MCP] failed to render client config snippet", e);
            return "{}";
        }
    }
}

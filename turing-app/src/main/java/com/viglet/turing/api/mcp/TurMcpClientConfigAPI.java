/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.mcp.server.TurMcpClientConfigService;
import com.viglet.turing.mcp.server.TurMcpClientConfigService.McpClientConfig;
import com.viglet.turing.properties.TurConfigProperties;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T255 / §XIII.7 — one-click client config (DX). Returns ready-to-paste
 * {@code mcp.json} snippets (Claude Code / Cursor / Claude Desktop) for the
 * Turing MCP server endpoint, so an admin can wire a client in a copy-paste.
 *
 * <p>Admin-only: the path is outside the {@code /mcp} security matcher and the
 * permit-all list, so it falls under the main chain's
 * {@code anyRequest().authenticated()}. Works whether or not the MCP server is
 * currently enabled (it reports {@code enabled} so the UI can prompt to turn it
 * on).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/v2/mcp")
@Tag(name = "MCP Client Config", description = "Generate ready-to-paste mcp.json client snippets")
public class TurMcpClientConfigAPI {

    private final TurMcpClientConfigService clientConfigService;
    private final TurConfigProperties configProperties;

    @Value("${turing.url:http://localhost:2700}")
    private String turingUrl;

    @Value("${spring.ai.mcp.server.enabled:false}")
    private boolean mcpServerEnabled;

    @Value("${spring.ai.mcp.server.name:turing-es}")
    private String mcpServerName;

    public TurMcpClientConfigAPI(TurMcpClientConfigService clientConfigService,
            TurConfigProperties configProperties) {
        this.clientConfigService = clientConfigService;
        this.configProperties = configProperties;
    }

    @Operation(summary = "Ready-to-paste mcp.json client configuration snippets")
    @GetMapping("/client-config")
    public McpClientConfig clientConfig() {
        String endpoint = stripTrailingSlash(turingUrl) + "/mcp";
        boolean requireAuth = configProperties.getMcpServer().isRequireAuth();
        return clientConfigService.build(mcpServerEnabled, endpoint, requireAuth, mcpServerName);
    }

    private String stripTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}

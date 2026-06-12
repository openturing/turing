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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.viglet.turing.testutil.AbstractTuringSpringIT;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Integration test for T253 — the opt-in surface of the gated write/ingestion
 * tools. With {@code turing.mcp-server.write-enabled=true}, the
 * {@code index_document} / {@code deindex_document} / {@code reindex_site} tools
 * are published over MCP (their per-call write-scope gate is unit-tested in
 * {@code TurMcpToolScopePolicyTest}). The default-off behaviour is asserted by
 * {@code TurMcpServerIT}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jmx.enabled=true",
                "spring.ai.mcp.server.enabled=true",
                "turing.mcp-server.write-enabled=true"
        })
class TurMcpWriteToolsIT extends AbstractTuringSpringIT {

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void uniqueJmxObjectNames(DynamicPropertyRegistry registry) {
        registry.add("spring.jmx.unique-names", () -> "true");
    }

    @Test
    void writeEnabled_publishesIngestionTools() {
        HttpClientStreamableHttpTransport transport =
                HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                        .endpoint("/mcp")
                        .build();
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(McpSchema.Implementation.builder("turing-it", "1.0").build())
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        try {
            client.initialize();
            List<String> names = client.listTools().tools().stream()
                    .map(McpSchema.Tool::name).toList();
            assertTrue(names.contains("index_document"),
                    () -> "expected index_document when write-enabled, got " + names);
            assertTrue(names.contains("deindex_document"),
                    () -> "expected deindex_document when write-enabled, got " + names);
            assertTrue(names.contains("reindex_site"),
                    () -> "expected reindex_site when write-enabled, got " + names);
        } finally {
            client.closeGracefully();
        }
    }
}

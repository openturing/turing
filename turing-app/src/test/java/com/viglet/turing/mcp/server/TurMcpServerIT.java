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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Integration test for T245 — Turing as an MCP server.
 *
 * <p>Boots the full backend on a real HTTP port with the MCP server enabled
 * ({@code spring.ai.mcp.server.enabled=true}) and drives the {@code /mcp}
 * Streamable HTTP endpoint with the <em>official MCP client SDK</em> (the same
 * {@code io.modelcontextprotocol} client Turing uses on its consumer side). This
 * exercises the real wire protocol end-to-end: {@code initialize} +
 * {@code tools/list}.
 *
 * <p>Asserts the three T245 deliverables: (1) the transport is up and speaks the
 * protocol; (2) the decorated Turing tool catalog is published — the
 * {@code dsl_*} search tools the internal Semantic-Navigation agent uses show
 * up; (3) decoration ran, so the published descriptions are the curated
 * {@code prompts/tools/dsl/*.md} content, never the {@code "."} placeholder.
 *
 * <p>The test client connects over loopback, so the default
 * {@code turing.mcp-server.loopback-only=true} gate lets it through; the 403
 * rejection path is covered by {@link TurMcpLoopbackFilterTest}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.jmx.enabled=true", "spring.ai.mcp.server.enabled=true"})
class TurMcpServerIT extends AbstractTuringSpringIT {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ToolCallbackProvider turingMcpToolCallbackProvider;

    /** See {@code TurSdkContractIT} — avoid Hikari MBean ObjectName collisions across cached contexts. */
    @DynamicPropertySource
    static void uniqueJmxObjectNames(DynamicPropertyRegistry registry) {
        registry.add("spring.jmx.unique-names", () -> "true");
    }

    @Test
    void toolCallbackProviderBean_publishesDecoratedSearchCatalog() {
        ToolCallback[] callbacks = turingMcpToolCallbackProvider.getToolCallbacks();
        assertNotNull(callbacks);
        Set<String> names = toolNames(List.of(callbacks));
        assertTrue(names.contains("describe_backbone"),
                () -> "expected T188 describe_backbone discovery tool in MCP catalog, got " + names);
        assertTrue(names.contains("list_sites"),
                () -> "expected list_sites in MCP catalog, got " + names);
        assertTrue(names.contains("search_site"),
                () -> "expected search_site in MCP catalog, got " + names);
        assertTrue(names.contains("facet_search"),
                () -> "expected facet_search in MCP catalog, got " + names);
        assertTrue(names.contains("rag_answer"),
                () -> "expected rag_answer in MCP catalog, got " + names);
        assertTrue(names.contains("list_agents"),
                () -> "expected list_agents in MCP catalog, got " + names);
        assertTrue(names.contains("invoke_agent"),
                () -> "expected invoke_agent in MCP catalog, got " + names);
        assertTrue(names.contains("search_metrics"),
                () -> "expected search_metrics in MCP catalog, got " + names);
        assertTrue(names.contains("top_failed_searches"),
                () -> "expected top_failed_searches in MCP catalog, got " + names);
        assertTrue(names.contains("content_gaps"),
                () -> "expected content_gaps in MCP catalog, got " + names);
        // T253 — write tools are gated off by default (turing.mcp-server.write-enabled=false),
        // so they must NOT appear on the read-only surface this IT boots.
        assertFalse(names.contains("index_document"),
                () -> "write tool index_document must be hidden when write-enabled=false, got " + names);
        assertFalse(names.contains("reindex_site"),
                () -> "write tool reindex_site must be hidden when write-enabled=false, got " + names);
    }

    @Test
    void mcpEndpoint_initializesAndListsDecoratedTools() {
        McpSyncClient client = newClient();
        try {
            McpSchema.InitializeResult init = client.initialize();
            assertNotNull(init, "MCP initialize must return a result");
            assertNotNull(init.serverInfo());

            McpSchema.ListToolsResult tools = client.listTools();
            assertNotNull(tools);
            List<McpSchema.Tool> list = tools.tools();
            assertFalse(list.isEmpty(), "MCP server must publish at least one tool");

            McpSchema.Tool listSites = list.stream()
                    .filter(t -> "list_sites".equals(t.name()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(listSites, () -> "list_sites not exposed over MCP; got "
                    + list.stream().map(McpSchema.Tool::name).toList());

            // Decoration ran: the curated prompts/tools/mcp/list_sites.md
            // description replaced the "." @Tool placeholder.
            String description = listSites.description();
            assertNotNull(description);
            assertNotEquals(".", description.trim(),
                    "tool description must be the curated .md content, not the '.' placeholder");
            assertTrue(description.length() > 1,
                    () -> "decorated description unexpectedly short: '" + description + "'");
        } finally {
            client.closeGracefully();
        }
    }

    @Test
    void mcpEndpoint_publishesResourcesAndTemplates() {
        McpSyncClient client = newClient();
        try {
            client.initialize();

            // Concrete resource: the site catalog.
            McpSchema.ListResourcesResult resources = client.listResources();
            assertNotNull(resources);
            assertTrue(resources.resources().stream().anyMatch(r -> "turing://sites".equals(r.uri())),
                    () -> "expected turing://sites resource, got "
                            + resources.resources().stream().map(McpSchema.Resource::uri).toList());

            // Reading it returns non-empty content.
            McpSchema.ReadResourceResult read =
                    client.readResource(new McpSchema.ReadResourceRequest("turing://sites"));
            assertNotNull(read);
            assertFalse(read.contents().isEmpty(), "turing://sites must return content");

            // Templates: schema + document.
            McpSchema.ListResourceTemplatesResult templates = client.listResourceTemplates();
            assertNotNull(templates);
            List<String> uriTemplates = templates.resourceTemplates().stream()
                    .map(McpSchema.ResourceTemplate::uriTemplate).toList();
            assertTrue(uriTemplates.contains("turing://site/{site}/schema"),
                    () -> "expected schema template, got " + uriTemplates);
            assertTrue(uriTemplates.contains("turing://site/{site}/doc/{docId}"),
                    () -> "expected document template, got " + uriTemplates);
        } finally {
            client.closeGracefully();
        }
    }

    @Test
    void mcpEndpoint_exposesPromptsCapability() {
        McpSyncClient client = newClient();
        try {
            client.initialize();
            // Personas in the test DB are not guaranteed, so assert the prompts
            // capability is wired and the call succeeds (non-null list) rather
            // than a specific persona. The persona→prompt mapping is unit-tested
            // in TurMcpPromptConfigTest.
            McpSchema.ListPromptsResult prompts = client.listPrompts();
            assertNotNull(prompts);
            assertNotNull(prompts.prompts());
        } finally {
            client.closeGracefully();
        }
    }

    private McpSyncClient newClient() {
        HttpClientStreamableHttpTransport transport =
                HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                        .endpoint("/mcp")
                        .build();
        return McpClient.sync(transport)
                .clientInfo(McpSchema.Implementation.builder("turing-it", "1.0").build())
                .requestTimeout(Duration.ofSeconds(30))
                .build();
    }

    private Set<String> toolNames(List<ToolCallback> callbacks) {
        return callbacks.stream()
                .map(c -> c.getToolDefinition().name())
                .collect(java.util.stream.Collectors.toSet());
    }
}

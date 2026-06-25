/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;

/**
 * Tests for T425 / §XXI.1 — the {@code mcp} custom-tool binding
 * ({@link TurCustomToolMcpHelper}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurCustomToolMcpHelperTest {

    private final TurMcpToolCallbackService mcpService = mock(TurMcpToolCallbackService.class);
    private final TurAIAgentRepository agentRepository = mock(TurAIAgentRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private TurCustomToolMcpHelper helperFor(String agentId) {
        return new TurCustomToolMcpHelper(mcpService, agentRepository, agentId, objectMapper);
    }

    @Test
    void callsNamedToolAndReturnsItsResult() {
        wireAgentWithMcpTools("agent-1",
                cb("dspace_search_items", "{\"total\":3}"),
                cb("dspace_list_communities", "[]"));

        String out = helperFor("agent-1").call("dspace_search_items", Map.of("query", "Valuation"));

        assertThat(out).isEqualTo("{\"total\":3}");
    }

    @Test
    void serializesArgsToJsonInput() {
        // Capture what input the tool received.
        StringBuilder captured = new StringBuilder();
        ToolCallback capturing = capturingCb("dspace_search_items", captured);
        wireAgentWithMcpTools("agent-1", capturing);

        helperFor("agent-1").call("dspace_search_items", Map.of("query", "ESG", "size", 8));

        assertThat(captured.toString()).contains("\"query\":\"ESG\"").contains("\"size\":8");
    }

    @Test
    void unknownToolReturnsErrorJson() {
        wireAgentWithMcpTools("agent-1", cb("dspace_search_items", "{}"));

        String out = helperFor("agent-1").call("dspace_nope", Map.of());

        assertThat(out).contains("\"error\"").contains("not found");
    }

    @Test
    void noAgentContextReturnsError() {
        String out = helperFor(null).call("dspace_search_items", Map.of());
        assertThat(out).contains("\"error\"").contains("no MCP servers");
    }

    @Test
    void blankToolNameReturnsError() {
        wireAgentWithMcpTools("agent-1", cb("dspace_search_items", "{}"));
        String out = helperFor("agent-1").call("  ", Map.of());
        assertThat(out).contains("\"error\"").contains("non-blank");
    }

    // ── helpers ──

    private void wireAgentWithMcpTools(String agentId, ToolCallback... tools) {
        TurAIAgent agent = new TurAIAgent();
        Set<TurMcpServer> servers = new HashSet<>();
        servers.add(new TurMcpServer());
        agent.setMcpServers(servers);
        when(agentRepository.findById(eq(agentId))).thenReturn(Optional.of(agent));
        lenient().when(mcpService.getToolCallbacks(anySet())).thenReturn(tools);
    }

    private static ToolCallback cb(String name, String result) {
        return makeCb(name, in -> result);
    }

    private static ToolCallback capturingCb(String name, StringBuilder sink) {
        return makeCb(name, in -> {
            sink.append(in);
            return "{}";
        });
    }

    private interface Responder {
        String respond(String input);
    }

    private static ToolCallback makeCb(String name, Responder responder) {
        ToolDefinition def = DefaultToolDefinition.builder()
                .name(name).description(".").inputSchema("{}").build();
        ToolMetadata metadata = DefaultToolMetadata.builder().build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return def;
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return metadata;
            }

            @Override
            public String call(String toolInput) {
                return responder.respond(toolInput);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return responder.respond(toolInput);
            }
        };
    }
}

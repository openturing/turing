/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.anthropic.models.messages.ToolUnion;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;

class TurAnthropicMessagesServiceTest {

    private final TurProviderOptionsParser optionsParser = new TurProviderOptionsParser();
    private final TurAnthropicMessagesService service = new TurAnthropicMessagesService(
            optionsParser, new com.viglet.turing.genai.nativeapi.TurNativeFunctionToolSupport(optionsParser),
            new TurAnthropicCitationSupport());

    private static EnabledCapability cap(TurNativeCapability capability, String configJson) {
        return new EnabledCapability(capability, configJson);
    }

    @Test
    void webSearchMapsToWebSearchTool() {
        List<ToolUnion> tools = service.buildTools(List.of(cap(TurNativeCapability.ANTHROPIC_WEB_SEARCH, null)));
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).isWebSearchTool20250305()).isTrue();
    }

    @Test
    void webSearchNeedsNoBetaHeader() {
        Set<String> betas = new LinkedHashSet<>();
        service.buildTools(List.of(cap(TurNativeCapability.ANTHROPIC_WEB_SEARCH, null)), betas);
        assertThat(betas).isEmpty();
    }

    @Test
    void webFetchMapsToWebFetchToolAndRequiresBetaHeader() {
        Set<String> betas = new LinkedHashSet<>();
        List<ToolUnion> tools = service.buildTools(
                List.of(cap(TurNativeCapability.ANTHROPIC_WEB_FETCH, null)), betas);
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).isWebFetchTool20250910()).isTrue();
        assertThat(betas).containsExactly("web-fetch-2025-09-10");
    }

    @Test
    void codeExecutionMapsToCodeExecutionToolAndRequiresBetaHeader() {
        Set<String> betas = new LinkedHashSet<>();
        List<ToolUnion> tools = service.buildTools(
                List.of(cap(TurNativeCapability.ANTHROPIC_CODE_EXECUTION, null)), betas);
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).isCodeExecutionTool20250522()).isTrue();
        assertThat(betas).containsExactly("code-execution-2025-05-22");
    }

    @Test
    void multipleToolsAccumulateOnlyTheirBetaTokens() {
        Set<String> betas = new LinkedHashSet<>();
        List<ToolUnion> tools = service.buildTools(List.of(
                cap(TurNativeCapability.ANTHROPIC_WEB_SEARCH, null),
                cap(TurNativeCapability.ANTHROPIC_WEB_FETCH, null),
                cap(TurNativeCapability.ANTHROPIC_CODE_EXECUTION, null)), betas);
        assertThat(tools).hasSize(3);
        // web_search is GA (no token); web_fetch + code_execution each contribute one.
        assertThat(betas).containsExactlyInAnyOrder(
                "web-fetch-2025-09-10", "code-execution-2025-05-22");
    }

    @Test
    void openAiCapabilitiesAreSkippedOnAnthropicPath() {
        // Defensive: the executor filters by vendor, but a stray OpenAI key must not produce a tool.
        assertThat(service.buildTools(List.of(cap(TurNativeCapability.OPENAI_WEB_SEARCH, null))))
                .isEmpty();
    }

    @Test
    void buildToolsToleratesNullAndEmpty() {
        assertThat(service.buildTools(null)).isEmpty();
        assertThat(service.buildTools(List.of())).isEmpty();
    }

    @Test
    void buildFunctionToolsMapsCoexistingCallbacksToCustomTools() {
        // T433 — a coexisting Turing/MCP/custom tool becomes an Anthropic custom tool.
        org.springframework.ai.tool.ToolCallback callback =
                org.mockito.Mockito.mock(org.springframework.ai.tool.ToolCallback.class);
        org.springframework.ai.tool.definition.ToolDefinition definition =
                org.mockito.Mockito.mock(org.springframework.ai.tool.definition.ToolDefinition.class);
        org.mockito.Mockito.when(definition.name()).thenReturn("finance_quote");
        org.mockito.Mockito.when(definition.description()).thenReturn("Get a quote");
        org.mockito.Mockito.when(definition.inputSchema()).thenReturn(
                "{\"type\":\"object\",\"properties\":{\"symbol\":{\"type\":\"string\"}},"
                        + "\"required\":[\"symbol\"]}");
        org.mockito.Mockito.when(callback.getToolDefinition()).thenReturn(definition);

        List<ToolUnion> tools = service.buildFunctionTools(
                new org.springframework.ai.tool.ToolCallback[] {callback});

        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).isTool()).isTrue();
        assertThat(tools.get(0).asTool().name()).isEqualTo("finance_quote");
    }

    @Test
    void buildFunctionToolsToleratesNull() {
        assertThat(service.buildFunctionTools(null)).isEmpty();
    }

    // ----- T144 / §X.5.a — Anthropic MCP Connector -----

    @Test
    @SuppressWarnings("unchecked")
    void mcpConnectorBuildsServerAndRequiresBetaHeader() {
        Set<String> betas = new LinkedHashSet<>();
        List<Object> servers = service.buildMcpServers(List.of(cap(TurNativeCapability.ANTHROPIC_MCP,
                "{\"serverUrl\":\"https://mcp.example.com/sse\",\"serverLabel\":\"acme\"}")), betas);

        assertThat(servers).hasSize(1);
        Map<String, Object> server = (Map<String, Object>) servers.get(0);
        assertThat(server).containsEntry("type", "url")
                .containsEntry("url", "https://mcp.example.com/sse")
                .containsEntry("name", "acme")
                .doesNotContainKeys("authorization_token", "tool_configuration");
        assertThat(betas).containsExactly("mcp-client-2025-11-20");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mcpConnectorCarriesAuthTokenAndAllowedTools() {
        Set<String> betas = new LinkedHashSet<>();
        List<Object> servers = service.buildMcpServers(List.of(cap(TurNativeCapability.ANTHROPIC_MCP,
                "{\"serverUrl\":\"https://mcp.example.com/sse\",\"serverName\":\"acme\","
                        + "\"authToken\":\"secret\",\"allowedTools\":\"search,fetch\"}")), betas);

        assertThat(servers).hasSize(1);
        Map<String, Object> server = (Map<String, Object>) servers.get(0);
        assertThat(server).containsEntry("authorization_token", "secret");
        Map<String, Object> toolConfiguration = (Map<String, Object>) server.get("tool_configuration");
        assertThat(toolConfiguration).containsEntry("enabled", true);
        assertThat((List<String>) toolConfiguration.get("allowed_tools"))
                .containsExactly("search", "fetch");
    }

    @Test
    void mcpConnectorWithoutUrlOrNameIsSkippedAndAddsNoBeta() {
        Set<String> betas = new LinkedHashSet<>();
        assertThat(service.buildMcpServers(
                List.of(cap(TurNativeCapability.ANTHROPIC_MCP, "{\"serverLabel\":\"acme\"}")), betas))
                .isEmpty();
        assertThat(betas).isEmpty();
    }

    @Test
    void mcpConnectorIsNotEmittedAsAServerTool() {
        // ANTHROPIC_MCP rides the mcp_servers parameter, not the tools array.
        assertThat(service.buildTools(List.of(cap(TurNativeCapability.ANTHROPIC_MCP,
                "{\"serverUrl\":\"https://mcp.example.com/sse\",\"serverLabel\":\"acme\"}")))).isEmpty();
    }

    @Test
    void buildMcpServersToleratesNullAndEmpty() {
        assertThat(service.buildMcpServers(null, new LinkedHashSet<>())).isEmpty();
        assertThat(service.buildMcpServers(List.of(), new LinkedHashSet<>())).isEmpty();
    }
}

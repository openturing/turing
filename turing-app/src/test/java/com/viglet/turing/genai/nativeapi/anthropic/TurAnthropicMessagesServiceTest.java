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
import com.anthropic.models.messages.CacheControlEphemeral;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.persona.TurPersonaModelCalibration.Params;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

class TurAnthropicMessagesServiceTest {

    private final TurProviderOptionsParser optionsParser = new TurProviderOptionsParser();
    private final TurAnthropicMessagesService service = new TurAnthropicMessagesService(
            optionsParser, new com.viglet.turing.genai.nativeapi.TurNativeFunctionToolSupport(optionsParser),
            new TurAnthropicCitationSupport(), new TurAnthropicContextManagement(optionsParser),
            anthropicCacheFactory());

    private static com.viglet.turing.genai.nativeapi.cache.TurContextCacheProviderFactory anthropicCacheFactory() {
        var noOp = new com.viglet.turing.genai.nativeapi.cache.TurNoOpContextCacheProvider();
        var anthropic = new com.viglet.turing.genai.nativeapi.cache.TurAnthropicContextCacheProvider(
                new TurProviderOptionsParser());
        return new com.viglet.turing.genai.nativeapi.cache.TurContextCacheProviderFactory(
                List.of(noOp, anthropic), noOp);
    }

    private static TurLLMInstance anthropicInstance(String providerOptionsJson) {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("anthropic-1");
        var vendor = new com.viglet.turing.persistence.model.llm.TurLLMVendor();
        vendor.setId("anthropic");
        vendor.setPlugin("anthropic");
        instance.setTurLLMVendor(vendor);
        instance.setProviderOptionsJson(providerOptionsJson);
        return instance;
    }

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

    // ----- T502 context caching (cache_control) -----

    @Test
    void cacheControlAbsentWhenNotOptedIn() {
        assertThat(service.resolveCacheControl(anthropicInstance(null))).isEmpty();
        assertThat(service.resolveCacheControl(anthropicInstance("{\"cacheControlEnabled\":false}")))
                .isEmpty();
    }

    @Test
    void cacheControlPresentWithDefault5mWhenOptedIn() {
        var cc = service.resolveCacheControl(anthropicInstance("{\"cacheControlEnabled\":true}"));
        assertThat(cc).isPresent();
        assertThat(cc.get().ttl()).contains(CacheControlEphemeral.Ttl.TTL_5M);
    }

    @Test
    void cacheControlHonorsOneHourTtl() {
        var cc = service.resolveCacheControl(anthropicInstance(
                "{\"cacheControlEnabled\":true,\"cacheControlTtl\":\"1h\"}"));
        assertThat(cc).isPresent();
        assertThat(cc.get().ttl()).contains(CacheControlEphemeral.Ttl.TTL_1H);
    }

    // ----- T504 extended / interleaved thinking -----

    @Test
    void thinkingDisabledByDefaultAndForNonPositiveBudget() {
        assertThat(service.resolveThinking(anthropicInstance(null)).enabled()).isFalse();
        assertThat(service.resolveThinking(anthropicInstance("{\"thinkingBudget\":0}")).enabled())
                .isFalse();
    }

    @Test
    void thinkingResolvesBudgetAndInterleavedFlag() {
        var t = service.resolveThinking(anthropicInstance(
                "{\"thinkingBudget\":4096,\"interleavedThinking\":true}"));
        assertThat(t.enabled()).isTrue();
        assertThat(t.budgetTokens()).isEqualTo(4096L);
        assertThat(t.interleaved()).isTrue();
    }

    @Test
    void buildParamsEnablesThinkingAndDropsTemperatureWhenBudgetSet() {
        TurLLMInstance instance = anthropicInstance("{\"thinkingBudget\":2048}");
        instance.setTemperature(0.3);
        var params = service.buildParams(instance, List.of(), "hi", List.of());
        assertThat(params.thinking()).isPresent();
        assertThat(params.thinking().get().isEnabled()).isTrue();
        // Anthropic requires temperature = 1 with thinking, so ours is not applied.
        assertThat(params.temperature()).isEmpty();
    }

    @Test
    void buildParamsKeepsTemperatureWhenThinkingOff() {
        TurLLMInstance instance = anthropicInstance(null);
        instance.setTemperature(0.3);
        var params = service.buildParams(instance, List.of(), "hi", List.of());
        assertThat(params.thinking()).isEmpty();
        assertThat(params.temperature()).contains(0.3);
    }

    @Test
    void personaCalibrationOverridesTemperatureAndMaxTokens() {
        TurLLMInstance instance = anthropicInstance(null);
        instance.setTemperature(0.9);
        var params = service.buildParams(instance, List.of(), "hi", List.of(), new Params(0.2, 4096));
        assertThat(params.temperature()).contains(0.2);
        assertThat(params.maxTokens()).isEqualTo(4096L);
    }

    @Test
    void personaCalibrationTemperatureStillSkippedUnderThinking() {
        TurLLMInstance instance = anthropicInstance("{\"thinkingBudget\":2048}");
        var params = service.buildParams(instance, List.of(), "hi", List.of(), new Params(0.2, 4096));
        assertThat(params.thinking()).isPresent();
        // Under thinking Anthropic forces temperature=1, so the calibrated value is skipped…
        assertThat(params.temperature()).isEmpty();
        // …but the maxTokens calibration still applies.
        assertThat(params.maxTokens()).isEqualTo(4096L);
    }
}

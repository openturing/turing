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
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.anthropic.models.messages.ToolUnion;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;

class TurAnthropicMessagesServiceTest {

    private final TurAnthropicMessagesService service =
            new TurAnthropicMessagesService(new TurProviderOptionsParser());

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
}

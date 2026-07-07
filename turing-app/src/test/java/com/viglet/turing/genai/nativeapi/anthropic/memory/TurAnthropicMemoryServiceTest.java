/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.anthropic.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anthropic.models.beta.messages.BetaMemoryTool20250818;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.viglet.turing.genai.nativeapi.anthropic.memory.TurAnthropicMemoryService.Config;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

class TurAnthropicMemoryServiceTest {

    private TurAnthropicMemoryService service;

    @BeforeEach
    void setUp() {
        // The handler isn't exercised by parseConfig/buildParams, so a null-backed
        // handler over an in-memory workspace stand-in is unnecessary here.
        TurProviderOptionsParser parser = new TurProviderOptionsParser();
        service = new TurAnthropicMemoryService(parser, new TurWorkspaceMemoryHandler(null),
                new com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicContextManagement(parser));
    }

    @Test
    void parseConfigFallsBackToDefaults() {
        Config config = service.parseConfig(new TurLLMInstance());
        assertThat(config.model()).isEqualTo(TurAnthropicMemoryService.DEFAULT_MODEL);
        assertThat(config.maxTokens()).isEqualTo(TurAnthropicMemoryService.DEFAULT_MAX_TOKENS);
        assertThat(config.maxIterations()).isEqualTo(TurAnthropicMemoryService.DEFAULT_MAX_ITERATIONS);
    }

    @Test
    void parseConfigUsesInstanceModelAndMaxTokens() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setModelName("claude-opus-4-8");
        instance.setProviderOptionsJson("{\"maxTokens\": 8192}");
        Config config = service.parseConfig(instance);
        assertThat(config.model()).isEqualTo("claude-opus-4-8");
        assertThat(config.maxTokens()).isEqualTo(8192L);
    }

    @Test
    void buildParamsAttachesMemoryToolAndContextManagementBeta() {
        TurLLMInstance instance = new TurLLMInstance();
        Config config = service.parseConfig(instance);
        MessageCreateParams params = service.buildParams(instance, config,
                BetaMemoryTool20250818.builder().build(), "system prompt",
                List.of(BetaMessageParam.builder()
                        .role(BetaMessageParam.Role.USER).content("hi").build()),
                com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicContextManagement.Options.NONE);

        assertThat(params.betas().orElseThrow())
                .contains(com.anthropic.models.beta.AnthropicBeta.CONTEXT_MANAGEMENT_2025_06_27);
        assertThat(params.tools()).isPresent();
        assertThat(params.tools().orElseThrow())
                .anyMatch(t -> t.isMemoryTool20250818());
        // T164 off by default → no context_management config attached.
        assertThat(params.contextManagement()).isEmpty();
    }

    @Test
    void buildParamsAttachesContextEditingWhenEnabled() {
        TurLLMInstance instance = new TurLLMInstance();
        Config config = service.parseConfig(instance);
        MessageCreateParams params = service.buildParams(instance, config,
                BetaMemoryTool20250818.builder().build(), "system prompt",
                List.of(BetaMessageParam.builder()
                        .role(BetaMessageParam.Role.USER).content("hi").build()),
                new com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicContextManagement.Options(
                        true, false));

        assertThat(params.contextManagement()).isPresent();
        assertThat(params.contextManagement().orElseThrow().edits().orElseThrow().get(0)
                .isClearToolUses20250919()).isTrue();
    }
}

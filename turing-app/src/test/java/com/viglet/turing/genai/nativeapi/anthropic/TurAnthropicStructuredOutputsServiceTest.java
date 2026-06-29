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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiStructuredOutputsService;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * F.10 / §X.11.d — verifies that {@link TurAnthropicStructuredOutputsService}
 * forces a single tool whose schema is the desired shape (with cache_control)
 * and falls back (empty) for a non-Anthropic instance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAnthropicStructuredOutputsServiceTest {

    private TurNativeProviderClient providerClient;
    private TurAnthropicStructuredOutputsService service;

    @BeforeEach
    void setUp() {
        providerClient = mock(TurNativeProviderClient.class);
        service = new TurAnthropicStructuredOutputsService(providerClient, new TurProviderOptionsParser());
    }

    private static TurLLMInstance instance() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm1");
        instance.setModelName("claude-sonnet-4-20250514");
        return instance;
    }

    @Test
    void buildParams_forcesSingleSchemaToolWithCacheControl() {
        Map<String, Object> schema = TurOpenAiStructuredOutputsService.strictObjectSchema(
                Map.of("verdict", Map.of("type", "string")));
        MessageCreateParams params = service.buildParams(instance(), "Judge it.", "Is it correct?",
                "verdict", schema);

        assertThat(params.tools()).isPresent();
        assertThat(params.tools().get()).hasSize(1);
        assertThat(params.tools().get().get(0).isTool()).isTrue();
        var tool = params.tools().get().get(0).asTool();
        assertThat(tool.name()).isEqualTo("verdict");
        assertThat(tool.cacheControl()).isPresent();
        // tool_choice pins that exact tool → the model must emit its input
        assertThat(params.toolChoice()).isPresent();
        assertThat(params.toolChoice().get().isTool()).isTrue();
        assertThat(params.toolChoice().get().asTool().name()).isEqualTo("verdict");
    }

    @Test
    void complete_returnsEmptyForNonAnthropicInstance() {
        TurLLMInstance instance = instance();
        when(providerClient.anthropic(instance)).thenReturn(Optional.<AnthropicClient>empty());
        assertThat(service.complete(instance, "sys", "in", "s", Map.of("type", "object"))).isEmpty();
    }
}

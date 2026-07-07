/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * F.10 / §X.11.c — verifies that {@link TurOpenAiStructuredOutputsService}
 * builds a strict {@code json_schema} {@code text.format} and falls back (empty)
 * for a non-OpenAI instance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurOpenAiStructuredOutputsServiceTest {

    private TurNativeProviderClient providerClient;
    private TurOpenAiStructuredOutputsService service;

    @BeforeEach
    void setUp() {
        providerClient = mock(TurNativeProviderClient.class);
        service = new TurOpenAiStructuredOutputsService(providerClient);
    }

    private static TurLLMInstance instance() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm1");
        instance.setModelName("gpt-4o");
        return instance;
    }

    @Test
    void buildParams_carriesStrictJsonSchema() {
        Map<String, Object> schema = TurOpenAiStructuredOutputsService.strictObjectSchema(
                Map.of("answer", Map.of("type", "string")));
        ResponseCreateParams params = service.buildParams(instance(), "Be precise.",
                "Summarize.", "summary", schema);

        assertThat(params.text()).isPresent();
        assertThat(params.text().get().format()).isPresent();
        assertThat(params.text().get().format().get().isJsonSchema()).isTrue();
        ResponseFormatTextJsonSchemaConfig cfg = params.text().get().format().get().asJsonSchema();
        assertThat(cfg.name()).isEqualTo("summary");
        assertThat(cfg.strict()).contains(true);
    }

    @Test
    void strictObjectSchema_setsAdditionalPropertiesFalseAndRequiresAll() {
        Map<String, Object> schema = TurOpenAiStructuredOutputsService.strictObjectSchema(
                Map.of("a", Map.of("type", "string"), "b", Map.of("type", "number")));
        assertThat(schema).containsEntry("type", "object").containsEntry("additionalProperties", false);
        assertThat(schema.get("required")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                .containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void complete_returnsEmptyForNonOpenAiInstance() {
        TurLLMInstance instance = instance();
        when(providerClient.openAi(instance)).thenReturn(Optional.<OpenAIClient>empty());
        assertThat(service.complete(instance, "sys", "in", "s", Map.of("type", "object"))).isEmpty();
    }
}

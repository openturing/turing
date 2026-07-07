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

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * F.10 / §X.11.a — verifies that {@link TurOpenAiPredictedOutputsService}
 * attaches the {@code prediction} parameter for an edit turn, omits it when
 * there is no base text, and falls back (empty) for a non-OpenAI instance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurOpenAiPredictedOutputsServiceTest {

    private TurNativeProviderClient providerClient;
    private TurOpenAiPredictedOutputsService service;

    @BeforeEach
    void setUp() {
        providerClient = mock(TurNativeProviderClient.class);
        service = new TurOpenAiPredictedOutputsService(providerClient);
    }

    private static TurLLMInstance instance() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm1");
        instance.setModelName("gpt-4o");
        instance.setTemperature(0.2);
        return instance;
    }

    @Test
    void buildParams_attachesPredictionAndMessages() {
        ChatCompletionCreateParams params = service.buildParams(instance(),
                "You rewrite text.", "Make it shorter.", "The original long answer text.");

        assertThat(params.prediction()).isPresent();
        assertThat(params.prediction().get().content().asText())
                .isEqualTo("The original long answer text.");
        // system + user
        assertThat(params.messages()).hasSize(2);
        assertThat(params.temperature()).contains(0.2);
        assertThat(params.model().asString()).isEqualTo("gpt-4o");
    }

    @Test
    void buildParams_omitsPredictionWhenNoBaseText() {
        ChatCompletionCreateParams params = service.buildParams(instance(),
                null, "Write something fresh.", "   ");

        assertThat(params.prediction()).isEmpty();
        // only the user message (no system prompt)
        assertThat(params.messages()).hasSize(1);
    }

    @Test
    void edit_returnsEmptyForNonOpenAiInstance() {
        TurLLMInstance instance = instance();
        when(providerClient.openAi(instance)).thenReturn(Optional.<OpenAIClient>empty());

        assertThat(service.edit(instance, "sys", "do it", "base")).isEmpty();
    }
}

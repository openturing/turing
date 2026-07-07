/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

@ExtendWith(MockitoExtension.class)
class TurRealtimeVoiceProviderFactoryTest {

    @Mock private TurGenAiLlmProviderFactory llmProviderFactory;
    @Mock private TurGenAiLlmProvider llmProvider;

    private final TurRealtimeVoiceProvider openAiVoice = new TurRealtimeVoiceProvider() {
        @Override public String getPluginType() {
            return "openai";
        }
        @Override public boolean supportsModel(String model) {
            return true;
        }
        @Override public String defaultModel() {
            return "gpt-realtime";
        }
        @Override public TurRealtimeVoiceSession createSession(TurLLMInstance instance,
                TurRealtimeVoiceSessionRequest request) {
            return null;
        }
    };

    @Test
    void resolvesOpenAiVoiceProviderForOpenAiInstance() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        when(llmProviderFactory.getProvider(instance)).thenReturn(llmProvider);
        when(llmProvider.getPluginType()).thenReturn("openai");

        TurRealtimeVoiceProviderFactory factory =
                new TurRealtimeVoiceProviderFactory(List.of(openAiVoice), llmProviderFactory);

        assertThat(factory.getProvider(instance)).containsSame(openAiVoice);
        assertThat(factory.isVoiceSupported(instance)).isTrue();
    }

    @Test
    void returnsEmptyForVendorWithoutVoiceProvider() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-2");
        when(llmProviderFactory.getProvider(instance)).thenReturn(llmProvider);
        when(llmProvider.getPluginType()).thenReturn("anthropic");

        TurRealtimeVoiceProviderFactory factory =
                new TurRealtimeVoiceProviderFactory(List.of(openAiVoice), llmProviderFactory);

        assertThat(factory.getProvider(instance)).isEmpty();
        assertThat(factory.isVoiceSupported(instance)).isFalse();
    }

    @Test
    void returnsEmptyForNullInstance() {
        lenient().when(llmProvider.getPluginType()).thenReturn("openai");
        TurRealtimeVoiceProviderFactory factory =
                new TurRealtimeVoiceProviderFactory(List.of(openAiVoice), llmProviderFactory);
        assertThat(factory.getProvider(null)).isEmpty();
    }
}

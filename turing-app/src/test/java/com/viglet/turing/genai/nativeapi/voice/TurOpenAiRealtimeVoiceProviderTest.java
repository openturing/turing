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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient.NativeCredentials;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

@ExtendWith(MockitoExtension.class)
class TurOpenAiRealtimeVoiceProviderTest {

    @Mock private TurNativeProviderClient nativeClient;

    private TurOpenAiRealtimeVoiceProvider provider() {
        return new TurOpenAiRealtimeVoiceProvider(nativeClient);
    }

    @Test
    void pluginTypeAndDefaultsAreOpenAi() {
        TurOpenAiRealtimeVoiceProvider provider = provider();
        assertThat(provider.getPluginType()).isEqualTo("openai");
        assertThat(provider.defaultModel()).isEqualTo("gpt-realtime");
    }

    @Test
    void supportsTheRealtimeModelFamily() {
        TurOpenAiRealtimeVoiceProvider provider = provider();
        assertThat(provider.supportsModel("gpt-realtime")).isTrue();
        assertThat(provider.supportsModel("gpt-realtime-mini")).isTrue();
        assertThat(provider.supportsModel("gpt-realtime-1.5")).isTrue();
        assertThat(provider.supportsModel("GPT-Realtime")).isTrue();
        assertThat(provider.supportsModel("gpt-4o-realtime-preview")).isTrue();
        assertThat(provider.supportsModel("gpt-4o")).isFalse();
        assertThat(provider.supportsModel("")).isFalse();
        assertThat(provider.supportsModel(null)).isFalse();
    }

    @Test
    void clientSecretsEndpointAppendsToBaseUrl() {
        assertThat(TurOpenAiRealtimeVoiceProvider.clientSecretsEndpoint("https://api.openai.com/v1"))
                .isEqualTo("https://api.openai.com/v1/realtime/client_secrets");
        // trailing slash tolerated
        assertThat(TurOpenAiRealtimeVoiceProvider.clientSecretsEndpoint("https://api.openai.com/v1/"))
                .isEqualTo("https://api.openai.com/v1/realtime/client_secrets");
    }

    @Test
    void webSocketUrlSwitchesHttpsToWssAndCarriesModel() {
        assertThat(TurOpenAiRealtimeVoiceProvider.webSocketUrl("https://api.openai.com/v1", "gpt-realtime"))
                .isEqualTo("wss://api.openai.com/v1/realtime?model=gpt-realtime");
        assertThat(TurOpenAiRealtimeVoiceProvider.webSocketUrl("http://localhost:8080/v1", "gpt-realtime-mini"))
                .isEqualTo("ws://localhost:8080/v1/realtime?model=gpt-realtime-mini");
    }

    @Test
    void parsesGaTopLevelShape() {
        TurOpenAiRealtimeVoiceProvider provider = provider();
        String json = """
                {"value":"ek_abc123","expires_at":1750000000,"session":{"model":"gpt-realtime"}}
                """;
        TurRealtimeVoiceSession session = provider.parseSession(json, "gpt-realtime", "marin",
                "https://api.openai.com/v1");
        assertThat(session.clientSecret()).isEqualTo("ek_abc123");
        assertThat(session.expiresAt()).isEqualTo(1750000000L);
        assertThat(session.model()).isEqualTo("gpt-realtime");
        assertThat(session.voice()).isEqualTo("marin");
        assertThat(session.provider()).isEqualTo("openai");
        assertThat(session.wsUrl()).isEqualTo("wss://api.openai.com/v1/realtime?model=gpt-realtime");
    }

    @Test
    void parsesNestedClientSecretEnvelope() {
        TurOpenAiRealtimeVoiceProvider provider = provider();
        String json = """
                {"client_secret":{"value":"ek_nested","expires_at":1750000123}}
                """;
        TurRealtimeVoiceSession session = provider.parseSession(json, "gpt-realtime-mini", "cedar",
                "https://api.openai.com/v1");
        assertThat(session.clientSecret()).isEqualTo("ek_nested");
        assertThat(session.expiresAt()).isEqualTo(1750000123L);
    }

    @Test
    void parseThrowsWhenSecretMissing() {
        TurOpenAiRealtimeVoiceProvider provider = provider();
        assertThatThrownBy(() -> provider.parseSession("{\"session\":{}}", "gpt-realtime", "marin",
                "https://api.openai.com/v1"))
                .isInstanceOf(TurRealtimeVoiceException.class)
                .hasMessageContaining("client secret");
    }

    @Test
    void createSessionThrowsWhenInstanceHasNoApiKey() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        when(nativeClient.credentials(instance))
                .thenReturn(new NativeCredentials("", "https://api.openai.com/v1"));
        assertThatThrownBy(() -> provider().createSession(instance,
                new TurRealtimeVoiceSessionRequest(null, null, null, null)))
                .isInstanceOf(TurRealtimeVoiceException.class)
                .hasMessageContaining("no API key");
    }

    @Test
    void createSessionThrowsOnUnsupportedModel() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        when(nativeClient.credentials(instance))
                .thenReturn(new NativeCredentials("sk-key", "https://api.openai.com/v1"));
        assertThatThrownBy(() -> provider().createSession(instance,
                new TurRealtimeVoiceSessionRequest("gpt-4o", null, null, null)))
                .isInstanceOf(TurRealtimeVoiceException.class)
                .hasMessageContaining("Unsupported realtime model");
    }
}

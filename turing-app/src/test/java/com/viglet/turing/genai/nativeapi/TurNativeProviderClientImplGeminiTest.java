/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.genai.Client;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * T489 / §X.19 — the {@code gemini()} accessor of the native provider client.
 */
@ExtendWith(MockitoExtension.class)
class TurNativeProviderClientImplGeminiTest {

    @Mock private TurGenAiLlmProviderFactory providerFactory;
    @Mock private TurSecretCryptoService secretCryptoService;
    @Mock private TurNativeCapabilityService capabilityService;
    @Mock private TurGenAiLlmProvider provider;

    private TurNativeProviderClientImpl client() {
        return new TurNativeProviderClientImpl(providerFactory, secretCryptoService,
                new TurProviderOptionsParser(), capabilityService);
    }

    private TurLLMInstance instance(String pluginType) {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn(pluginType);
        return instance;
    }

    @Test
    void emptyForNonGeminiVendor() {
        TurLLMInstance instance = instance("openai");
        assertThat(client().gemini(instance)).isEmpty();
    }

    @Test
    void emptyWhenGeminiHasNoApiKey() {
        TurLLMInstance instance = instance("gemini");
        // No encrypted key set → no native client.
        assertThat(client().gemini(instance)).isEmpty();
    }

    @Test
    void buildsClientForGeminiWithApiKey() {
        TurLLMInstance instance = instance("gemini");
        instance.setApiKeyEncrypted("enc");
        lenient().when(secretCryptoService.decrypt("enc")).thenReturn("plain-key");

        Optional<Client> result = client().gemini(instance);

        assertThat(result).isPresent();
    }
}

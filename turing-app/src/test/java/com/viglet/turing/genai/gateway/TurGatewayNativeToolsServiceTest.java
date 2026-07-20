/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicMessagesService;
import com.viglet.turing.genai.nativeapi.gemini.TurGoogleGenAiNativeService;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiResponsesService;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * T747 / §XLIX — unit coverage for the {@code x-turing-tools} header normalisation
 * and the no-op guard. The vendor-dispatch path is exercised by the gateway
 * integration test (needs live provider SDKs).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurGatewayNativeToolsServiceTest {

    @Mock
    private TurGenAiLlmProviderFactory providerFactory;
    @Mock
    private TurNativeProviderClient nativeClient;
    @Mock
    private TurNativeCapabilityService capabilityService;
    @Mock
    private TurOpenAiResponsesService openAiResponses;
    @Mock
    private TurAnthropicMessagesService anthropicMessages;
    @Mock
    private TurGoogleGenAiNativeService geminiNative;

    private TurGatewayNativeToolsService service() {
        return new TurGatewayNativeToolsService(providerFactory, nativeClient, capabilityService,
                openAiResponses, anthropicMessages, geminiNative);
    }

    @Test
    void normalisesCommonToolSpellings() {
        assertThat(TurGatewayNativeToolsService.normaliseFunction("web_search")).isEqualTo("web-search");
        assertThat(TurGatewayNativeToolsService.normaliseFunction("WEB-SEARCH")).isEqualTo("web-search");
        assertThat(TurGatewayNativeToolsService.normaliseFunction("code_execution")).isEqualTo("code-exec");
        assertThat(TurGatewayNativeToolsService.normaliseFunction("code-interpreter")).isEqualTo("code-exec");
        assertThat(TurGatewayNativeToolsService.normaliseFunction("url_context")).isEqualTo("web-fetch");
        assertThat(TurGatewayNativeToolsService.normaliseFunction("  ")).isNull();
        assertThat(TurGatewayNativeToolsService.normaliseFunction(null)).isNull();
    }

    @Test
    void supportsFalseWhenNoFunctionsRequested() {
        assertThat(service().supports(new TurLLMInstance(), Set.of())).isFalse();
        assertThat(service().supports(new TurLLMInstance(), null)).isFalse();
    }
}

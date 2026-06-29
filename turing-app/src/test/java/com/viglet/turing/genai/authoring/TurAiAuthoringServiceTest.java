/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.ParameterizedTypeReference;

import com.viglet.turing.genai.TurToolExecutionLoop;
import com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicStructuredOutputsService;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiStructuredOutputsService;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * F.10 / §X.11.c — T426. Verifies the AI Authoring chat prefers the
 * provider-bound structured-output path for no-tool turns and falls back to the
 * legacy {@code BeanOutputConverter} path for tooled turns / non-structured
 * providers / unparseable structured replies.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAiAuthoringServiceTest {

    record Shape(String name) {
    }

    private static final ParameterizedTypeReference<AiAuthoringResponse<Shape>> TYPE_REF =
            new ParameterizedTypeReference<>() {
            };
    private static final String SYS = "You author a Shape.";

    private TurGlobalSettingsService globalSettings;
    private TurLLMInstanceRepository llmRepository;
    private TurLlmModelFactory llmModelFactory;
    private TurSecretCryptoService secretCryptoService;
    private TurToolExecutionLoop toolExecutionLoop;
    private TurOpenAiStructuredOutputsService openAiStructured;
    private TurAnthropicStructuredOutputsService anthropicStructured;
    private TurAiAuthoringService service;

    @BeforeEach
    void setUp() {
        globalSettings = mock(TurGlobalSettingsService.class);
        llmRepository = mock(TurLLMInstanceRepository.class);
        llmModelFactory = mock(TurLlmModelFactory.class);
        secretCryptoService = mock(TurSecretCryptoService.class);
        toolExecutionLoop = mock(TurToolExecutionLoop.class);
        openAiStructured = mock(TurOpenAiStructuredOutputsService.class);
        anthropicStructured = mock(TurAnthropicStructuredOutputsService.class);
        service = new TurAiAuthoringService(globalSettings, llmRepository, llmModelFactory,
                secretCryptoService, toolExecutionLoop, openAiStructured, anthropicStructured);

        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm1");
        when(globalSettings.getDefaultLlmId()).thenReturn("llm1");
        when(llmRepository.findById("llm1")).thenReturn(Optional.of(instance));
    }

    private AiAuthoringRequest<Shape> request() {
        return new AiAuthoringRequest<>(List.of(new AiAuthoringMessage("user", "Call it Acme")), null);
    }

    /** Make the legacy tool-execution path return the given JSON text. */
    private void stubLegacy(String json) {
        when(secretCryptoService.decrypt(any())).thenReturn("key");
        when(llmModelFactory.createChatModel(any(), any())).thenReturn(mock(ChatModel.class));
        ChatResponse aiResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(aiResponse.getResult().getOutput().getText()).thenReturn(json);
        when(toolExecutionLoop.call(any(), any())).thenReturn(aiResponse);
    }

    @Test
    void noToolTurn_usesStructuredPath() {
        when(openAiStructured.complete(any(), anyString(), anyString(), eq("authoring_response"),
                any(), eq(false)))
                .thenReturn(Optional.of("{\"message\":\"Done\",\"state\":{\"name\":\"Acme\"}}"));

        AiAuthoringResponse<Shape> response = service.chat(request(), SYS, TYPE_REF);

        assertThat(response.message()).isEqualTo("Done");
        assertThat(response.state().name()).isEqualTo("Acme");
        // legacy path never touched
        verify(toolExecutionLoop, never()).call(any(), any());
    }

    @Test
    void noToolTurn_fallsBackToLegacy_whenProviderNotStructured() {
        when(openAiStructured.complete(any(), anyString(), anyString(), any(), any(), eq(false)))
                .thenReturn(Optional.empty());
        when(anthropicStructured.complete(any(), anyString(), anyString(), any(), any()))
                .thenReturn(Optional.empty());
        stubLegacy("{\"message\":\"Legacy\",\"state\":{\"name\":\"L\"}}");

        AiAuthoringResponse<Shape> response = service.chat(request(), SYS, TYPE_REF);

        assertThat(response.message()).isEqualTo("Legacy");
        assertThat(response.state().name()).isEqualTo("L");
    }

    @Test
    void tooledTurn_skipsStructuredAndUsesLegacy() {
        stubLegacy("{\"message\":\"Tooled\",\"state\":{\"name\":\"T\"}}");
        ToolCallback[] tools = { mock(ToolCallback.class) };

        AiAuthoringResponse<Shape> response = service.chat(request(), SYS, TYPE_REF, tools);

        assertThat(response.message()).isEqualTo("Tooled");
        verify(openAiStructured, never()).complete(any(), anyString(), anyString(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean());
        verify(anthropicStructured, never()).complete(any(), anyString(), anyString(), any(), any());
    }
}

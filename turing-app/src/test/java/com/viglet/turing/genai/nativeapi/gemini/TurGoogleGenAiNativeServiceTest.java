/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * T489 / §X.19 — unit coverage for the pure prompt/option mapping of the Gemini
 * native branch. The {@code generateContentStream} call itself needs a live SDK
 * client and is exercised by the real-LLM-gated integration path, not here.
 */
class TurGoogleGenAiNativeServiceTest {

    private final TurGoogleGenAiNativeService service =
            new TurGoogleGenAiNativeService(new TurProviderOptionsParser());

    @Test
    void mapsHistoryToContentsWithGeminiRoles() {
        List<Content> contents = TurGoogleGenAiNativeService.toContents(List.of(
                new ChatMessageItem("user", "hi"),
                new ChatMessageItem("assistant", "hello"),
                new ChatMessageItem("user", "bye")));

        assertThat(contents).hasSize(3);
        assertThat(contents.get(0).role()).contains("user");
        // Gemini's assistant role is "model".
        assertThat(contents.get(1).role()).contains("model");
        assertThat(contents.get(2).role()).contains("user");
        assertThat(contents.get(0).text()).isEqualTo("hi");
        assertThat(contents.get(1).text()).isEqualTo("hello");
    }

    @Test
    void skipsBlankAndNullHistoryTurns() {
        List<Content> contents = TurGoogleGenAiNativeService.toContents(List.of(
                new ChatMessageItem("user", "  "),
                new ChatMessageItem("user", "real")));

        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).text()).isEqualTo("real");
    }

    @Test
    void toContentsHandlesNullHistory() {
        assertThat(TurGoogleGenAiNativeService.toContents(null)).isEmpty();
    }

    @Test
    void resolveModelNameFallsBackToDefault() {
        TurLLMInstance instance = new TurLLMInstance();
        assertThat(service.resolveModelName(instance))
                .isEqualTo(TurGoogleGenAiNativeService.DEFAULT_CHAT_MODEL);
    }

    @Test
    void resolveModelNamePrefersInstanceModelOverDefault() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setModelName("gemini-2.5-pro");
        assertThat(service.resolveModelName(instance)).isEqualTo("gemini-2.5-pro");
    }

    @Test
    void resolveModelNamePrefersProviderOptionOverInstanceModel() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setModelName("gemini-2.5-pro");
        instance.setProviderOptionsJson("{\"chatModel\":\"gemini-2.5-flash\"}");
        assertThat(service.resolveModelName(instance)).isEqualTo("gemini-2.5-flash");
    }

    @Test
    void buildConfigSetsSystemInstructionWhenPromptPresent() {
        TurLLMInstance instance = new TurLLMInstance();
        GenerateContentConfig config = service.buildConfig(instance, "You are helpful.", List.of());

        assertThat(config.systemInstruction()).isPresent();
        assertThat(config.systemInstruction().get().text()).isEqualTo("You are helpful.");
    }

    @Test
    void buildConfigOmitsSystemInstructionWhenPromptBlank() {
        TurLLMInstance instance = new TurLLMInstance();
        GenerateContentConfig config = service.buildConfig(instance, "  ", List.of());

        assertThat(config.systemInstruction()).isEmpty();
    }

    @Test
    void buildConfigCarriesSamplingOptions() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setTemperature(0.4);
        instance.setProviderOptionsJson("{\"topP\":0.8,\"maxTokens\":256}");
        GenerateContentConfig config = service.buildConfig(instance, "sys", List.of());

        assertThat(config.temperature()).contains(0.4f);
        assertThat(config.topP()).contains(0.8f);
        assertThat(config.maxOutputTokens()).contains(256);
    }

    @Test
    void buildConfigAddsGoogleSearchToolWhenCapabilityEnabled() {
        TurLLMInstance instance = new TurLLMInstance();
        GenerateContentConfig config = service.buildConfig(instance, "sys",
                List.of(new EnabledCapability(TurNativeCapability.GEMINI_GOOGLE_SEARCH, null)));

        assertThat(config.tools()).isPresent();
        assertThat(config.tools().get()).hasSize(1);
        assertThat(config.tools().get().get(0).googleSearch()).isPresent();
    }

    @Test
    void buildConfigAddsNoToolsWhenNoToolCapability() {
        TurLLMInstance instance = new TurLLMInstance();
        GenerateContentConfig config = service.buildConfig(instance, "sys", List.of());

        // No tools configured → the Optional is empty (unchanged base path).
        assertThat(config.tools()).isEmpty();
    }

    @Test
    void buildConfigAddsUrlContextToolWhenCapabilityEnabled() {
        TurLLMInstance instance = new TurLLMInstance();
        GenerateContentConfig config = service.buildConfig(instance, "sys",
                List.of(new EnabledCapability(TurNativeCapability.GEMINI_URL_CONTEXT, null)));

        assertThat(config.tools()).isPresent();
        assertThat(config.tools().get()).hasSize(1);
        assertThat(config.tools().get().get(0).urlContext()).isPresent();
    }

    @Test
    void buildConfigAddsCodeExecutionToolWhenCapabilityEnabled() {
        TurLLMInstance instance = new TurLLMInstance();
        GenerateContentConfig config = service.buildConfig(instance, "sys",
                List.of(new EnabledCapability(TurNativeCapability.GEMINI_CODE_EXECUTION, null)));

        assertThat(config.tools()).isPresent();
        assertThat(config.tools().get()).hasSize(1);
        assertThat(config.tools().get().get(0).codeExecution()).isPresent();
    }

    @Test
    void buildConfigCombinesGoogleSearchAndUrlContext() {
        TurLLMInstance instance = new TurLLMInstance();
        GenerateContentConfig config = service.buildConfig(instance, "sys", List.of(
                new EnabledCapability(TurNativeCapability.GEMINI_GOOGLE_SEARCH, null),
                new EnabledCapability(TurNativeCapability.GEMINI_URL_CONTEXT, null)));

        assertThat(config.tools()).isPresent();
        assertThat(config.tools().get()).hasSize(2);
        assertThat(config.tools().get().get(0).googleSearch()).isPresent();
        assertThat(config.tools().get().get(1).urlContext()).isPresent();
    }
}

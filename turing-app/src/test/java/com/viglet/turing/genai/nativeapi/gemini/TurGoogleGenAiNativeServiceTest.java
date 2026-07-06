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
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.cache.TurContextCacheProviderFactory;
import com.viglet.turing.genai.nativeapi.cache.TurContextCacheRef;
import com.viglet.turing.genai.nativeapi.cache.TurNoOpContextCacheProvider;
import com.viglet.turing.genai.persona.TurPersonaModelCalibration.Params;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * T489 / §X.19 — unit coverage for the pure prompt/option mapping of the Gemini
 * native branch. The {@code generateContentStream} call itself needs a live SDK
 * client and is exercised by the real-LLM-gated integration path, not here.
 */
class TurGoogleGenAiNativeServiceTest {

    /** A factory wired only with the no-op provider — every instance resolves uncached. */
    private static final TurContextCacheProviderFactory NO_OP_CACHE = noOpCacheFactory();

    private final TurGoogleGenAiNativeService service =
            new TurGoogleGenAiNativeService(new TurProviderOptionsParser(), NO_OP_CACHE);

    private static TurContextCacheProviderFactory noOpCacheFactory() {
        TurNoOpContextCacheProvider noOp = new TurNoOpContextCacheProvider();
        return new TurContextCacheProviderFactory(List.of(noOp), noOp);
    }

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
    void personaCalibrationOverridesTemperatureAndMaxTokens() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setTemperature(0.4);
        instance.setProviderOptionsJson("{\"maxTokens\":256}");
        // T606 — opted-in persona calibration wins over the instance + providerOptions.
        GenerateContentConfig config = service.buildConfig(instance, "sys", List.of(),
                Optional.empty(), new Params(0.2, 4096));

        assertThat(config.temperature()).contains(0.2f);
        assertThat(config.maxOutputTokens()).contains(4096);
    }

    @Test
    void noneCalibrationKeepsInstanceSampling() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setTemperature(0.4);
        instance.setProviderOptionsJson("{\"maxTokens\":256}");
        GenerateContentConfig config = service.buildConfig(instance, "sys", List.of(),
                Optional.empty(), Params.NONE);

        assertThat(config.temperature()).contains(0.4f);
        assertThat(config.maxOutputTokens()).contains(256);
    }

    @Test
    void buildConfigAddsThinkingConfigWhenBudgetOrThoughtsSet() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setProviderOptionsJson("{\"thinkingBudget\":1024,\"includeThoughts\":true}");
        GenerateContentConfig config = service.buildConfig(instance, "sys", List.of());

        assertThat(config.thinkingConfig()).isPresent();
        assertThat(config.thinkingConfig().get().thinkingBudget()).contains(1024);
        assertThat(config.thinkingConfig().get().includeThoughts()).contains(true);
    }

    @Test
    void buildConfigOmitsThinkingConfigByDefault() {
        TurLLMInstance instance = new TurLLMInstance();
        GenerateContentConfig config = service.buildConfig(instance, "sys", List.of());
        assertThat(config.thinkingConfig()).isEmpty();
    }

    @Test
    void thinkingSurfacedReflectsIncludeThoughtsOption() {
        TurLLMInstance off = new TurLLMInstance();
        assertThat(service.isThinkingSurfaced(off)).isFalse();

        TurLLMInstance budgetOnly = new TurLLMInstance();
        budgetOnly.setProviderOptionsJson("{\"thinkingBudget\":512}");
        // Budget caps reasoning but does not surface the summary.
        assertThat(service.isThinkingSurfaced(budgetOnly)).isFalse();

        TurLLMInstance on = new TurLLMInstance();
        on.setProviderOptionsJson("{\"includeThoughts\":true}");
        assertThat(service.isThinkingSurfaced(on)).isTrue();
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
    void buildConfigRequestsImageModalityWhenImageGenerationEnabled() {
        TurLLMInstance instance = new TurLLMInstance();
        GenerateContentConfig config = service.buildConfig(instance, "sys",
                List.of(new EnabledCapability(TurNativeCapability.GEMINI_IMAGE_GENERATION, null)));

        assertThat(config.responseModalities()).isPresent();
        assertThat(config.responseModalities().get()).containsExactly("TEXT", "IMAGE");
        // Image generation is a modality, not a tool entry.
        assertThat(config.tools()).isEmpty();
    }

    @Test
    void buildConfigOmitsImageModalityByDefault() {
        TurLLMInstance instance = new TurLLMInstance();
        GenerateContentConfig config = service.buildConfig(instance, "sys", List.of());
        assertThat(config.responseModalities()).isEmpty();
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

    // ----- T499 context caching -----

    @Test
    void buildConfigUsesCachedContentAndOmitsSystemInstructionWhenCacheRefPresent() {
        TurLLMInstance instance = new TurLLMInstance();
        TurContextCacheRef ref = new TurContextCacheRef(
                "gemini", "cachedContents/abc123", 4096, null, false);
        GenerateContentConfig config = service.buildConfig(instance, "You are helpful.",
                List.of(), Optional.of(ref));

        assertThat(config.cachedContent()).contains("cachedContents/abc123");
        // The prefix lives in the cache, so it must NOT also be on the request.
        assertThat(config.systemInstruction()).isEmpty();
    }

    @Test
    void buildConfigBypassesToolsWhenCacheRefPresent() {
        TurLLMInstance instance = new TurLLMInstance();
        TurContextCacheRef ref = new TurContextCacheRef(
                "gemini", "cachedContents/abc123", null, null, true);
        GenerateContentConfig config = service.buildConfig(instance, "sys",
                List.of(new EnabledCapability(TurNativeCapability.GEMINI_GOOGLE_SEARCH, null)),
                Optional.of(ref));

        // Server-side tools belong to the cache; they can't be re-declared.
        assertThat(config.cachedContent()).contains("cachedContents/abc123");
        assertThat(config.tools()).isEmpty();
    }

    @Test
    void resolveContextCacheReturnsEmptyWhenNotEnabled() {
        TurLLMInstance instance = new TurLLMInstance();
        assertThat(service.resolveContextCache(instance, "gemini-2.0-flash", "sys")).isEmpty();
    }

    @Test
    void resolveContextCacheReturnsEmptyWhenPromptBlankEvenIfEnabled() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setProviderOptionsJson("{\"contextCacheEnabled\":true}");
        assertThat(service.resolveContextCache(instance, "gemini-2.0-flash", "  ")).isEmpty();
    }

    @Test
    void resolveContextCacheStaysEmptyForNoOpProviderEvenWhenEnabled() {
        // The no-op factory resolves every instance to "no cache" — the OpenAI /
        // unconfigured-vendor guarantee. So even an opted-in instance gets empty.
        TurLLMInstance instance = new TurLLMInstance();
        instance.setProviderOptionsJson(
                "{\"contextCacheEnabled\":true,\"contextCacheTtlSeconds\":600}");
        assertThat(service.resolveContextCache(instance, "gemini-2.0-flash", "big stable prompt"))
                .isEmpty();
    }
}

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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.Tool;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.openai.TurStoredCompletionsService.StoredCompletionsDirective;
import com.viglet.turing.genai.persona.TurPersonaModelCalibration.Params;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

class TurOpenAiResponsesServiceTest {

    private final TurProviderOptionsParser optionsParser = new TurProviderOptionsParser();
    private final TurOpenAiResponsesService service = new TurOpenAiResponsesService(
            optionsParser, new com.viglet.turing.genai.nativeapi.TurNativeFunctionToolSupport(optionsParser),
            org.mockito.Mockito.mock(com.viglet.turing.genai.workspace.TurAgentWorkspace.class));

    private static EnabledCapability cap(TurNativeCapability capability, String configJson) {
        return new EnabledCapability(capability, configJson);
    }

    @Test
    void webSearchMapsToWebSearchTool() {
        List<Tool> tools = service.buildTools(List.of(cap(TurNativeCapability.OPENAI_WEB_SEARCH, null)));
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).isWebSearch()).isTrue();
    }

    @Test
    void fileSearchRequiresVectorStoreIds() {
        // No ids -> tool skipped
        assertThat(service.buildTools(List.of(cap(TurNativeCapability.OPENAI_FILE_SEARCH, "{}"))))
                .isEmpty();

        // With ids -> file_search tool produced
        List<Tool> tools = service.buildTools(List.of(cap(TurNativeCapability.OPENAI_FILE_SEARCH,
                "{\"vectorStoreIds\":[\"vs_123\",\"vs_456\"]}")));
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).isFileSearch()).isTrue();
    }

    @Test
    void codeInterpreterAndImageGenerationMapDirectly() {
        List<Tool> tools = service.buildTools(List.of(
                cap(TurNativeCapability.OPENAI_CODE_INTERPRETER, null),
                cap(TurNativeCapability.OPENAI_IMAGE_GENERATION, null)));
        assertThat(tools)
                .hasSize(2)
                .anyMatch(Tool::isCodeInterpreter)
                .anyMatch(Tool::isImageGeneration);
    }

    @Test
    void mcpRequiresServerLabelAndUrl() {
        assertThat(service.buildTools(List.of(cap(TurNativeCapability.OPENAI_MCP,
                "{\"serverLabel\":\"only-label\"}")))).isEmpty();

        List<Tool> tools = service.buildTools(List.of(cap(TurNativeCapability.OPENAI_MCP,
                "{\"serverLabel\":\"turing\",\"serverUrl\":\"https://example.org/mcp\"}")));
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).isMcp()).isTrue();
    }

    @Test
    void computerUseIsNotAOneShotResponsesTool() {
        // computer_use is wired through the dedicated multi-turn loop
        // (TurOpenAiComputerUseService), so the one-shot Responses path skips it.
        assertThat(service.buildTools(List.of(cap(TurNativeCapability.OPENAI_COMPUTER_USE, null))))
                .isEmpty();
    }

    @Test
    void buildToolsToleratesNullAndEmpty() {
        assertThat(service.buildTools(null)).isEmpty();
        assertThat(service.buildTools(List.of())).isEmpty();
    }

    @Test
    void buildFunctionToolsMapsCoexistingCallbacksToFunctionTools() {
        // T433 — a coexisting Turing/MCP/custom tool becomes a Responses function tool.
        org.springframework.ai.tool.ToolCallback callback =
                org.mockito.Mockito.mock(org.springframework.ai.tool.ToolCallback.class);
        org.springframework.ai.tool.definition.ToolDefinition definition =
                org.mockito.Mockito.mock(org.springframework.ai.tool.definition.ToolDefinition.class);
        org.mockito.Mockito.when(definition.name()).thenReturn("finance_quote");
        org.mockito.Mockito.when(definition.description()).thenReturn("Get a quote");
        org.mockito.Mockito.when(definition.inputSchema()).thenReturn(
                "{\"type\":\"object\",\"properties\":{\"symbol\":{\"type\":\"string\"}},"
                        + "\"required\":[\"symbol\"]}");
        org.mockito.Mockito.when(callback.getToolDefinition()).thenReturn(definition);

        List<Tool> tools = service.buildFunctionTools(
                new org.springframework.ai.tool.ToolCallback[] {callback});

        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).isFunction()).isTrue();
        assertThat(tools.get(0).asFunction().name()).isEqualTo("finance_quote");
    }

    @Test
    void buildFunctionToolsToleratesNull() {
        assertThat(service.buildFunctionTools(null)).isEmpty();
    }

    @Test
    void storedCompletionsDirectiveSetsStoreAndMetadata() {
        // F.9 / T167 — an enabled directive flips store:true and tags the request
        // with the metadata map.
        TurLLMInstance instance = new TurLLMInstance();
        instance.setModelName("gpt-4o-mini");
        StoredCompletionsDirective directive = new StoredCompletionsDirective(true,
                Map.of("agentId", "agent-7", "conversationId", "conv-9"));

        ResponseCreateParams params = service.buildParams(instance, List.of(), "You are helpful.",
                List.of(), directive);

        assertThat(params.store()).contains(true);
        assertThat(params.metadata()).isPresent();
        assertThat(params.metadata().get()._additionalProperties().keySet())
                .containsExactlyInAnyOrder("agentId", "conversationId");
    }

    @Test
    void disabledStoredCompletionsLeavesStoreUnset() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setModelName("gpt-4o-mini");

        ResponseCreateParams params = service.buildParams(instance, List.of(), "hi", List.of(),
                StoredCompletionsDirective.DISABLED);

        assertThat(params.store()).isEmpty();
        assertThat(params.metadata()).isEmpty();
    }

    @Test
    void safetyIdentifierIsSetWhenProvidedAndUnsetWhenBlank() {
        // T181 / §X.14.a — a non-blank id rides the request; null/blank leaves it unset.
        TurLLMInstance instance = new TurLLMInstance();
        instance.setModelName("gpt-4o-mini");

        ResponseCreateParams withId = service.buildParams(instance, List.of(), "hi", List.of(),
                "deadbeef-hash");
        assertThat(withId.safetyIdentifier()).contains("deadbeef-hash");

        ResponseCreateParams withoutId = service.buildParams(instance, List.of(), "hi", List.of(),
                (String) null);
        assertThat(withoutId.safetyIdentifier()).isEmpty();
    }

    @Test
    void reasoningOptionsAttachSummaryAndEffortOnReasoningModel() {
        // T178/T179 — on a reasoning model the reasoning config rides the request.
        TurLLMInstance instance = new TurLLMInstance();
        instance.setModelName("o4-mini");

        ResponseCreateParams params = service.buildParams(instance, List.of(), "hi", List.of(),
                new TurOpenAiResponsesService.ReasoningOptions("concise", "high"));

        assertThat(params.reasoning()).isPresent();
        assertThat(params.reasoning().get().summary())
                .contains(com.openai.models.Reasoning.Summary.CONCISE);
        assertThat(params.reasoning().get().effort())
                .contains(com.openai.models.ReasoningEffort.HIGH);
    }

    @Test
    void reasoningOptionsAreIgnoredOnNonReasoningModel() {
        // T178 — a chat model (gpt-4o-mini) would reject the reasoning param, so
        // it is never attached even when the agent set the request option.
        TurLLMInstance instance = new TurLLMInstance();
        instance.setModelName("gpt-4o-mini");

        ResponseCreateParams params = service.buildParams(instance, List.of(), "hi", List.of(),
                new TurOpenAiResponsesService.ReasoningOptions("auto", null));

        assertThat(params.reasoning()).isEmpty();
    }

    @Test
    void noneReasoningOptionsLeaveRequestUnchanged() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setModelName("o3");

        ResponseCreateParams params = service.buildParams(instance, List.of(), "hi", List.of(),
                TurOpenAiResponsesService.ReasoningOptions.NONE);

        assertThat(params.reasoning()).isEmpty();
    }

    @Test
    void reasoningModelDetectionCoversOSeriesAndGpt5() {
        assertThat(TurOpenAiResponsesService.isReasoningModelName("o1")).isTrue();
        assertThat(TurOpenAiResponsesService.isReasoningModelName("o1-mini")).isTrue();
        assertThat(TurOpenAiResponsesService.isReasoningModelName("o3")).isTrue();
        assertThat(TurOpenAiResponsesService.isReasoningModelName("o4-mini")).isTrue();
        assertThat(TurOpenAiResponsesService.isReasoningModelName("GPT-5")).isTrue();
        assertThat(TurOpenAiResponsesService.isReasoningModelName("gpt-4o-mini")).isFalse();
        assertThat(TurOpenAiResponsesService.isReasoningModelName("gpt-4.1")).isFalse();
        assertThat(TurOpenAiResponsesService.isReasoningModelName(null)).isFalse();
    }

    @Test
    void safeImageIdStripsUnsafeCharsAndFallsBack() {
        // Provider ids are kept verbatim when already filename-safe.
        assertThat(TurOpenAiResponsesService.safeImageId("ig_abc-123", 0)).isEqualTo("ig_abc-123");
        // Path/extension characters are stripped so the key can't escape the folder.
        assertThat(TurOpenAiResponsesService.safeImageId("../../etc/passwd.png", 0))
                .isEqualTo("etcpasswdpng");
        // Blank / null id falls back to the positional index.
        assertThat(TurOpenAiResponsesService.safeImageId(null, 2)).isEqualTo("image-2");
        assertThat(TurOpenAiResponsesService.safeImageId("   ", 5)).isEqualTo("image-5");
        assertThat(TurOpenAiResponsesService.safeImageId("///", 3)).isEqualTo("image-3");
    }

    // ----- T503 background mode -----

    @Test
    void backgroundDisabledByDefault() {
        TurLLMInstance instance = new TurLLMInstance();
        assertThat(service.resolveBackground(instance).enabled()).isFalse();
        instance.setProviderOptionsJson("{\"backgroundEnabled\":false}");
        assertThat(service.resolveBackground(instance).enabled()).isFalse();
    }

    @Test
    void backgroundResolvesDefaultsWhenEnabled() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setProviderOptionsJson("{\"backgroundEnabled\":true}");
        var bg = service.resolveBackground(instance);
        assertThat(bg.enabled()).isTrue();
        assertThat(bg.pollIntervalMs()).isEqualTo(TurOpenAiResponsesService.DEFAULT_BG_POLL_MS);
        assertThat(bg.maxWaitMs()).isEqualTo(TurOpenAiResponsesService.DEFAULT_BG_MAX_WAIT_MS);
    }

    @Test
    void backgroundHonorsCustomPollAndMaxWait() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setProviderOptionsJson(
                "{\"backgroundEnabled\":true,\"backgroundPollIntervalMs\":250,\"backgroundMaxWaitMs\":5000}");
        var bg = service.resolveBackground(instance);
        assertThat(bg.pollIntervalMs()).isEqualTo(250L);
        assertThat(bg.maxWaitMs()).isEqualTo(5000L);
    }

    @Test
    void buildParamsSetsBackgroundAndStoreWhenEnabled() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setProviderOptionsJson("{\"backgroundEnabled\":true}");
        ResponseCreateParams params = service.buildParams(instance, List.of(), "hi", List.of());
        assertThat(params.background()).contains(true);
        // Background responses must be stored so retrieve() can resume them.
        assertThat(params.store()).contains(true);
    }

    @Test
    void buildParamsLeavesBackgroundUnsetByDefault() {
        TurLLMInstance instance = new TurLLMInstance();
        ResponseCreateParams params = service.buildParams(instance, List.of(), "hi", List.of());
        assertThat(params.background()).isEmpty();
    }

    @Test
    void personaCalibrationOverridesTemperatureAndSetsMaxOutputTokens() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setTemperature(0.9);
        ResponseCreateParams params = service.buildParams(instance, List.of(), "hi", List.of(),
                new Params(0.2, 4096));
        assertThat(params.temperature()).contains(0.2);
        // The chat path sets no maxOutputTokens by default; calibration adds it.
        assertThat(params.maxOutputTokens()).contains(4096L);
    }

    @Test
    void noneCalibrationKeepsInstanceTemperatureAndNoMaxOutputTokens() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setTemperature(0.9);
        ResponseCreateParams params = service.buildParams(instance, List.of(), "hi", List.of(),
                Params.NONE);
        assertThat(params.temperature()).contains(0.9);
        assertThat(params.maxOutputTokens()).isEmpty();
    }

    @Test
    void isTerminalStopsOnlyOnNonPendingStatus() {
        assertThat(TurOpenAiResponsesService.isTerminal(
                com.openai.models.responses.ResponseStatus.QUEUED)).isFalse();
        assertThat(TurOpenAiResponsesService.isTerminal(
                com.openai.models.responses.ResponseStatus.IN_PROGRESS)).isFalse();
        assertThat(TurOpenAiResponsesService.isTerminal(
                com.openai.models.responses.ResponseStatus.COMPLETED)).isTrue();
        assertThat(TurOpenAiResponsesService.isTerminal(
                com.openai.models.responses.ResponseStatus.FAILED)).isTrue();
        assertThat(TurOpenAiResponsesService.isTerminal(null)).isTrue();
    }
}

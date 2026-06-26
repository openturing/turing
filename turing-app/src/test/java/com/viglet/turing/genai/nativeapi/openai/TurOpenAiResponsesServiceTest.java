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

import org.junit.jupiter.api.Test;

import com.openai.models.responses.Tool;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;

class TurOpenAiResponsesServiceTest {

    private final TurProviderOptionsParser optionsParser = new TurProviderOptionsParser();
    private final TurOpenAiResponsesService service = new TurOpenAiResponsesService(
            optionsParser, new com.viglet.turing.genai.nativeapi.TurNativeFunctionToolSupport(optionsParser));

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
}

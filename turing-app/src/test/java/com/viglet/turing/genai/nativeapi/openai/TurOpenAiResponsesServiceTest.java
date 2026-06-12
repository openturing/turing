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

    private final TurOpenAiResponsesService service =
            new TurOpenAiResponsesService(new TurProviderOptionsParser());

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
        assertThat(tools).hasSize(2);
        assertThat(tools).anyMatch(Tool::isCodeInterpreter);
        assertThat(tools).anyMatch(Tool::isImageGeneration);
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
    void computerUseIsAdvertisedButNotWired() {
        assertThat(service.buildTools(List.of(cap(TurNativeCapability.OPENAI_COMPUTER_USE, null))))
                .isEmpty();
    }

    @Test
    void buildToolsToleratesNullAndEmpty() {
        assertThat(service.buildTools(null)).isEmpty();
        assertThat(service.buildTools(List.of())).isEmpty();
    }
}

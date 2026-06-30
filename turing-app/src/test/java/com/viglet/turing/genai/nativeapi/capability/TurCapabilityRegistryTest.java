/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.genai.tool.TurNativeToolService.NativeToolDescriptor;
import com.viglet.turing.genai.tool.TurNativeToolService.NativeToolGroup;

@ExtendWith(MockitoExtension.class)
class TurCapabilityRegistryTest {

    @Mock
    private TurNativeToolService nativeToolService;

    private TurCapabilityRegistry registry() {
        return new TurCapabilityRegistry(nativeToolService);
    }

    @Test
    void nativeDescriptorsCoverEveryEnumConstant() {
        List<TurCapabilityDescriptor> natives = registry().nativeDescriptors();
        assertThat(natives).hasSize(TurNativeCapability.values().length);
        assertThat(natives)
                .allSatisfy(d -> {
                    assertThat(d.kind()).isNotNull();
                    assertThat(d.function()).isNotBlank();
                    assertThat(d.provider()).isNotNull();
                    assertThat(d.category()).isNotBlank();
                    assertThat(d.toolNames()).isEmpty();
                });
    }

    @Test
    void webSearchSharesFunctionAcrossVendorsForMutex() {
        List<TurCapabilityDescriptor> natives = registry().nativeDescriptors();
        List<String> webSearchProviders = natives.stream()
                .filter(d -> d.function().equals(TurCapabilityFunctions.WEB_SEARCH))
                .map(d -> d.provider().name())
                .sorted()
                .toList();
        // OpenAI web_search ↔ Anthropic web_search ↔ Gemini google_search (T490)
        // all share the WEB_SEARCH function, so they form one mutex group.
        assertThat(webSearchProviders).containsExactly("ANTHROPIC", "GEMINI", "OPENAI");
    }

    @Test
    void computerUseOwnsTurn() {
        assertThat(registry().nativeDescriptors())
                .filteredOn(d -> d.function().equals(TurCapabilityFunctions.COMPUTER_USE))
                .isNotEmpty()
                .allSatisfy(d -> assertThat(d.ownsTurn()).isTrue());
    }

    @Test
    void turingCodeInterpreterOverlapsCodeExecFunction() {
        when(nativeToolService.getToolGroups()).thenReturn(List.of(
                new NativeToolGroup("code-interpreter", "Code Interpreter",
                        List.of(new NativeToolDescriptor("execute_python", "run python", "code-interpreter"))),
                new NativeToolGroup("finance", "Finance",
                        List.of(new NativeToolDescriptor("get_quote", "stock quote", "finance")))));

        List<TurCapabilityDescriptor> turing = registry().turingDescriptors();

        TurCapabilityDescriptor codeInterpreter = turing.stream()
                .filter(d -> d.key().equals("code-interpreter")).findFirst().orElseThrow();
        // Cross-source overlap → shares CODE_EXEC with the provider code tools.
        assertThat(codeInterpreter.function()).isEqualTo(TurCapabilityFunctions.CODE_EXEC);
        assertThat(codeInterpreter.provider()).isEqualTo(TurCapabilityProvider.TURING);
        assertThat(codeInterpreter.toolNames()).containsExactly("execute_python");
        assertThat(codeInterpreter.category()).isEqualTo("Code");

        // No overlap → keeps its own group id as the function.
        TurCapabilityDescriptor finance = turing.stream()
                .filter(d -> d.key().equals("finance")).findFirst().orElseThrow();
        assertThat(finance.function()).isEqualTo("finance");
    }

    @Test
    void allMergesNativeTuringAndRequestOptions() {
        when(nativeToolService.getToolGroups()).thenReturn(List.of(
                new NativeToolGroup("finance", "Finance",
                        List.of(new NativeToolDescriptor("get_quote", "stock quote", "finance")))));
        TurCapabilityRegistry registry = registry();
        int requestOptions = registry.requestOptionDescriptors().size();
        List<TurCapabilityDescriptor> all = registry.all();
        assertThat(all).hasSize(TurNativeCapability.values().length + 1 + requestOptions);
        assertThat(all).anyMatch(d -> d.provider() == TurCapabilityProvider.TURING);
        assertThat(all).anyMatch(d -> d.provider() == TurCapabilityProvider.OPENAI);
        assertThat(all).anyMatch(d -> d.kind() == TurCapabilityKind.REQUEST_OPTION);
    }

    @Test
    void requestOptionsAreWellFormedAndNotTools() {
        assertThat(registry().requestOptionDescriptors())
                .isNotEmpty()
                .allSatisfy(d -> {
                    assertThat(d.kind()).isEqualTo(TurCapabilityKind.REQUEST_OPTION);
                    assertThat(d.valueType()).isIn("BOOLEAN", "SELECT");
                    assertThat(d.toolNames()).isEmpty();
                    if ("SELECT".equals(d.valueType())) {
                        assertThat(d.options()).isNotEmpty();
                    }
                });
    }

    @Test
    void humanizeKeyFixesProviderTokens() {
        assertThat(TurCapabilityRegistry.humanizeKey("openai-web-search")).isEqualTo("OpenAI Web Search");
        assertThat(TurCapabilityRegistry.humanizeKey("anthropic-code-execution"))
                .isEqualTo("Anthropic Code Execution");
        assertThat(TurCapabilityRegistry.humanizeKey("openai-mcp")).isEqualTo("OpenAI MCP");
    }
}

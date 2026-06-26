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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.capability.TurNativeCapabilityResolver.NativeSelection;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.genai.tool.TurNativeToolService.NativeToolDescriptor;
import com.viglet.turing.genai.tool.TurNativeToolService.NativeToolGroup;

/**
 * Unit tests for {@link TurNativeCapabilityResolver} — the pure
 * "capacidade é mutex, resto coexiste" resolution (T433 / §X.18.b).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurNativeCapabilityResolverTest {

    private final TurNativeToolService nativeToolService = mock(TurNativeToolService.class);
    private final TurCapabilityRegistry registry = new TurCapabilityRegistry(nativeToolService);
    private final TurNativeCapabilityResolver resolver = new TurNativeCapabilityResolver(registry);

    private static EnabledCapability cap(TurNativeCapability capability) {
        return new EnabledCapability(capability, null);
    }

    private static ToolCallback tool(String name) {
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        lenient().when(definition.name()).thenReturn(name);
        lenient().when(callback.getToolDefinition()).thenReturn(definition);
        return callback;
    }

    private void stubGroups() {
        when(nativeToolService.getToolGroups()).thenReturn(List.of(
                new NativeToolGroup("code-interpreter", "Code Interpreter",
                        List.of(new NativeToolDescriptor("execute_python", "", "code-interpreter"))),
                new NativeToolGroup("finance", "Finance",
                        List.of(new NativeToolDescriptor("finance_quote", "", "finance")))));
    }

    @Test
    void isLegacyOnlyWhenSelectionIsNull() {
        assertThat(resolver.isLegacy(null)).isTrue();
        assertThat(resolver.isLegacy("")).isFalse();
        assertThat(resolver.isLegacy("openai-web-search")).isFalse();
    }

    @Test
    void parseSelectionTrimsAndLowercasesAndDropsBlanks() {
        assertThat(resolver.parseSelection(null)).isEmpty();
        assertThat(resolver.parseSelection(" Openai-Web-Search , , anthropic-bash "))
                .containsExactlyInAnyOrder("openai-web-search", "anthropic-bash");
    }

    @Test
    void resolveIntersectsSelectionWithInstanceCapabilities() {
        List<EnabledCapability> instance = List.of(
                cap(TurNativeCapability.OPENAI_WEB_SEARCH),
                cap(TurNativeCapability.OPENAI_CODE_INTERPRETER));

        NativeSelection selection = resolver.resolve("openai-web-search", instance);

        assertThat(selection.nativeCapabilities())
                .extracting(c -> c.capability())
                .containsExactly(TurNativeCapability.OPENAI_WEB_SEARCH);
        assertThat(selection.claimedFunctions()).containsExactly("web-search");
        assertThat(selection.hasOwnsTurn()).isFalse();
        assertThat(selection.isEmpty()).isFalse();
    }

    @Test
    void resolveEmptyWhenNoOverlap() {
        NativeSelection selection = resolver.resolve("anthropic-web-search",
                List.of(cap(TurNativeCapability.OPENAI_WEB_SEARCH)));
        assertThat(selection.isEmpty()).isTrue();
        assertThat(selection.claimedFunctions()).isEmpty();
    }

    @Test
    void resolveFlagsOwnsTurnCapability() {
        NativeSelection selection = resolver.resolve("openai-computer-use",
                List.of(cap(TurNativeCapability.OPENAI_COMPUTER_USE)));
        assertThat(selection.hasOwnsTurn()).isTrue();
        assertThat(selection.ownsTurnCapability()).isEqualTo(TurNativeCapability.OPENAI_COMPUTER_USE);
    }

    @Test
    void coexistingDropsSameFunctionTuringToolKeepsOthers() {
        stubGroups();
        // code-exec is claimed (e.g. OpenAI code_interpreter selected); the Turing
        // code-interpreter tool must drop, finance + an MCP/custom tool must stay.
        ToolCallback[] agentTools = {
                tool("execute_python"), tool("finance_quote"), tool("some_mcp_tool")};

        ToolCallback[] kept = resolver.coexistingTools(agentTools, java.util.Set.of("code-exec"));

        assertThat(kept).extracting(c -> c.getToolDefinition().name())
                .containsExactly("finance_quote", "some_mcp_tool");
    }

    @Test
    void coexistingReturnsAllWhenNothingClaimed() {
        ToolCallback[] agentTools = {tool("finance_quote")};
        assertThat(resolver.coexistingTools(agentTools, java.util.Set.of())).hasSize(1);
        assertThat(resolver.coexistingTools(agentTools, null)).hasSize(1);
    }
}

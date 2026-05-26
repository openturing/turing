/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;

import com.viglet.turing.genai.flow.ChatFlowNode.NodeData;
import com.viglet.turing.genai.flow.TurFunctionCallNodeExecutor.ExecutionResult;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

/**
 * Pin tests for the T46 {@code functionCall} runtime. Exercises:
 * <ul>
 *   <li>NATIVE happy path — slot interpolation feeds the callback, result
 *       lands in {@code outputVariable}.</li>
 *   <li>missing tool, missing functionName, missing state — graceful
 *       failures, no callback invocation.</li>
 *   <li>{@code toolSource=MCP} — explicit "not supported in v1" pass-through
 *       so the engine advances rather than blowing up.</li>
 *   <li>side-effect only (no {@code outputVariable}) — callback still
 *       invoked, variable map unchanged.</li>
 *   <li>callback throws — failure reported, no slot write.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurFunctionCallNodeExecutorTest {

    private static final String INSTRUCTION_TEMPLATE =
            "{\"site\": \"{{site}}\", \"q\": \"{{query}}\"}";

    @Test
    @DisplayName("NATIVE happy path: interpolates {{slots}}, invokes callback, writes outputVariable")
    void execute_happyPath() throws Exception {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(
                new DefaultToolDefinition("search_site", "search a site", "{}"));
        when(callback.call(any())).thenReturn("hit-1 hit-2 hit-3");

        TurNativeToolService nativeToolService = mock(TurNativeToolService.class);
        when(nativeToolService.getToolCallbacks(anySet())).thenReturn(new ToolCallback[] { callback });

        TurFunctionCallNodeExecutor executor = new TurFunctionCallNodeExecutor(nativeToolService);
        TurChatFlowState state = stateWithSlots(java.util.Map.of("site", "edu", "query", "machine learning"));
        ChatFlowNode node = functionCallNode("NATIVE", "search_site",
                INSTRUCTION_TEMPLATE, "ann_results");

        ExecutionResult result = executor.execute(state, node);

        verify(callback).call("{\"site\": \"edu\", \"q\": \"machine learning\"}");
        assertThat(result.ok()).isTrue();
        assertThat(result.result()).isEqualTo("hit-1 hit-2 hit-3");
        assertThat(ChatFlowOps.readVariables(state).get("ann_results")).isEqualTo("hit-1 hit-2 hit-3");
    }

    @Test
    @DisplayName("blank aiInstruction defaults to '{}' — callback still invoked")
    void execute_blankInstructionSendsEmptyJson() throws Exception {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(
                new DefaultToolDefinition("now", "current time", "{}"));
        when(callback.call("{}")).thenReturn("2026-05-26T10:00:00Z");
        TurNativeToolService nativeToolService = mock(TurNativeToolService.class);
        when(nativeToolService.getToolCallbacks(anySet())).thenReturn(new ToolCallback[] { callback });

        TurFunctionCallNodeExecutor executor = new TurFunctionCallNodeExecutor(nativeToolService);

        ExecutionResult result = executor.execute(stateWithSlots(java.util.Map.of()),
                functionCallNode("NATIVE", "now", null, "current_time"));

        verify(callback).call("{}");
        assertThat(result.ok()).isTrue();
    }

    @Test
    @DisplayName("side-effect only: blank outputVariable → no slot write, callback still invoked")
    void execute_sideEffectOnly_noSlotWrite() throws Exception {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(
                new DefaultToolDefinition("post_lead", "post a lead", "{}"));
        when(callback.call(any())).thenReturn("OK");
        TurNativeToolService nativeToolService = mock(TurNativeToolService.class);
        when(nativeToolService.getToolCallbacks(anySet())).thenReturn(new ToolCallback[] { callback });

        TurFunctionCallNodeExecutor executor = new TurFunctionCallNodeExecutor(nativeToolService);
        TurChatFlowState state = stateWithSlots(java.util.Map.of("email", "a@b.com"));
        ChatFlowNode node = functionCallNode("NATIVE", "post_lead",
                "{\"email\": \"{{email}}\"}", "  ");

        ExecutionResult result = executor.execute(state, node);

        assertThat(result.ok()).isTrue();
        // Variable map must NOT carry a new slot — outputVariable was blank.
        assertThat(ChatFlowOps.readVariables(state)).containsOnlyKeys("email");
    }

    @Test
    @DisplayName("missing functionName → failure, no callback resolution attempted")
    void execute_missingFunctionName_fails() {
        TurNativeToolService nativeToolService = mock(TurNativeToolService.class);
        TurFunctionCallNodeExecutor executor = new TurFunctionCallNodeExecutor(nativeToolService);

        ExecutionResult result = executor.execute(
                stateWithSlots(java.util.Map.of()),
                functionCallNode("NATIVE", "  ", "{}", "out"));

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("functionName");
        verify(nativeToolService, never()).getToolCallbacks(anySet());
    }

    @Test
    @DisplayName("tool not found in registry → failure, no slot mutation")
    void execute_toolNotFound_fails() {
        TurNativeToolService nativeToolService = mock(TurNativeToolService.class);
        when(nativeToolService.getToolCallbacks(anySet())).thenReturn(new ToolCallback[0]);
        TurFunctionCallNodeExecutor executor = new TurFunctionCallNodeExecutor(nativeToolService);

        TurChatFlowState state = stateWithSlots(java.util.Map.of("foo", "bar"));
        ExecutionResult result = executor.execute(state,
                functionCallNode("NATIVE", "ghost_tool", "{}", "out"));

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("ghost_tool");
        // No mutation — state's variable map is preserved.
        assertThat(ChatFlowOps.readVariables(state)).containsOnlyKeys("foo");
    }

    @Test
    @DisplayName("toolSource=MCP is acknowledged but skipped in v1 (T46) — engine advances cleanly")
    void execute_mcpSource_skippedInV1() {
        TurNativeToolService nativeToolService = mock(TurNativeToolService.class);
        TurFunctionCallNodeExecutor executor = new TurFunctionCallNodeExecutor(nativeToolService);

        ExecutionResult result = executor.execute(stateWithSlots(java.util.Map.of()),
                mcpFunctionCallNode());

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("MCP");
        verify(nativeToolService, never()).getToolCallbacks(anySet());
    }

    @Test
    @DisplayName("callback throws → failure, no slot write, exception swallowed")
    void execute_callbackThrows_isFailureNotPropagated() throws Exception {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(
                new DefaultToolDefinition("flaky", "flaky tool", "{}"));
        when(callback.call(any())).thenThrow(new RuntimeException("upstream 500"));
        TurNativeToolService nativeToolService = mock(TurNativeToolService.class);
        when(nativeToolService.getToolCallbacks(anySet())).thenReturn(new ToolCallback[] { callback });

        TurFunctionCallNodeExecutor executor = new TurFunctionCallNodeExecutor(nativeToolService);
        TurChatFlowState state = stateWithSlots(java.util.Map.of());

        ExecutionResult result = executor.execute(state,
                functionCallNode("NATIVE", "flaky", "{}", "out"));

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("upstream 500");
        assertThat(ChatFlowOps.readVariables(state)).isEmpty();
    }

    @Test
    @DisplayName("unknown toolSource string → failure, no callback resolution")
    void execute_unknownToolSource_fails() {
        TurNativeToolService nativeToolService = mock(TurNativeToolService.class);
        TurFunctionCallNodeExecutor executor = new TurFunctionCallNodeExecutor(nativeToolService);

        ExecutionResult result = executor.execute(stateWithSlots(java.util.Map.of()),
                functionCallNodeWithSource("WEIRD", "x", "{}", "out"));

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("WEIRD");
        verify(nativeToolService, never()).getToolCallbacks(anySet());
    }

    @Test
    @DisplayName("null state / null node → failure without NPE")
    void execute_nullInputs_failGracefully() {
        TurNativeToolService nativeToolService = mock(TurNativeToolService.class);
        TurFunctionCallNodeExecutor executor = new TurFunctionCallNodeExecutor(nativeToolService);

        assertThat(executor.execute(null, functionCallNode("NATIVE", "x", "{}", "out")).ok()).isFalse();
        assertThat(executor.execute(stateWithSlots(java.util.Map.of()), null).ok()).isFalse();
    }

    @Test
    @DisplayName("null toolSource defaults to NATIVE — backward-compat for older flows")
    void execute_nullToolSourceDefaultsToNative() throws Exception {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(
                new DefaultToolDefinition("hello", "greet", "{}"));
        when(callback.call("{}")).thenReturn("world");
        TurNativeToolService nativeToolService = mock(TurNativeToolService.class);
        when(nativeToolService.getToolCallbacks(anySet())).thenReturn(new ToolCallback[] { callback });

        TurFunctionCallNodeExecutor executor = new TurFunctionCallNodeExecutor(nativeToolService);
        ExecutionResult result = executor.execute(stateWithSlots(java.util.Map.of()),
                functionCallNodeWithSource(null, "hello", "{}", "out"));

        assertThat(result.ok()).isTrue();
        verify(callback).call("{}");
    }

    // ─────────────────────────── helpers ───────────────────────────

    private static TurChatFlowState stateWithSlots(java.util.Map<String, String> slots) {
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId("conv-test");
        ChatFlowOps.writeVariables(state, new java.util.LinkedHashMap<>(slots));
        return state;
    }

    /** Build a {@code functionCall} node with the standard NATIVE / NATIVE-empty toolSource. */
    private static ChatFlowNode functionCallNode(String toolSource, String functionName,
            String instruction, String outputVariable) {
        return functionCallNodeWithSource(toolSource, functionName, instruction, outputVariable);
    }

    private static ChatFlowNode functionCallNodeWithSource(String toolSource, String functionName,
            String instruction, String outputVariable) {
        return new ChatFlowNode("fc-1", "functionCall", new NodeData(
                "Function call", "functionCall",
                instruction,           // aiInstruction (used as JSON template)
                outputVariable,        // outputVariable (target slot)
                null,                  // validationRule
                functionName,          // functionName
                null,                  // conditionExpression
                toolSource,            // toolSource
                null,                  // mcpServerId
                null,                  // subFlowId
                null,                  // subFlowName
                null,                  // personaId
                null,                  // switchVariable
                List.of(),             // switchOptions
                List.of(),             // inlineOptions
                null,                  // overrideExistingValue
                null,                  // slotName
                null,                  // slotOperation
                null,                  // slotValue
                null,                  // onJudgeReject
                null,                  // toolsEnabled
                List.of(),             // requiredTools
                null,                  // routineId
                null                   // routineTimeoutMs
        ));
    }

    private static ChatFlowNode mcpFunctionCallNode() {
        return new ChatFlowNode("fc-mcp", "functionCall", new NodeData(
                "MCP call", "functionCall",
                "{}", "out", null,
                "remote_tool",          // functionName — MCP nodes still name a tool
                null,
                "MCP", "some-mcp-server-id",
                null, null, null, null,
                List.of(), List.of(),
                null, null, null, null, null, null,
                List.of(), null, null));
    }
}

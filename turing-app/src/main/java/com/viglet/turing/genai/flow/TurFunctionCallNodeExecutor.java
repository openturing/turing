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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

import lombok.extern.slf4j.Slf4j;

/**
 * T46 — runtime execution for the {@code functionCall} chat-flow node type.
 * Invokes a tool deterministically from the flow (not from the LLM), so a
 * flow author can guarantee a side-effect call (price lookup, CRM hit, slot
 * hydration for a UI card) happens at a precise step without waiting for the
 * LLM to "decide" to call the tool.
 *
 * <p><b>Schema</b> (reuses existing {@link ChatFlowNode} fields, no new
 * data-model columns):
 * <ul>
 *   <li>{@code toolSource} — {@code "NATIVE"} for v1. {@code "MCP"} is
 *       intentionally unsupported in v1 — the flow logs a warning and
 *       advances. Custom Tools are NATIVE-callable via their sanitised
 *       title so they ride the same path.</li>
 *   <li>{@code functionName} — the tool name as Spring AI sees it
 *       (e.g. {@code "search_site"}, {@code "get_current_time"}). Lookup is
 *       routed through {@link TurNativeToolService#getToolCallbacks(Set)}
 *       which already filters by {@code ToolDefinition.name()}.</li>
 *   <li>{@code aiInstruction} — used as the tool input JSON template, with
 *       {@code &#123;&#123;slotName&#125;&#125;} placeholders interpolated
 *       from the conversation's variable map. Blank → {@code "{}"}.
 *       Semantic shift from the LLM-narrative meaning {@code aiInstruction}
 *       has on other node types; this is documented in the authoring
 *       skill prompt.</li>
 *   <li>{@code outputVariable} — slot to write the tool's String result
 *       into. Optional — when blank the call is side-effect only (e.g. a
 *       CRM push that returns void).</li>
 * </ul>
 *
 * <p><b>Error handling.</b> Tool not found, MCP source, JSON-template
 * failure, or callback throwing all result in {@link ExecutionResult#ok()}
 * = {@code false} and a logged warning; the engine advances to the next
 * edge regardless (matches the lenient default the rest of the flow ops
 * use). Stricter {@code continueOnFailure}/{@code onError} routing is
 * deferred to T49/T50.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurFunctionCallNodeExecutor {

    private final TurNativeToolService nativeToolService;

    public TurFunctionCallNodeExecutor(TurNativeToolService nativeToolService) {
        this.nativeToolService = nativeToolService;
    }

    /**
     * Executes the {@code functionCall} described by {@code node} against
     * {@code state}'s variable map. Side-effects on success:
     * <ul>
     *   <li>the variable map gets {@code outputVariable} = tool result (when
     *       set), persisted via
     *       {@link ChatFlowOps#writeVariables(TurChatFlowState, Map)};</li>
     *   <li>the result is also returned in {@link ExecutionResult} so the
     *       caller can decide whether to emit telemetry / log lines.</li>
     * </ul>
     * Caller is responsible for advancing the cursor + saving state +
     * publishing the slot-event SSE; this method just mutates the in-memory
     * variable map.
     */
    public ExecutionResult execute(TurChatFlowState state, ChatFlowNode node) {
        if (state == null || node == null) {
            return ExecutionResult.failure("state or node is null");
        }
        String functionName = node.functionName();
        if (functionName == null || functionName.isBlank()) {
            return ExecutionResult.failure("functionCall node '" + node.id()
                    + "' has no functionName — skipping");
        }
        String source = node.toolSource() == null ? "NATIVE" : node.toolSource().trim().toUpperCase();
        if ("MCP".equals(source)) {
            log.warn("[FlowOps/functionCall] node '{}' uses toolSource=MCP — not yet supported in v1 (T46); "
                    + "engine will advance without invoking. Track in TK4 follow-up.", node.id());
            return ExecutionResult.failure("MCP toolSource not supported in v1");
        }
        if (!"NATIVE".equals(source)) {
            return ExecutionResult.failure("Unknown toolSource '" + node.toolSource()
                    + "' on functionCall node '" + node.id() + "'");
        }

        ToolCallback[] callbacks = nativeToolService.getToolCallbacks(Set.of(functionName));
        if (callbacks.length == 0) {
            return ExecutionResult.failure("native tool '" + functionName
                    + "' not found for functionCall node '" + node.id() + "'");
        }
        ToolCallback callback = callbacks[0];

        Map<String, String> variables = ChatFlowOps.readVariables(state);
        String rawTemplate = node.aiInstruction();
        String inputJson = (rawTemplate == null || rawTemplate.isBlank())
                ? "{}"
                : ChatFlowOps.interpolateVariables(rawTemplate, variables);

        String result;
        try {
            result = callback.call(inputJson);
        } catch (Exception e) {
            log.warn("[FlowOps/functionCall] node '{}' invoking '{}' failed: {}",
                    node.id(), functionName, e.getMessage(), e);
            return ExecutionResult.failure(e.getMessage());
        }

        String outputSlot = node.outputVariable();
        if (outputSlot != null && !outputSlot.isBlank()) {
            // Allocate a fresh map so we don't mutate the caller's view of
            // the original map and so writeVariables sees the new key.
            Map<String, String> next = new LinkedHashMap<>(variables);
            next.put(outputSlot.trim(), result == null ? "" : result);
            ChatFlowOps.writeVariables(state, next);
            log.info("[FlowOps/functionCall] node '{}' '{}' wrote {} chars into slot '{}' on conv '{}'",
                    node.id(), functionName, result == null ? 0 : result.length(),
                    outputSlot, state.getConversationId());
        } else {
            log.info("[FlowOps/functionCall] node '{}' '{}' returned {} chars (no outputVariable — side-effect only) on conv '{}'",
                    node.id(), functionName, result == null ? 0 : result.length(),
                    state.getConversationId());
        }
        return ExecutionResult.success(result);
    }

    /**
     * Outcome of a {@link #execute(TurChatFlowState, ChatFlowNode)} call.
     * {@code ok=true} ⇒ tool ran without throwing (return value may still
     * be the tool's own error string — the engine doesn't second-guess
     * that). {@code ok=false} ⇒ resolution or invocation failure;
     * {@code error} carries a one-line reason for the warning log.
     */
    public record ExecutionResult(boolean ok, String result, String error) {

        static ExecutionResult success(String result) {
            return new ExecutionResult(true, result, null);
        }

        static ExecutionResult failure(String error) {
            return new ExecutionResult(false, null, error);
        }
    }
}

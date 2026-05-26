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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single step in a {@link ChatFlowGraph}. Mirrors the React Flow node
 * shape: identity + position carried for the editor, and the {@code data}
 * sub-object holding the runtime fields the engine consumes.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatFlowNode(String id, String type, NodeData data) {

    public ChatFlowNode {
        data = data == null
                ? new NodeData(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
                : data;
    }

    public String label() {
        return data.label();
    }

    public String aiInstruction() {
        return data.aiInstruction();
    }

    public String outputVariable() {
        return data.outputVariable();
    }

    public String validationRule() {
        return data.validationRule();
    }

    public String functionName() {
        return data.functionName();
    }

    public String conditionExpression() {
        return data.conditionExpression();
    }

    public String toolSource() {
        return data.toolSource();
    }

    public String mcpServerId() {
        return data.mcpServerId();
    }

    public String subFlowId() {
        return data.subFlowId();
    }

    public String subFlowName() {
        return data.subFlowName();
    }

    /**
     * Persona id this node should switch the conversation's active voice to.
     * Only meaningful when the node's type is {@code persona}; ignored otherwise.
     *
     * @since 2026.2.7
     */
    public String personaId() {
        return data.personaId();
    }

    /**
     * Variable name read from the flow's collected variables to drive a {@code switch} node's
     * multi-way branch. Typically the {@code outputVariable} of an upstream AI Question so the
     * switch routes on what the user just said.
     *
     * @since 2026.2.7
     */
    public String switchVariable() {
        return data.switchVariable();
    }

    /**
     * Branches exposed by a {@code switch} node. Each entry's {@code id} matches an outgoing
     * edge's {@code sourceHandle}; the {@code label} is the human-readable text the engine
     * matches against the resolved switch variable.
     *
     * @since 2026.2.7
     */
    public List<SwitchOption> switchOptions() {
        return data.switchOptions() == null ? List.of() : data.switchOptions();
    }

    /**
     * Optional list of suggested answers for an {@code aiQuestion} node — surfaces as
     * clickable chips in the chat UI so the user can pick instead of typing. Unlike
     * {@link #switchOptions()}, picking one of these does <em>not</em> branch the flow —
     * the chosen label is sent as the user message and captured into {@code outputVariable}
     * exactly as if typed. Free-text input remains available in parallel.
     *
     * @since 2026.2.7
     */
    public List<String> inlineOptions() {
        return data.inlineOptions() == null ? List.of() : data.inlineOptions();
    }

    /**
     * When {@code true} on an {@code aiQuestion} node, the engine always
     * asks the question and overwrites any value already collected into
     * {@link #outputVariable()}. When {@code false} (or {@code null}) and
     * the slot already holds a non-blank value in the flow state's
     * variables, the node is treated as a transparent transition and the
     * engine skips straight to the next edge — no LLM round-trip.
     *
     * @since 2026.2.7
     */
    public Boolean overrideExistingValue() {
        return data.overrideExistingValue();
    }

    /**
     * Slot the {@code slot} node operates on. Must be one of the agent's
     * declared slots so the value lands in a stable, typed schema.
     *
     * @since 2026.2.7
     */
    public String slotName() {
        return data.slotName();
    }

    /**
     * Operation performed by a {@code slot} node: {@code SET} (write
     * {@link #slotValue()} into the conversation state, honoring
     * {@link #overrideExistingValue()} when the slot is already populated)
     * or {@code DELETE} (remove the slot entirely).
     *
     * @since 2026.2.7
     */
    public String slotOperation() {
        return data.slotOperation();
    }

    /**
     * Literal value the {@code slot} node writes when {@link #slotOperation()}
     * is {@code SET}. Ignored for {@code DELETE}.
     *
     * @since 2026.2.7
     */
    public String slotValue() {
        return data.slotValue();
    }

    /**
     * Soft-fail policy for the LLM_JUDGE guardrail on this {@code aiQuestion}
     * node. Controls what happens when the judge rejects the user's reply on a
     * slot-collecting node:
     *
     * <ul>
     *   <li>{@code "advance_with_literal"} — after retry, force-capture the
     *       user's message into {@code outputVariable} and advance regardless
     *       of whether the judge produced a substantive redirect. Useful on
     *       free-form slots (cargo, objetivo) where the judge occasionally
     *       rejects valid compound answers and would otherwise leave the flow
     *       stuck with a null slot.</li>
     *   <li>{@code "reprompt"} — default. Stay on the node and surface the
     *       judge's redirect_message; force-capture only when both verdicts
     *       returned blank redirects (current heuristic at
     *       {@code LlmJudgeGuardrailStrategy#isNonBlankRedirect}).</li>
     *   <li>{@code "block"} — never force-capture. Always trust the judge's
     *       rejection. Use on critical-validation slots (CPF, email, payment)
     *       where capturing a wrong value is worse than reprompting.</li>
     * </ul>
     *
     * <p>{@code null} or unrecognized values fall back to {@code "reprompt"}.
     * Backwards-compatible: existing flows without this field keep the
     * pre-2026.2.31 behavior.
     *
     * @since 2026.2.31
     */
    public String onJudgeReject() {
        return data.onJudgeReject();
    }

    /**
     * Declarative tool-availability switch for an {@code aiQuestion} node
     * (§I.5 step 3 of the harness inversion). Three meaningful values:
     *
     * <ul>
     *   <li>{@code Boolean.TRUE} (or {@code null}) — tools are available
     *       to the LLM. Default — preserves pre-T11 behavior.</li>
     *   <li>{@code Boolean.FALSE} — tools are STRIPPED on this node. The
     *       executor builds the {@code ChatOptions} with an empty
     *       {@code toolCallbacks} list and {@code internalToolExecutionEnabled(false)}.
     *       Replaces the bilingual substring matcher in
     *       {@code TurAgentChatExecutor.forbidsToolCalls(...)}.</li>
     * </ul>
     *
     * <p>Used by flows that need to gate the LLM from preemptively calling
     * tools (canonical case: a coupon-capture node where the proposal tool
     * MUST wait until the slot is populated). When this field is set,
     * the legacy "NÃO CHAME NENHUMA TOOL" sentinel in
     * {@link #aiInstruction()} is no longer required — but it remains as
     * backwards-compat fallback in the executor until T16 deletes it.
     *
     * @since 2026.2.7
     */
    public Boolean toolsEnabled() {
        return data.toolsEnabled();
    }

    /**
     * Tools the LLM must have available on this node regardless of any
     * runtime pre-filtering (§IV.2 / T29). When the tool catalog grows
     * beyond {@code turing.genai.tool-prefilter.min-tools-threshold},
     * {@code TurToolPreFilterService} runs a Lucene/BM25 similarity match
     * between the user message and each tool's {@code prompts/tools/**.md}
     * description and keeps only the top-K. Tools listed here are <em>never</em>
     * dropped by that filter — use them on nodes that depend on a specific
     * tool being callable even when the user's message doesn't lexically
     * mention it.
     *
     * <p>Names match the {@code @Tool(name)} value (or the MCP/custom tool's
     * registered name). Empty list (default) means "no protected tools on
     * this node" — the filter is free to drop anything that scores below
     * the cutoff.
     *
     * @since 2026.3.1
     */
    public List<String> requiredTools() {
        return data.requiredTools() == null ? List.of() : data.requiredTools();
    }

    /**
     * Id of the {@code TurRoutine} this {@code scheduleAgent} node fires
     * (T48). Only meaningful when the node's type is {@code scheduleAgent};
     * ignored otherwise. The routine runs asynchronously via JMS — its
     * result lands in {@link #outputVariable()} through the slot bus and
     * the auto-resume listener walks the flow past this node when the slot
     * arrives.
     *
     * @since 2026.3.1
     */
    public String routineId() {
        return data.routineId();
    }

    /**
     * Per-node timeout override (ms) for a {@code scheduleAgent} node.
     * When the routine hasn't written its slot within this budget, the
     * engine takes the {@code timeout} outgoing edge on the next
     * walk-through (falls back to the first edge when none is wired). A
     * blank/null value falls back to the routine's own
     * {@code defaultTimeoutMs}.
     *
     * @since 2026.3.1
     */
    public Integer routineTimeoutMs() {
        return data.routineTimeoutMs();
    }

    /**
     * When {@link Boolean#TRUE TRUE} on a {@code functionCall} or
     * {@code scheduleAgent} node (T49), a tool/routine failure routes the
     * flow along the outgoing edge whose {@code sourceHandle} is
     * {@code "failure"} — authors wire an alternative path for graceful
     * recovery (apologize-then-retry, ask user to re-input, escalate to
     * handoff). When {@link Boolean#FALSE FALSE} or {@code null}, the
     * engine logs the failure and advances to the first outgoing edge
     * (legacy behavior preserved for existing flows).
     *
     * <p>Without a wired {@code "failure"} sourceHandle, the engine falls
     * back to the first outgoing edge — same lenient default the rest of
     * the flow ops use ({@link ChatFlowNode.SwitchOption} wildcard,
     * {@code scheduleAgent} timeout fallback). T50 will surface this in
     * the editor as a red try/catch edge.
     *
     * @since 2026.3.1
     */
    public Boolean continueOnFailure() {
        return data.continueOnFailure();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NodeData(
            String label,
            String type,
            String aiInstruction,
            String outputVariable,
            String validationRule,
            String functionName,
            String conditionExpression,
            String toolSource,
            String mcpServerId,
            String subFlowId,
            String subFlowName,
            String personaId,
            String switchVariable,
            List<SwitchOption> switchOptions,
            List<String> inlineOptions,
            Boolean overrideExistingValue,
            String slotName,
            String slotOperation,
            String slotValue,
            String onJudgeReject,
            Boolean toolsEnabled,
            List<String> requiredTools,
            String routineId,
            Integer routineTimeoutMs,
            Boolean continueOnFailure) {

        /**
         * Back-compat constructor for callers/tests predating
         * {@link #continueOnFailure()} (T49). Delegates to the canonical
         * constructor with {@code continueOnFailure = null}.
         */
        public NodeData(String label, String type, String aiInstruction, String outputVariable,
                String validationRule, String functionName, String conditionExpression, String toolSource,
                String mcpServerId, String subFlowId, String subFlowName, String personaId,
                String switchVariable, List<SwitchOption> switchOptions, List<String> inlineOptions,
                Boolean overrideExistingValue, String slotName, String slotOperation, String slotValue,
                String onJudgeReject, Boolean toolsEnabled, List<String> requiredTools,
                String routineId, Integer routineTimeoutMs) {
            this(label, type, aiInstruction, outputVariable, validationRule, functionName,
                    conditionExpression, toolSource, mcpServerId, subFlowId, subFlowName, personaId,
                    switchVariable, switchOptions, inlineOptions, overrideExistingValue, slotName,
                    slotOperation, slotValue, onJudgeReject, toolsEnabled, requiredTools, routineId,
                    routineTimeoutMs, null);
        }
    }

    /**
     * A single branch on a {@code switch} or {@code subFlowSwitch} node.
     * <ul>
     *   <li>{@code id} — the edge's {@code sourceHandle} on a {@code switch} node, and the option
     *       id the LLM classifier emits when no exact/substring match wins. Required.</li>
     *   <li>{@code label} — the human-readable text the engine matches user input against. Required.</li>
     *   <li>{@code subFlowId} — only used by {@code subFlowSwitch} (T47): when this option wins,
     *       the engine descends into the named sub-flow instead of routing to an outgoing edge.
     *       Optional / unused on plain {@code switch} nodes.</li>
     * </ul>
     *
     * @since 2026.2.7
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SwitchOption(String id, String label, String subFlowId) {

        /** Back-compat constructor for callers/tests that predate {@code subFlowId} (T47). */
        public SwitchOption(String id, String label) {
            this(id, label, null);
        }
    }
}

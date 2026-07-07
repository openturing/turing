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

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

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
public record ChatFlowNode(String id, String type, NodeData data) implements Serializable {

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
     * Declared fields of a {@code formCapture} node rendered as a NATIVE
     * multi-field form by the SDK (T107). When non-empty, the chat-flow
     * engine emits a structured {@code "form"} SSE event describing these
     * fields instead of relying on a single free-text answer into
     * {@link #outputVariable()}; the visitor fills the whole form at once and
     * the SDK posts every value back through {@code POST .../chat/form-submit},
     * which writes each field to its named slot. The node is considered
     * "satisfied" — and {@code walkThroughSatisfiedQuestions} skips past it —
     * once every {@code required != FALSE} field's slot holds a non-blank
     * value.
     *
     * <p>Empty list (the default) preserves the legacy single-field
     * {@code formCapture} behavior (regex-validated single-turn capture into
     * {@link #outputVariable()}).
     *
     * @since 2026.3.1
     */
    public List<FormField> formFields() {
        return data.formFields() == null ? List.of() : data.formFields();
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

    /**
     * Schema hint for a {@code planningStep} node (T108): a free-form
     * description of the JSON shape the LLM should emit into
     * {@link #outputVariable()} (the plan slot, conventionally {@code __plan}).
     * The engine doesn't parse this string — it's passed verbatim into the
     * planning prompt so the author can steer the structure (e.g.
     * {@code "List<{id: string, title: string, status: 'pending'|'done'}>"}).
     * Whatever the model returns is normalized to the canonical plan-item
     * array by {@code ChatFlowOps.generatePlan(...)} regardless of this hint,
     * so a blank value still yields a well-formed plan.
     *
     * @since 2026.3.1
     */
    public String planSchema() {
        return data.planSchema();
    }

    /**
     * Completion policy for an {@code iteratePlan} node (T108-2): what happens
     * to the in-flight plan item once its body sub-flow finishes (the engine
     * ascends back to the {@code iteratePlan} node). Two values:
     *
     * <ul>
     *   <li>{@code "mark_done"} (default, also for {@code null}/unrecognized) —
     *       flip the item's {@code status} to {@code "done"} in the plan slot;
     *       the plan is preserved as an auditable record of what ran.</li>
     *   <li>{@code "remove"} — drop the item from the plan entirely (queue-drain
     *       semantics).</li>
     * </ul>
     *
     * <p>Both modes guarantee forward progress, so the iteration always
     * terminates: each completed item is either flipped to {@code done} (and
     * thus skipped by the next "first pending" pick) or removed.
     *
     * @since 2026.3.1
     */
    public String completionMode() {
        return data.completionMode();
    }

    /**
     * Configuration for a {@code humanApproval} node (T119 / §IX.5.a): the
     * notification channel/target/template fired when the flow reaches the
     * node, the slot the operator's decision is written into, and the
     * timeout policy. {@code null} on every other node type. See
     * {@link HumanApprovalConfig}.
     *
     * @since 2026.3.1
     */
    public HumanApprovalConfig humanApproval() {
        return data.humanApproval();
    }

    /**
     * Per-node A/B experiment key (T72). When non-blank <em>and</em> the node
     * declares at least one {@link #nodeVariants() variant}, the engine swaps
     * this single node's behavior (currently its {@link #aiInstruction()}) for
     * the variant deterministically assigned to the conversation — granular
     * optimization without duplicating the whole flow. Blank/null (the
     * default) means the node behaves exactly as authored.
     *
     * <p>Independent namespace from the flow-level {@code experimentKey} (T67):
     * a node experiment is sticky on {@code conversationId + ":" +
     * nodeExperimentKey} so it can run inside any flow without interfering with
     * a concurrent flow-level split.
     *
     * @since 2026.3.1
     */
    public String nodeExperimentKey() {
        return data.nodeExperimentKey();
    }

    /**
     * The variant arms of this node's per-node A/B experiment (T72). Each
     * variant carries an optional {@code aiInstruction} override; a variant
     * with a blank override is the "control" arm and keeps the node's base
     * instruction. Empty list (the default) means no per-node experiment.
     *
     * @since 2026.3.1
     */
    public List<NodeVariant> nodeVariants() {
        return data.nodeVariants() == null ? List.of() : data.nodeVariants();
    }

    /**
     * T72 — returns the <em>effective</em> node for {@code conversationId}.
     * When this node declares a non-blank {@link #nodeExperimentKey()} and at
     * least one variant, a variant is deterministically picked (sticky per
     * conversation, weighted-hash identical to the flow-level
     * {@code assignVariant}) and a copy of this node is returned with the
     * variant's non-blank {@code aiInstruction} substituted in. Otherwise the
     * node is returned unchanged.
     *
     * <p>The node {@code id} and {@code type} are never touched — only the
     * node's behavior swaps — so graph traversal (edges, cursor advancement,
     * sub-flow descent) is byte-for-byte identical regardless of the chosen
     * arm. A control variant (blank override) also returns {@code this}.
     *
     * @since 2026.3.1
     */
    public ChatFlowNode resolveVariant(String conversationId) {
        NodeVariant chosen = pickNodeVariant(nodeVariants(), conversationId, nodeExperimentKey());
        if (chosen == null) {
            return this;
        }
        String override = chosen.aiInstruction();
        if (override == null || override.isBlank()) {
            // Control arm — keep the node's base instruction untouched.
            return this;
        }
        return new ChatFlowNode(id, type, data.withAiInstruction(override));
    }

    /**
     * T72 — structured per-turn trace for a node-level A/B experiment, sibling
     * of the flow-level {@code formatVariantTrace} (T75). Returns
     * {@link Optional#empty()} when the node is {@code null}, carries no
     * {@code nodeExperimentKey}, or resolves no variant. Format:
     * {@code nodeExperimentKey:variantLabel:nodeId}. Package-private for unit
     * testing.
     *
     * @since 2026.3.1
     */
    static Optional<String> formatNodeVariantTrace(ChatFlowNode node, String conversationId) {
        if (node == null) {
            return Optional.empty();
        }
        String key = node.nodeExperimentKey();
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        NodeVariant chosen = pickNodeVariant(node.nodeVariants(), conversationId, key);
        if (chosen == null) {
            return Optional.empty();
        }
        String label = (chosen.label() == null || chosen.label().isBlank())
                ? "(unset)" : chosen.label();
        return Optional.of(key + ":" + label + ":" + node.id());
    }

    /**
     * Deterministic weighted-random assignment of a conversation to one
     * variant of a per-node experiment (T72). Mirrors the flow-level
     * {@code TurChatFlowEngineService.assignVariant} weighting so per-node and
     * per-flow A/B share one definition of "sticky weighted bucketing":
     *
     * <ul>
     *   <li>Sum of weights &gt; 0 → variants are picked proportionally to
     *       their {@code weight} ({@code (1,1) ≡ (50,50)}).</li>
     *   <li>All weights null/0 → uniform distribution.</li>
     *   <li>A single variant with weight 0 inside a non-zero experiment is
     *       excluded — pause an arm without deleting it.</li>
     * </ul>
     *
     * <p>Returns {@code null} when there is no experiment to resolve (blank
     * {@code experimentKey} or empty variant list). Package-private for unit
     * testing.
     *
     * @since 2026.3.1
     */
    static NodeVariant pickNodeVariant(List<NodeVariant> variants, String conversationId,
            String experimentKey) {
        if (experimentKey == null || experimentKey.isBlank()
                || variants == null || variants.isEmpty()) {
            return null;
        }
        int totalWeight = 0;
        for (NodeVariant v : variants) {
            totalWeight += Math.max(0, v.weight() == null ? 0 : v.weight());
        }
        String convo = conversationId == null ? "" : conversationId;
        int hash = (convo + ":" + experimentKey).hashCode();
        if (totalWeight <= 0) {
            // Uniform fallback — every variant equally likely.
            return variants.get(Math.floorMod(hash, variants.size()));
        }
        int bucket = Math.floorMod(hash, totalWeight);
        int cumulative = 0;
        for (NodeVariant v : variants) {
            cumulative += Math.max(0, v.weight() == null ? 0 : v.weight());
            if (bucket < cumulative) {
                return v;
            }
        }
        // Defensive — unreachable when totalWeight > 0.
        return variants.get(variants.size() - 1);
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
            Boolean continueOnFailure,
            String nodeExperimentKey,
            List<NodeVariant> nodeVariants,
            List<FormField> formFields,
            String planSchema,
            String completionMode,
            HumanApprovalConfig humanApproval) implements Serializable {

        /**
         * Back-compat constructor for callers/tests predating the T119
         * {@code humanApproval} node config ({@code humanApproval}) — this is
         * the post-T108-2 / pre-T119 canonical signature. Delegates with
         * {@code humanApproval = null}.
         */
        public NodeData(String label, String type, String aiInstruction, String outputVariable,
                String validationRule, String functionName, String conditionExpression, String toolSource,
                String mcpServerId, String subFlowId, String subFlowName, String personaId,
                String switchVariable, List<SwitchOption> switchOptions, List<String> inlineOptions,
                Boolean overrideExistingValue, String slotName, String slotOperation, String slotValue,
                String onJudgeReject, Boolean toolsEnabled, List<String> requiredTools,
                String routineId, Integer routineTimeoutMs, Boolean continueOnFailure,
                String nodeExperimentKey, List<NodeVariant> nodeVariants, List<FormField> formFields,
                String planSchema, String completionMode) {
            this(label, type, aiInstruction, outputVariable, validationRule, functionName,
                    conditionExpression, toolSource, mcpServerId, subFlowId, subFlowName, personaId,
                    switchVariable, switchOptions, inlineOptions, overrideExistingValue, slotName,
                    slotOperation, slotValue, onJudgeReject, toolsEnabled, requiredTools, routineId,
                    routineTimeoutMs, continueOnFailure, nodeExperimentKey, nodeVariants, formFields,
                    planSchema, completionMode, null);
        }

        /**
         * Back-compat constructor for callers/tests predating the T108-2
         * {@code iteratePlan} completion mode ({@code completionMode}) — this is
         * the post-T108-1 / pre-T108-2 canonical signature. Delegates with
         * {@code completionMode = null}.
         */
        public NodeData(String label, String type, String aiInstruction, String outputVariable,
                String validationRule, String functionName, String conditionExpression, String toolSource,
                String mcpServerId, String subFlowId, String subFlowName, String personaId,
                String switchVariable, List<SwitchOption> switchOptions, List<String> inlineOptions,
                Boolean overrideExistingValue, String slotName, String slotOperation, String slotValue,
                String onJudgeReject, Boolean toolsEnabled, List<String> requiredTools,
                String routineId, Integer routineTimeoutMs, Boolean continueOnFailure,
                String nodeExperimentKey, List<NodeVariant> nodeVariants, List<FormField> formFields,
                String planSchema) {
            this(label, type, aiInstruction, outputVariable, validationRule, functionName,
                    conditionExpression, toolSource, mcpServerId, subFlowId, subFlowName, personaId,
                    switchVariable, switchOptions, inlineOptions, overrideExistingValue, slotName,
                    slotOperation, slotValue, onJudgeReject, toolsEnabled, requiredTools, routineId,
                    routineTimeoutMs, continueOnFailure, nodeExperimentKey, nodeVariants, formFields,
                    planSchema, null);
        }

        /**
         * Back-compat constructor for callers/tests predating the T108
         * {@code planningStep} schema hint ({@code planSchema}) — this is the
         * pre-T108 canonical signature. Delegates with {@code planSchema = null}.
         */
        public NodeData(String label, String type, String aiInstruction, String outputVariable,
                String validationRule, String functionName, String conditionExpression, String toolSource,
                String mcpServerId, String subFlowId, String subFlowName, String personaId,
                String switchVariable, List<SwitchOption> switchOptions, List<String> inlineOptions,
                Boolean overrideExistingValue, String slotName, String slotOperation, String slotValue,
                String onJudgeReject, Boolean toolsEnabled, List<String> requiredTools,
                String routineId, Integer routineTimeoutMs, Boolean continueOnFailure,
                String nodeExperimentKey, List<NodeVariant> nodeVariants, List<FormField> formFields) {
            this(label, type, aiInstruction, outputVariable, validationRule, functionName,
                    conditionExpression, toolSource, mcpServerId, subFlowId, subFlowName, personaId,
                    switchVariable, switchOptions, inlineOptions, overrideExistingValue, slotName,
                    slotOperation, slotValue, onJudgeReject, toolsEnabled, requiredTools, routineId,
                    routineTimeoutMs, continueOnFailure, nodeExperimentKey, nodeVariants, formFields, null);
        }

        /**
         * Back-compat constructor for callers/tests predating the T107
         * {@code formCapture} multi-field form ({@code formFields}). Delegates
         * to the canonical constructor with {@code formFields = null}.
         */
        public NodeData(String label, String type, String aiInstruction, String outputVariable,
                String validationRule, String functionName, String conditionExpression, String toolSource,
                String mcpServerId, String subFlowId, String subFlowName, String personaId,
                String switchVariable, List<SwitchOption> switchOptions, List<String> inlineOptions,
                Boolean overrideExistingValue, String slotName, String slotOperation, String slotValue,
                String onJudgeReject, Boolean toolsEnabled, List<String> requiredTools,
                String routineId, Integer routineTimeoutMs, Boolean continueOnFailure,
                String nodeExperimentKey, List<NodeVariant> nodeVariants) {
            this(label, type, aiInstruction, outputVariable, validationRule, functionName,
                    conditionExpression, toolSource, mcpServerId, subFlowId, subFlowName, personaId,
                    switchVariable, switchOptions, inlineOptions, overrideExistingValue, slotName,
                    slotOperation, slotValue, onJudgeReject, toolsEnabled, requiredTools, routineId,
                    routineTimeoutMs, continueOnFailure, nodeExperimentKey, nodeVariants, null, null);
        }

        /**
         * Back-compat constructor for callers/tests predating the T72 per-node
         * A/B fields ({@code nodeExperimentKey} / {@code nodeVariants}).
         * Delegates to the canonical constructor with all three trailing
         * fields set to {@code null}.
         */
        public NodeData(String label, String type, String aiInstruction, String outputVariable,
                String validationRule, String functionName, String conditionExpression, String toolSource,
                String mcpServerId, String subFlowId, String subFlowName, String personaId,
                String switchVariable, List<SwitchOption> switchOptions, List<String> inlineOptions,
                Boolean overrideExistingValue, String slotName, String slotOperation, String slotValue,
                String onJudgeReject, Boolean toolsEnabled, List<String> requiredTools,
                String routineId, Integer routineTimeoutMs, Boolean continueOnFailure) {
            this(label, type, aiInstruction, outputVariable, validationRule, functionName,
                    conditionExpression, toolSource, mcpServerId, subFlowId, subFlowName, personaId,
                    switchVariable, switchOptions, inlineOptions, overrideExistingValue, slotName,
                    slotOperation, slotValue, onJudgeReject, toolsEnabled, requiredTools, routineId,
                    routineTimeoutMs, continueOnFailure, null, null, null);
        }

        /**
         * Back-compat constructor for callers/tests predating
         * {@link #continueOnFailure()} (T49). Delegates with
         * {@code continueOnFailure = null}.
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

        /**
         * T72 — returns a copy of this {@code NodeData} with {@code aiInstruction}
         * replaced by {@code newInstruction}; every other field is preserved.
         * Used by {@link ChatFlowNode#resolveVariant(String)} to apply a chosen
         * per-node A/B variant without touching the node's identity or routing.
         */
        public NodeData withAiInstruction(String newInstruction) {
            return new NodeData(label, type, newInstruction, outputVariable, validationRule,
                    functionName, conditionExpression, toolSource, mcpServerId, subFlowId, subFlowName,
                    personaId, switchVariable, switchOptions, inlineOptions, overrideExistingValue,
                    slotName, slotOperation, slotValue, onJudgeReject, toolsEnabled, requiredTools,
                    routineId, routineTimeoutMs, continueOnFailure, nodeExperimentKey, nodeVariants,
                    formFields, planSchema, completionMode, humanApproval);
        }
    }

    /**
     * Runtime configuration of a {@code humanApproval} node (T119 / §IX.5.a).
     * When the engine walks onto the node it fires a notification on the chosen
     * {@code channel} to {@code target} (rendering {@code template} with
     * {@code {{slot}}} placeholders), persists a pending-approval record, and
     * parks the conversation. An operator's decision — delivered through
     * {@code POST /api/genai/approval/{token}} — is written into
     * {@code approvalSlot} and the flow advances. If no decision arrives within
     * {@code timeoutSeconds}, the {@code timeoutBehavior} resolves it
     * automatically.
     *
     * <ul>
     *   <li>{@code channel} — {@code slack} | {@code email} | {@code webhook}.
     *       Selects how the approver is notified.</li>
     *   <li>{@code target} — channel-specific destination: an e-mail address
     *       ({@code email}), a Slack incoming-webhook URL ({@code slack}), or
     *       the name of an admin-declared {@link com.viglet.turing.persistence.model.agent.TurChatWebhook}
     *       to fire ({@code webhook}).</li>
     *   <li>{@code template} — message body; {@code {{slot}}} placeholders are
     *       substituted with the conversation's slot values. A blank template
     *       falls back to a generic "approval required" message.</li>
     *   <li>{@code approvalSlot} — slot the decision lands in
     *       ({@code approve} | {@code reject} | {@code edit:<text>}). Downstream
     *       {@code condition}/{@code switch} nodes branch on it.</li>
     *   <li>{@code timeoutSeconds} — seconds to wait before auto-resolving;
     *       {@code null} or {@code <= 0} means wait indefinitely (no sweep).</li>
     *   <li>{@code timeoutBehavior} — {@code auto_reject} (default) or
     *       {@code auto_approve}: the decision written into {@code approvalSlot}
     *       when the timeout elapses.</li>
     * </ul>
     *
     * @since 2026.3.1
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HumanApprovalConfig(String channel, String target, String template,
            String approvalSlot, Integer timeoutSeconds, String timeoutBehavior)
            implements Serializable {
    }

    /**
     * A single field of a {@code formCapture} node's native multi-field form
     * (T107). Each field maps to one conversation slot.
     *
     * <ul>
     *   <li>{@code name} — the slot the captured value is written to. Required.</li>
     *   <li>{@code label} — human-readable field label rendered above the input.</li>
     *   <li>{@code type} — input widget hint for the SDK: {@code text} (default),
     *       {@code email}, {@code tel}, {@code number}, {@code date},
     *       {@code textarea}, or {@code select}. Free-form string — unknown
     *       values degrade to a plain text input on the SDK side.</li>
     *   <li>{@code required} — when {@code FALSE}, the field is optional and
     *       does not block the node's "satisfied" check. {@code null} (the
     *       default) is treated as required.</li>
     *   <li>{@code placeholder} — optional input placeholder text.</li>
     *   <li>{@code validationRule} — same vocabulary as
     *       {@link ChatFlowNode#validationRule()} ({@code email}, {@code phone},
     *       {@code cpf}, …) — surfaced to the SDK for client-side hinting.</li>
     *   <li>{@code options} — choice labels for a {@code select} field; ignored
     *       for other types. Never {@code null}.</li>
     * </ul>
     *
     * @since 2026.3.1
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FormField(String name, String label, String type, Boolean required,
            String placeholder, String validationRule, List<String> options)
            implements Serializable {

        public FormField {
            options = options == null ? List.of() : options;
        }

        /** {@code true} unless the author explicitly set {@code required = false}. */
        public boolean isRequired() {
            return !Boolean.FALSE.equals(required);
        }
    }

    /**
     * A single arm of a per-node A/B experiment (T72). When this variant is
     * the one assigned to a conversation, its non-blank {@code aiInstruction}
     * replaces the node's base instruction at prompt-build time; a blank
     * override marks the "control" arm (keep the base instruction).
     *
     * <ul>
     *   <li>{@code label} — human-readable arm name surfaced in the
     *       {@code [A/B Node Trace]} log and (future) analytics.</li>
     *   <li>{@code weight} — relative traffic weight (null/0 = excluded when
     *       other arms have weight; all-zero = uniform split).</li>
     *   <li>{@code aiInstruction} — the swapped instruction text, or blank to
     *       reuse the node's authored instruction.</li>
     * </ul>
     *
     * @since 2026.3.1
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NodeVariant(String label, Integer weight, String aiInstruction)
            implements Serializable {
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
    public record SwitchOption(String id, String label, String subFlowId)
            implements Serializable {

        /** Back-compat constructor for callers/tests that predate {@code subFlowId} (T47). */
        public SwitchOption(String id, String label) {
            this(id, label, null);
        }
    }
}

/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow.strategy;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.lucene.util.automaton.ByteRunAutomaton;
import org.apache.lucene.util.automaton.LevenshteinAutomata;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.MapAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.viglet.turing.genai.flow.ChatFlowEdge;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared helpers reused across {@link TurChatFlowGuardrailStrategy}
 * implementations: graph navigation, runtime variable I/O, regex
 * validation. All methods are static and stateless — utilities, not
 * services. Strategies pull whatever they need; the engine pulls the
 * graph-navigation pieces.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Slf4j
public final class ChatFlowOps {

    /**
     * Synthetic node id stamped onto state when the user abandons a flow
     * that has no real end node. Surfaced by the History page as
     * "Abandoned" status.
     */
    public static final String ABANDONED_NODE_ID = "__abandoned__";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> VARIABLES_TYPE = new TypeReference<>() {
    };

    /** Validation patterns understood by node {@code validationRule} fields. */
    private static final Map<String, Pattern> VALIDATION_PATTERNS = Map.ofEntries(
            Map.entry("email", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")),
            Map.entry("phone", Pattern.compile("\\+?[0-9][0-9 ()\\-]{6,}\\b")),
            Map.entry("url",   Pattern.compile("https?://[\\w.\\-/?#=&%+]+", Pattern.CASE_INSENSITIVE)),
            Map.entry("number",Pattern.compile("-?\\d+(?:[.,]\\d+)?")),
            Map.entry("date",  Pattern.compile("\\b\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}\\b")),
            // BR document formats — match canonical "###.###.###-##" and "##.###.###/####-##"
            // but also tolerate digits-only (formCapture nodes typically clean punctuation
            // client-side before sending). We don't run the mod-11 digit-check here yet;
            // the regex narrows the input enough that downstream code (or the strategy
            // re-asking on a "still wrong" hit) can reject obvious garbage. Full check
            // is a follow-up — see [[project_form_capture_digit_check]].
            Map.entry("cpf",   Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b")),
            Map.entry("cnpj",  Pattern.compile("\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b")),
            Map.entry("cep",   Pattern.compile("\\b\\d{5}-?\\d{3}\\b")));

    private ChatFlowOps() {
        // utility class — not instantiable
    }

    /**
     * True when {@code node} is an interactive question step — currently
     * {@code aiQuestion} (LLM-driven prompt + extraction) or
     * {@code formCapture} (deterministic input field with regex validation
     * via {@link ChatFlowNode#validationRule()}). Both pause the engine
     * waiting for a user reply, both collect into
     * {@link ChatFlowNode#outputVariable()}, and both expose chip suggestions
     * via {@link ChatFlowNode#inlineOptions()}; treating them as a single
     * "question-like" family in the auxiliary helpers (satisfied-question
     * skip walk, suggested-options surface) keeps behavior consistent so
     * authors can swap one for the other without losing a feature.
     *
     * <p>The difference between the two surfaces only at strategy time:
     * {@code aiQuestion} delegates extraction to the LLM (or the structured-
     * output strategy's JSON contract), while {@code formCapture} is
     * expected to reject anything the {@code validationRule} regex doesn't
     * match — no LLM extraction.
     *
     * @since 2026.2.7
     */
    public static boolean isQuestionLike(ChatFlowNode node) {
        if (node == null) return false;
        String type = node.type();
        return "aiQuestion".equals(type) || "formCapture".equals(type);
    }

    /**
     * {@code true} when {@code node} is a {@code formCapture} node that
     * declares at least one {@link ChatFlowNode.FormField} — i.e. a NATIVE
     * multi-field form (T107) rather than the legacy single-field
     * {@code formCapture} that collects one regex-validated answer into
     * {@link ChatFlowNode#outputVariable()}.
     *
     * @since 2026.3.1
     */
    public static boolean isNativeForm(ChatFlowNode node) {
        return node != null && "formCapture".equals(node.type())
                && !node.formFields().isEmpty();
    }

    /**
     * {@code true} when every {@link ChatFlowNode.FormField#isRequired()
     * required} field of a {@link #isNativeForm native form} node holds a
     * non-blank value in {@code variables}. Optional fields ({@code required =
     * false}) never block satisfaction. Returns {@code false} for nodes that
     * are not native forms — callers should gate on {@link #isNativeForm}
     * first.
     *
     * @since 2026.3.1
     */
    public static boolean isNativeFormSatisfied(ChatFlowNode node, Map<String, String> variables) {
        if (!isNativeForm(node)) {
            return false;
        }
        Map<String, String> vars = variables == null ? Map.of() : variables;
        for (ChatFlowNode.FormField field : node.formFields()) {
            if (field == null || !field.isRequired()) {
                continue;
            }
            String name = field.name();
            if (name == null || name.isBlank()) {
                continue;
            }
            String value = vars.get(name.trim());
            if (value == null || value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    // ─────────────────────────── System-prompt building ───────────────────────────

    /**
     * Base system-prompt addendum every guardrail strategy needs: an active-step
     * header, the node's goal / collect / validation / already-collected
     * variables, the next interactive node's goal (so the bot can chain
     * directly into the next question instead of waiting for the user to
     * "ping" again), and the conversational HARD RULES.
     * <p>
     * Strategies append their strategy-specific tail (the heuristic adds a hint
     * about validation; the structured-output strategy adds the JSON contract).
     * Returns an empty string when the node is terminal — once the flow ended,
     * the agent should chat freely.
     */
    public static String buildBaseAddendum(ChatFlowNode node, TurChatFlowState state, ChatFlowGraph graph) {
        return buildBaseAddendum(node, state, graph, true);
    }

    /**
     * Same as {@link #buildBaseAddendum(ChatFlowNode, TurChatFlowState, ChatFlowGraph)}
     * but with explicit control over the "Next step preview" hint at the end.
     *
     * <p>Set {@code includeNextStepHint = false} when the prompt is for the
     * REGEN path after a transparent walk: the LLM has just been asked to
     * "ask the CURRENT step's question" and showing it the next step's
     * instruction nudges it to ask THAT one instead — the classic
     * one-step-ahead chip-vs-question mismatch (chips render from the
     * current node, but the text matches the next node's aiInstruction).
     * The default true preserves the chaining benefit for the initial
     * pre-LLM call where the bot can capture + ask in one reply.
     */
    public static String buildBaseAddendum(ChatFlowNode node, TurChatFlowState state,
            ChatFlowGraph graph, boolean includeNextStepHint) {
        if (node == null || "end".equals(node.type())) {
            return "";
        }
        // Composed from the three split helpers so a single source of
        // truth covers both the cached path (via
        // {@link com.viglet.turing.genai.flow.TurChatFlowStaticPromptCache})
        // and any legacy / direct caller using this static entry point.
        return buildStaticAddendumHead(node)
                + buildVariablesLine(state)
                + buildStaticAddendumTail(node, graph, includeNextStepHint);
    }

    /**
     * T31 / §IV.5 — deterministic head of the base addendum: depends only
     * on {@code node}. Cached by
     * {@link com.viglet.turing.genai.flow.TurChatFlowStaticPromptCache#staticHead(String, String, ChatFlowNode)}
     * keyed by {@code (flowId, nodeId)} — flow saves evict the cache so
     * edits to a node's label / aiInstruction / outputVariable /
     * validationRule propagate on the next chat turn without restart.
     * Returns an empty string for null or terminal nodes so callers can
     * short-circuit the whole addendum at one place.
     */
    public static String buildStaticAddendumHead(ChatFlowNode node) {
        if (node == null || "end".equals(node.type())) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== ACTIVE FLOW STEP: ")
                .append(safe(node.label()))
                .append(" ===\n");
        if (node.aiInstruction() != null && !node.aiInstruction().isBlank()) {
            sb.append("Goal: ").append(node.aiInstruction().trim()).append('\n');
        }
        if (node.outputVariable() != null && !node.outputVariable().isBlank()) {
            sb.append("Collect: ").append(node.outputVariable().trim()).append('\n');
        }
        if (node.validationRule() != null && !node.validationRule().isBlank()
                && !"none".equalsIgnoreCase(node.validationRule())) {
            sb.append("Validate the collected value as: ")
                    .append(node.validationRule().trim()).append('\n');
        }
        return sb.toString();
    }

    /**
     * T31 / §IV.5 — dynamic part of the base addendum: the "Already
     * collected" line. Derived from the per-conversation
     * {@code variablesJson} so the value changes turn-to-turn — NOT
     * cacheable on {@code (flowId, nodeId)} alone. Returns an empty string
     * when no variables have been collected yet so callers can
     * concatenate unconditionally.
     */
    public static String buildVariablesLine(TurChatFlowState state) {
        Map<String, String> variables = readVariables(state);
        if (variables.isEmpty()) {
            return "";
        }
        return "Already collected: " + summarizeVariables(variables) + "\n";
    }

    /**
     * T31 / §IV.5 — deterministic tail of the base addendum: the next-step
     * preview hint (depends on the graph) and the HARD_RULES constant.
     * Depends only on {@code (node, graph, includeNextStepHint)}; cached by
     * {@link com.viglet.turing.genai.flow.TurChatFlowStaticPromptCache#staticTail(String, String, boolean, ChatFlowNode, ChatFlowGraph)}
     * keyed by {@code (flowId, nodeId, includeNextStepHint)}. Edges /
     * neighboring node aiInstruction changes evict via the flow save hook.
     */
    public static String buildStaticAddendumTail(ChatFlowNode node, ChatFlowGraph graph,
            boolean includeNextStepHint) {
        if (node == null || "end".equals(node.type())) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (includeNextStepHint) {
            appendNextStepHint(sb, graph, node);
        }
        sb.append(HARD_RULES);
        return sb.toString();
    }

    /**
     * Maximum length of a single slot value rendered in the "Already collected"
     * block of the system prompt. Beyond this, the value is replaced by a
     * short marker so the LLM can SEE that the slot exists (and decide whether
     * to call a tool that uses it) without being tempted to echo a 1.5 KB JSON
     * blob verbatim in its reply — the bug that produced the raw {@code
     * programas_match} array dumped into chat after a card-driven tool run.
     */
    private static final int MAX_INLINED_VARIABLE_LENGTH = 120;

    /**
     * Renders the variable map for the {@code Already collected:} line of the
     * system prompt, replacing each value whose serialized form exceeds
     * {@link #MAX_INLINED_VARIABLE_LENGTH} with an opaque marker that conveys
     * "this slot is populated, but its contents are large/structured and
     * managed by a downstream UI component (cards, timeline, etc.) — do NOT
     * paste it back in the reply".
     *
     * <p>Recognizes JSON-shaped values (start with {@code {} or {@code [}) so
     * the marker reads {@code (json, N chars)} instead of just
     * {@code (truncated)}. Plain text values that happen to be long get the
     * same truncation treatment.
     */
    private static String summarizeVariables(Map<String, String> variables) {
        LinkedHashMap<String, String> rendered = new LinkedHashMap<>(variables.size());
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue();
            if (value == null) {
                rendered.put(entry.getKey(), "");
                continue;
            }
            if (value.length() <= MAX_INLINED_VARIABLE_LENGTH) {
                rendered.put(entry.getKey(), value);
                continue;
            }
            String trimmed = value.trim();
            boolean json = trimmed.startsWith("{") || trimmed.startsWith("[");
            String marker = json
                    ? "(json, " + value.length() + " chars — content managed by UI/tool, do not quote)"
                    : "(text, " + value.length() + " chars — truncated, do not quote)";
            rendered.put(entry.getKey(), marker);
        }
        return rendered.toString();
    }

    /**
     * Appends a "NEXT STEP" hint pointing at the next interactive node so the
     * chat LLM can chain from "user gave a value" straight into "next question"
     * in a single reply. Without this hint, the bot collects a value and stops,
     * forcing the user to send a "ping" between steps.
     * <p>
     * Three cases:
     * <ol>
     *   <li>Single edge to a normal interactive node → show its goal.</li>
     *   <li>Single edge to a {@code condition} node → show BOTH branch goals
     *       prefixed with the {@code yes}/{@code no} handle, so the bot can
     *       pick the right next question based on the user's answer.</li>
     *   <li>Anything else (multiple edges, dead end, end node) → no hint;
     *       the bot will just collect the value.</li>
     * </ol>
     */
    private static void appendNextStepHint(StringBuilder sb, ChatFlowGraph graph, ChatFlowNode current) {
        if (graph == null || current == null) {
            return;
        }
        List<ChatFlowEdge> outgoing = graph.outgoingEdges(current.id());
        if (outgoing.size() != 1) {
            return;
        }
        Optional<ChatFlowNode> targetOpt = graph.nodeById(outgoing.get(0).target());
        if (targetOpt.isEmpty()) {
            return;
        }
        ChatFlowNode target = targetOpt.get();
        String type = target.type();
        if ("end".equals(type)) {
            return;
        }
        if ("condition".equals(type)) {
            appendConditionalNextSteps(sb, graph, target);
            return;
        }
        if ("switch".equals(type)) {
            appendSwitchNextSteps(sb, graph, target);
            return;
        }
        if (target.aiInstruction() == null || target.aiInstruction().isBlank()) {
            return;
        }
        sb.append("Next step preview (for chaining your reply): ")
                .append(target.aiInstruction().trim())
                .append("\n");
    }

    /**
     * For switch targets, expands each option's downstream so the bot knows what to ask after the
     * user's answer routes the flow. Handles are option ids (semantically opaque), so we look up
     * each edge's matching {@code SwitchOption} on the source node and surface the human-readable
     * label instead — that's what the model can sensibly reason about when chaining.
     */
    private static void appendSwitchNextSteps(StringBuilder sb, ChatFlowGraph graph, ChatFlowNode switchNode) {
        List<ChatFlowEdge> branches = graph.outgoingEdges(switchNode.id());
        if (branches.isEmpty()) {
            return;
        }
        Map<String, String> labelByHandle = new LinkedHashMap<>();
        for (ChatFlowNode.SwitchOption option : switchNode.switchOptions()) {
            if (option != null && option.id() != null && option.label() != null) {
                labelByHandle.put(option.id(), option.label());
            }
        }
        StringBuilder hint = new StringBuilder();
        boolean any = false;
        for (ChatFlowEdge edge : branches) {
            Optional<ChatFlowNode> branchOpt = graph.nodeById(edge.target());
            if (branchOpt.isEmpty()) {
                continue;
            }
            ChatFlowNode branch = branchOpt.get();
            if ("end".equals(branch.type())) {
                continue;
            }
            String instruction = branch.aiInstruction();
            if (instruction == null || instruction.isBlank()) {
                continue;
            }
            String handle = edge.sourceHandle();
            String label = handle == null ? null : labelByHandle.get(handle);
            String displayBranch = label != null && !label.isBlank()
                    ? label
                    : handle == null || handle.isBlank() ? "default" : handle;
            hint.append("  - If '").append(displayBranch).append("' branch: ")
                    .append(instruction.trim()).append("\n");
            any = true;
        }
        if (!any) {
            return;
        }
        String varName = switchNode.switchVariable() == null || switchNode.switchVariable().isBlank()
                ? "the user's answer"
                : switchNode.switchVariable().trim();
        sb.append("Next step preview (the flow branches on `")
                .append(varName)
                .append("` — pick the matching question):\n")
                .append(hint);
    }

    /**
     * For condition targets, expands each {@code yes}/{@code no} branch so the
     * bot knows what to ask after the user's answer routes the flow. The
     * condition expression is included so the LLM can pick the branch
     * deterministically (the engine will evaluate it for state, but the LLM
     * needs to know which question to chain into in its REPLY).
     */
    private static void appendConditionalNextSteps(StringBuilder sb, ChatFlowGraph graph, ChatFlowNode condition) {
        List<ChatFlowEdge> branches = graph.outgoingEdges(condition.id());
        if (branches.isEmpty()) {
            return;
        }
        StringBuilder hint = new StringBuilder();
        boolean any = false;
        for (ChatFlowEdge edge : branches) {
            Optional<ChatFlowNode> branchOpt = graph.nodeById(edge.target());
            if (branchOpt.isEmpty()) {
                continue;
            }
            ChatFlowNode branch = branchOpt.get();
            if ("end".equals(branch.type())) {
                continue;
            }
            String instruction = branch.aiInstruction();
            if (instruction == null || instruction.isBlank()) {
                continue;
            }
            String handle = edge.sourceHandle() == null || edge.sourceHandle().isBlank()
                    ? "default"
                    : edge.sourceHandle();
            hint.append("  - If '").append(handle).append("' branch: ")
                    .append(instruction.trim()).append("\n");
            any = true;
        }
        if (!any) {
            return;
        }
        String expr = condition.conditionExpression() == null || condition.conditionExpression().isBlank()
                ? "the user's answer"
                : condition.conditionExpression().trim();
        sb.append("Next step preview (the flow branches on `")
                .append(expr)
                .append("` — pick the matching question):\n")
                .append(hint);
    }

    /**
     * Conversational rules every guardrail strategy enforces. Kept in a
     * single place so wording fixes (e.g. politeness ≠ abandonment, no
     * improvising the next step) propagate to all three strategies at once.
     */
    private static final String HARD_RULES = """

            Hard rules (the Goal above is non-negotiable):
            - Ask EXACTLY the question implied by Goal. Do NOT improvise the next
              step ahead of time, do NOT volunteer information not in Goal.
            - Stay strictly on Goal. If the user deviates, briefly redirect them
              back to this step inside your reply.
            - While this flow step is active, your role is the Goal — nothing else.
              Do NOT mention the agent's general purpose, product, persona, or
              capabilities. Do NOT say "minha função é...", "I'm here to help with
              X", "as an assistant for...". Drop the meta-talk and engage with the
              Goal directly. The user already knows what kind of agent they are
              talking to.
            - Accept short, bare answers as valid. If you asked for a name and the
              user replies with just "Alexandre", that IS the name — acknowledge
              it and IMMEDIATELY chain into the "Next step preview" question (if
              one is shown above). Never close the conversation with "how can I
              help?" between steps; always either ask the current question OR
              the next one.
            - Politeness markers in isolation — "obrigado", "valeu", "ok",
              "thanks", "thank you", "got it" — are NEVER abandonment; they
              acknowledge the previous message. Treat them as on-topic and
              continue from where you were.
            - Reply ONLY in the language the user used.
            """;

    // ─────────────────────────── Graph navigation ───────────────────────────

    /** Returns the node currently active in the conversation, if resolvable. */
    public static Optional<ChatFlowNode> currentNode(TurChatFlowState state, ChatFlowGraph graph) {
        if (state == null || graph == null) {
            return Optional.empty();
        }
        return graph.nodeById(state.getCurrentNodeId());
    }

    /** First node reached by following the START node's first outgoing edge. */
    public static Optional<ChatFlowNode> firstInteractiveNode(ChatFlowGraph graph, ChatFlowNode start) {
        List<ChatFlowEdge> outgoing = graph.outgoingEdges(start.id());
        if (outgoing.isEmpty()) {
            return Optional.empty();
        }
        return graph.nodeById(outgoing.get(0).target());
    }

    /**
     * Sets {@code state.currentNodeId} to the graph's end node id, or to
     * {@link #ABANDONED_NODE_ID} when the graph has no end node. Used to
     * end a flow gracefully when the user abandons it.
     */
    public static void transitionToEnd(TurChatFlowState state, ChatFlowGraph graph) {
        Optional<ChatFlowNode> endNode = graph.nodes().stream()
                .filter(n -> "end".equals(n.type()))
                .findFirst();
        state.setCurrentNodeId(endNode.map(ChatFlowNode::id).orElse(ABANDONED_NODE_ID));
    }

    /**
     * Sets {@code state.currentNodeId} to the target of the first outgoing
     * edge of {@code current}, or leaves it unchanged when no usable edge
     * exists.
     *
     * @return the new current node id (whether or not it changed).
     */
    public static String advanceToFirstEdge(TurChatFlowState state,
            ChatFlowGraph graph,
            ChatFlowNode current) {
        List<ChatFlowEdge> outgoing = graph.outgoingEdges(current.id());
        if (outgoing.isEmpty()) {
            log.info("[FlowOps] Node '{}' has no outgoing edges — staying", current.id());
            return state.getCurrentNodeId();
        }
        ChatFlowEdge nextEdge = outgoing.get(0);
        Optional<ChatFlowNode> targetNode = graph.nodeById(nextEdge.target());
        if (targetNode.isEmpty()) {
            log.warn("[FlowOps] Edge '{}' points at missing node '{}'",
                    nextEdge.id(), nextEdge.target());
            return state.getCurrentNodeId();
        }
        state.setCurrentNodeId(targetNode.get().id());
        return targetNode.get().id();
    }

    /**
     * Outgoing-edge {@code sourceHandle} the engine reads to route the
     * {@code continueOnFailure} branch of a {@code functionCall} or
     * {@code scheduleAgent} node (T49). Visualised as a red try/catch edge
     * by the editor in T50.
     *
     * @since 2026.3.1
     */
    public static final String FAILURE_HANDLE = "failure";

    /**
     * Routes {@code state} along the {@link #FAILURE_HANDLE failure}-handled
     * outgoing edge of {@code current}; falls back to
     * {@link #advanceToFirstEdge} when no edge is wired to the failure
     * handle. Used by T49 — node-level failure-recovery branching for
     * {@code functionCall} and {@code scheduleAgent} nodes that opt in via
     * {@link ChatFlowNode#continueOnFailure()}.
     *
     * @return the new current node id (whether or not it changed).
     * @since 2026.3.1
     */
    public static String advanceOnFailure(TurChatFlowState state,
            ChatFlowGraph graph,
            ChatFlowNode current) {
        List<ChatFlowEdge> outgoing = graph.outgoingEdges(current.id());
        for (ChatFlowEdge edge : outgoing) {
            if (!FAILURE_HANDLE.equalsIgnoreCase(edge.sourceHandle())) {
                continue;
            }
            Optional<ChatFlowNode> targetNode = graph.nodeById(edge.target());
            if (targetNode.isPresent()) {
                state.setCurrentNodeId(targetNode.get().id());
                log.info("[FlowOps] Node '{}' failure path → '{}'",
                        current.id(), targetNode.get().id());
                return targetNode.get().id();
            }
            log.warn("[FlowOps] Failure edge from '{}' points at missing node '{}'",
                    current.id(), edge.target());
        }
        log.info("[FlowOps] Node '{}' continueOnFailure=true but no '{}' edge wired — falling back to first outgoing edge",
                current.id(), FAILURE_HANDLE);
        return advanceToFirstEdge(state, graph, current);
    }

    /** Whether {@code nodeId} represents a terminal state (real end or abandon). */
    public static boolean isTerminalNodeId(String nodeId, ChatFlowGraph graph) {
        if (ABANDONED_NODE_ID.equals(nodeId)) {
            return true;
        }
        return graph.nodeById(nodeId).map(n -> "end".equals(n.type())).orElse(false);
    }

    // ─────────────────────────── Condition walker ───────────────────────────

    /**
     * Hard cap on consecutive condition hops in {@link #walkThroughConditions}.
     * Authored flows never need this many — the cap exists purely to make a
     * pathological cycle non-fatal.
     */
    private static final int CONDITION_WALK_MAX = 8;

    /**
     * Walks the state through any chain of {@code condition} nodes, evaluating
     * each {@code conditionExpression} and following the matching
     * {@code "yes"} / {@code "no"} edge. Returns when the state lands on a
     * non-condition node, when the chain dead-ends, or when the safety cap is
     * hit. Mutates {@code state.currentNodeId}.
     * <p>
     * Conditions are transparent to the chat — the LLM never sees them as a
     * "Goal", because they have none. The engine resolves them between turns.
     */
    public static void walkThroughConditions(TurChatFlowState state,
            ChatFlowGraph graph,
            ChatModel auxModel) {
        if (state == null || graph == null) {
            return;
        }
        for (int i = 0; i < CONDITION_WALK_MAX; i++) {
            Optional<ChatFlowNode> currentOpt = currentNode(state, graph);
            if (currentOpt.isEmpty()) {
                return;
            }
            ChatFlowNode current = currentOpt.get();
            if (!"condition".equals(current.type())) {
                return;
            }
            Map<String, String> variables = readVariables(state);
            boolean truthy = evaluateCondition(auxModel, current.conditionExpression(), variables);
            String wantedHandle = truthy ? "yes" : "no";
            ChatFlowEdge edge = pickConditionEdge(graph, current.id(), wantedHandle);
            if (edge == null) {
                log.warn("[FlowOps] Condition '{}' has no '{}' branch — staying", current.id(), wantedHandle);
                return;
            }
            Optional<ChatFlowNode> target = graph.nodeById(edge.target());
            if (target.isEmpty()) {
                log.warn("[FlowOps] Condition edge '{}' points at missing node '{}'",
                        edge.id(), edge.target());
                return;
            }
            log.info("[FlowOps] Condition '{}' = {} → '{}'",
                    current.id(), truthy, target.get().id());
            state.setCurrentNodeId(target.get().id());
        }
        log.warn("[FlowOps] walkThroughConditions hit the {}-hop safety cap", CONDITION_WALK_MAX);
    }

    // ──────────────────────────── Satisfied-question walker ───────────────────────────

    /**
     * Hard cap on consecutive question-skip hops in
     * {@link #walkThroughSatisfiedQuestions}. A flow that strings more than
     * this many already-filled questions in a row is almost certainly cyclic;
     * the cap keeps the path non-fatal.
     */
    private static final int QUESTION_SKIP_WALK_MAX = 16;

    /**
     * Walks the state past consecutive {@code aiQuestion} nodes whose
     * {@code outputVariable} slot is already filled with a non-blank value
     * in the flow's variables map — provided the node does NOT have
     * {@code overrideExistingValue = true}.
     *
     * <p>The skip is a transparent transition: no LLM round-trip, no
     * persisted advance signal. Used so a flow that re-enters a previously
     * answered question (loop back from a condition, re-trigger, …) doesn't
     * re-ask the user for data they already provided.
     *
     * @since 2026.2.7
     */
    public static void walkThroughSatisfiedQuestions(TurChatFlowState state, ChatFlowGraph graph) {
        if (state == null || graph == null) {
            return;
        }
        for (int i = 0; i < QUESTION_SKIP_WALK_MAX; i++) {
            Optional<ChatFlowNode> currentOpt = currentNode(state, graph);
            if (currentOpt.isEmpty()) {
                return;
            }
            ChatFlowNode current = currentOpt.get();
            if (!isQuestionLike(current)) {
                return;
            }
            // Author opted in to always asking — never skip.
            if (Boolean.TRUE.equals(current.overrideExistingValue())) {
                return;
            }
            Map<String, String> variables = readVariables(state);
            // T107 — a native multi-field formCapture is satisfied (and thus
            // skippable) when every required field's slot is filled; its own
            // outputVariable is not what advances it.
            if (isNativeForm(current)) {
                if (!isNativeFormSatisfied(current, variables)) {
                    return;
                }
            } else {
                String outVar = current.outputVariable();
                if (outVar == null || outVar.isBlank()) {
                    return;
                }
                String existing = variables.get(outVar.trim());
                if (existing == null || existing.isBlank()) {
                    return;
                }
            }
            List<ChatFlowEdge> outgoing = graph.outgoingEdges(current.id());
            if (outgoing.isEmpty()) {
                return;
            }
            Optional<ChatFlowNode> target = graph.nodeById(outgoing.get(0).target());
            if (target.isEmpty()) {
                return;
            }
            log.info("[FlowOps] Skipping satisfied question '{}' → '{}' ({})",
                    current.id(), target.get().id(),
                    isNativeForm(current) ? "form fields filled"
                            : "slot '" + current.outputVariable() + "' already filled");
            state.setCurrentNodeId(target.get().id());
        }
        log.warn("[FlowOps] walkThroughSatisfiedQuestions hit the {}-hop safety cap",
                QUESTION_SKIP_WALK_MAX);
    }

    // ─────────────────────────── Slot node ───────────────────────────

    /**
     * Like {@link #applySlotNode(TurChatFlowState, ChatFlowNode)} but
     * interprets {@code {{slotName}}} references inside the node's
     * {@code slotValue} as live reads from the conversation's variable map
     * — performed once at write time, so the persisted slot value is fully
     * resolved (consumers never have to interpolate).
     *
     * <p>This is the value-add of the dedicated {@code writeSlot} node type
     * over a plain {@code slot SET}: the latter writes the raw literal
     * (including any {@code {{...}}} placeholders), which pushed
     * interpolation onto every consumer. {@code writeSlot} pulls it server-
     * side so reads are simple lookups.
     *
     * <p>Operation is always SET — {@code writeSlot} doesn't expose DELETE
     * (use a {@code slot} node for that). Unknown slot references resolve
     * to the empty string, mirroring the SDK's interpolator on the React
     * side so behavior is consistent across the boundary.
     *
     * @since 2026.2.7
     */
    public static void applyWriteSlotNode(TurChatFlowState state, ChatFlowNode node) {
        if (state == null || node == null) return;
        String slotName = node.slotName();
        if (slotName == null || slotName.isBlank()) {
            log.warn("[FlowOps] writeSlot node '{}' has no slotName — skipping", node.id());
            return;
        }
        String key = slotName.trim();
        Map<String, String> variables = readVariables(state);
        String existing = variables.get(key);
        boolean alreadyFilled = existing != null && !existing.isBlank();
        boolean override = Boolean.TRUE.equals(node.overrideExistingValue());
        if (alreadyFilled && !override) {
            log.info("[FlowOps] writeSlot node '{}' on '{}' skipped (override=false, value present)",
                    node.id(), key);
            return;
        }
        String raw = node.slotValue() == null ? "" : node.slotValue();
        String resolved = interpolateVariables(raw, variables);
        variables.put(key, resolved);
        log.info("[FlowOps] writeSlot node '{}' wrote '{}' ({} chars after interpolation) on conv '{}'",
                node.id(), key, resolved.length(), state.getConversationId());
        writeVariables(state, variables);
    }

    /**
     * Substitutes {@code {{name}}} occurrences with the corresponding entry
     * in {@code variables}. Unknown / blank values resolve to the empty
     * string — same forgiving contract as the React-side interpolator the
     * portal uses on read.
     */
    public static String interpolateVariables(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty()) return "";
        // Each match is replaced with the live value (or "" when missing).
        // No nested-template support — a single pass keeps the contract
        // predictable; if authors need composition they can chain writeSlot
        // nodes.
        java.util.regex.Matcher m = INTERPOLATION_PATTERN.matcher(template);
        StringBuilder out = new StringBuilder(template.length());
        while (m.find()) {
            String name = m.group(1).trim();
            String value = variables == null ? null : variables.get(name);
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static final Pattern INTERPOLATION_PATTERN = Pattern.compile("\\{\\{\\s*([^}\\s][^}]*?)\\s*}}");

    /**
     * Applies a {@code slot} node's effect to the conversation state.
     * <ul>
     *   <li>{@code SET}: writes {@link ChatFlowNode#slotValue()} into
     *       {@link ChatFlowNode#slotName()}. When
     *       {@link ChatFlowNode#overrideExistingValue()} is {@code false} or
     *       absent and the slot already has a non-blank value, the call is a
     *       no-op so curated values are preserved. {@code null} {@code slotValue}
     *       is coerced to the empty string so operators can use SET to seed an
     *       empty slot explicitly.</li>
     *   <li>{@code DELETE}: removes {@link ChatFlowNode#slotName()} from the
     *       state. Downstream Switch / aiQuestion nodes will see an empty
     *       slot — the natural way to force a re-capture.</li>
     * </ul>
     * Unknown / blank operations are treated as no-ops (defensive: a
     * mistyped export shouldn't blow up the engine). Persists the change
     * by calling {@link #writeVariables} when (and only when) the variable
     * map was actually mutated.
     *
     * @since 2026.2.7
     */
    public static void applySlotNode(TurChatFlowState state, ChatFlowNode node) {
        if (state == null || node == null) {
            return;
        }
        String slotName = node.slotName();
        if (slotName == null || slotName.isBlank()) {
            log.warn("[FlowOps] Slot node '{}' has no slotName — skipping", node.id());
            return;
        }
        String key = slotName.trim();
        String operation = node.slotOperation() == null
                ? "SET"
                : node.slotOperation().trim().toUpperCase();
        Map<String, String> variables = readVariables(state);

        if ("DELETE".equals(operation)) {
            if (variables.remove(key) != null) {
                log.info("[FlowOps] Slot node '{}' removed slot '{}' on conversation '{}'",
                        node.id(), key, state.getConversationId());
                writeVariables(state, variables);
            }
            return;
        }

        if (!"SET".equals(operation)) {
            log.warn("[FlowOps] Slot node '{}' has unknown operation '{}' — skipping",
                    node.id(), node.slotOperation());
            return;
        }

        String existing = variables.get(key);
        boolean alreadyFilled = existing != null && !existing.isBlank();
        boolean override = Boolean.TRUE.equals(node.overrideExistingValue());
        if (alreadyFilled && !override) {
            log.info("[FlowOps] Slot node '{}' SET on '{}' skipped (override=false, value present)",
                    node.id(), key);
            return;
        }
        String value = node.slotValue() == null ? "" : node.slotValue();
        variables.put(key, value);
        log.info("[FlowOps] Slot node '{}' SET '{}' on conversation '{}'",
                node.id(), key, state.getConversationId());
        writeVariables(state, variables);
    }

    /**
     * Picks an outgoing edge from {@code sourceId} whose {@code sourceHandle}
     * matches {@code handle} (case-insensitive, null/blank treated as a
     * wildcard fallback when no exact match exists).
     */
    private static ChatFlowEdge pickConditionEdge(ChatFlowGraph graph, String sourceId, String handle) {
        List<ChatFlowEdge> outgoing = graph.outgoingEdges(sourceId);
        ChatFlowEdge wildcard = null;
        for (ChatFlowEdge edge : outgoing) {
            String h = edge.sourceHandle();
            if (h != null && h.equalsIgnoreCase(handle)) {
                return edge;
            }
            if ((h == null || h.isBlank()) && wildcard == null) {
                wildcard = edge;
            }
        }
        return wildcard;
    }

    // ─────────────────────────── Planning step (T108) ───────────────────────────

    /**
     * Reserved slot a {@code planningStep} node writes to (and an
     * {@code iteratePlan} node reads from) when the author leaves
     * {@code outputVariable} blank. Conventionally surfaced in the chat-flow
     * graph and the slot bus as {@code __plan}.
     *
     * @since 2026.3.1
     */
    public static final String DEFAULT_PLAN_SLOT = "__plan";

    /** Hard cap on the number of plan items a planning step will keep. */
    private static final int MAX_PLAN_ITEMS = 24;

    private static final TypeReference<List<Map<String, Object>>> PLAN_LIST_TYPE = new TypeReference<>() {
    };

    /**
     * A single item of a {@code planningStep} plan (T108): a typed TODO entry
     * the LLM emitted and an {@code iteratePlan} node walks one by one.
     *
     * <ul>
     *   <li>{@code id} — stable identifier (sequential {@code "1"}, {@code "2"},
     *       … when the model omits one). Used by {@code iteratePlan} to mark
     *       the right item done.</li>
     *   <li>{@code title} — human-readable step text the sub-flow executes.</li>
     *   <li>{@code status} — {@code "pending"} or {@code "done"}; anything else
     *       normalizes to {@code "pending"}.</li>
     * </ul>
     *
     * @since 2026.3.1
     */
    public record PlanItem(String id, String title, String status) {
        public static final String PENDING = "pending";
        public static final String DONE = "done";

        /** {@code true} when this item still needs to be executed. */
        public boolean isPending() {
            return !DONE.equalsIgnoreCase(status);
        }
    }

    /**
     * Applies a {@code planningStep} node: asks {@code auxModel} to decompose
     * the user's goal into a typed TODO list and writes the resulting JSON
     * array into the node's plan slot ({@link ChatFlowNode#outputVariable()},
     * defaulting to {@link #DEFAULT_PLAN_SLOT}). The node is transparent — no
     * user round-trip — and the engine advances on its single outgoing edge.
     *
     * <p>Honors {@link ChatFlowNode#overrideExistingValue()}: when the plan
     * slot already holds a non-blank value and override is not set, the call
     * is a no-op so a plan computed on a previous turn (or seeded by an
     * upstream node) is preserved across re-entries. When {@code auxModel} is
     * {@code null} or the model fails, an empty array ({@code []}) is written
     * so downstream {@code iteratePlan} nodes see a well-formed — if empty —
     * plan instead of a missing slot.
     *
     * @since 2026.3.1
     */
    public static void applyPlanningStepNode(TurChatFlowState state, ChatFlowNode node, ChatModel auxModel) {
        if (state == null || node == null) {
            return;
        }
        String slot = node.outputVariable();
        String key = (slot == null || slot.isBlank()) ? DEFAULT_PLAN_SLOT : slot.trim();
        Map<String, String> variables = readVariables(state);
        String existing = variables.get(key);
        boolean alreadyFilled = existing != null && !existing.isBlank();
        boolean override = Boolean.TRUE.equals(node.overrideExistingValue());
        if (alreadyFilled && !override) {
            log.info("[FlowOps] planningStep node '{}' on '{}' skipped (override=false, plan present)",
                    node.id(), key);
            return;
        }
        List<PlanItem> plan = generatePlan(auxModel, node, variables);
        String planJson = serializePlan(plan);
        variables.put(key, planJson);
        log.info("[FlowOps] planningStep node '{}' wrote {} item(s) into slot '{}' on conv '{}'",
                node.id(), plan.size(), key, state.getConversationId());
        writeVariables(state, variables);
    }

    /**
     * Asks {@code auxModel} to break the goal carried by the node's
     * {@link ChatFlowNode#aiInstruction()} (plus the already-collected
     * variables for context) into 3–6 concrete steps, then normalizes the
     * model's reply into a canonical {@link PlanItem} list. Returns an empty
     * list when {@code auxModel} is {@code null}, the instruction is blank, or
     * the model returns nothing parseable — callers should treat an empty plan
     * as "nothing to iterate".
     */
    public static List<PlanItem> generatePlan(ChatModel auxModel, ChatFlowNode node,
            Map<String, String> variables) {
        if (auxModel == null) {
            log.info("[FlowOps] planningStep '{}' has no aux model — emitting empty plan",
                    node == null ? "?" : node.id());
            return List.of();
        }
        String instruction = node == null ? null : node.aiInstruction();
        String goal = (instruction == null || instruction.isBlank())
                ? "Decompose the user's current goal into concrete, ordered steps."
                : instruction.trim();
        String schemaHint = node == null ? null : node.planSchema();
        String varsJson = serializeVariables(variables);
        String sys = """
                You are a planning assistant for a chat-flow engine. Break the goal into a SHORT
                ordered list of 3-6 concrete, actionable steps.

                Reply with ONLY a JSON array — no prose, no markdown fences. Each element is an
                object with exactly these keys:
                  - "id": a short stable string id ("1", "2", ...)
                  - "title": the step text (imperative, one line)
                  - "status": always "pending"

                Example:
                [{"id":"1","title":"Gather the user's requirements","status":"pending"},
                 {"id":"2","title":"Draft the proposal","status":"pending"}]

                Keep titles concise. Do NOT invent steps unrelated to the goal. Reply in the
                language of the goal.
                """;
        StringBuilder user = new StringBuilder();
        user.append("Goal: ").append(goal).append('\n');
        if (schemaHint != null && !schemaHint.isBlank()) {
            user.append("Desired item shape: ").append(schemaHint.trim()).append('\n');
        }
        user.append("Already collected (context): ").append(varsJson).append('\n');
        user.append("Plan (JSON array only):");
        try {
            Prompt prompt = new Prompt(List.of(new SystemMessage(sys), new UserMessage(user.toString())));
            var response = auxModel.call(prompt);
            String reply = response.getResult() != null
                    && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null
                            ? response.getResult().getOutput().getText()
                            : "";
            List<PlanItem> plan = parsePlan(reply);
            log.info("[FlowOps] planningStep '{}' generated {} item(s)",
                    node == null ? "?" : node.id(), plan.size());
            return plan;
        } catch (Exception e) {
            log.warn("[FlowOps] planningStep '{}' generation failed: {} — emitting empty plan",
                    node == null ? "?" : node.id(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Parses an LLM reply (or a persisted plan slot value) into a canonical
     * {@link PlanItem} list. Tolerant by design: strips markdown code fences,
     * isolates the outermost {@code [...]} array, fills in a sequential
     * {@code id} when the model omits one, defaults a missing/odd
     * {@code status} to {@code "pending"}, drops items with a blank title, and
     * caps the result at {@link #MAX_PLAN_ITEMS}. Returns an empty list when
     * nothing parseable is found.
     *
     * @since 2026.3.1
     */
    public static List<PlanItem> parsePlan(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String json = isolateJsonArray(raw);
        if (json == null) {
            return List.of();
        }
        List<Map<String, Object>> rows;
        try {
            rows = OBJECT_MAPPER.readValue(json, PLAN_LIST_TYPE);
        } catch (JacksonException e) {
            log.warn("[FlowOps] parsePlan could not parse plan JSON: {}", e.getMessage());
            return List.of();
        }
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<PlanItem> out = new java.util.ArrayList<>();
        int seq = 1;
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Object titleObj = row.get("title");
            String title = titleObj == null ? null : titleObj.toString().trim();
            if (title == null || title.isBlank()) {
                continue;
            }
            Object idObj = row.get("id");
            String id = (idObj == null || idObj.toString().isBlank())
                    ? String.valueOf(seq)
                    : idObj.toString().trim();
            Object statusObj = row.get("status");
            String status = (statusObj != null && PlanItem.DONE.equalsIgnoreCase(statusObj.toString().trim()))
                    ? PlanItem.DONE
                    : PlanItem.PENDING;
            out.add(new PlanItem(id, title, status));
            seq++;
            if (out.size() >= MAX_PLAN_ITEMS) {
                break;
            }
        }
        return out;
    }

    /**
     * Serializes a {@link PlanItem} list back into the compact JSON array
     * persisted in the plan slot. Always returns valid JSON — {@code "[]"}
     * for an empty/null list.
     *
     * @since 2026.3.1
     */
    public static String serializePlan(List<PlanItem> plan) {
        if (plan == null || plan.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> rows = new java.util.ArrayList<>(plan.size());
        for (PlanItem item : plan) {
            if (item == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.id());
            row.put("title", item.title());
            row.put("status", item.status() == null ? PlanItem.PENDING : item.status());
            rows.add(row);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(rows);
        } catch (JacksonException e) {
            log.warn("[FlowOps] serializePlan failed: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * Strips markdown code fences and isolates the outermost JSON array from a
     * model reply. Returns {@code null} when no {@code [ ... ]} span is found.
     */
    private static String isolateJsonArray(String raw) {
        String text = raw.trim();
        // Drop a leading ```json / ``` fence and a trailing ``` fence if present.
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline >= 0) {
                text = text.substring(firstNewline + 1);
            }
            int closingFence = text.lastIndexOf("```");
            if (closingFence >= 0) {
                text = text.substring(0, closingFence);
            }
            text = text.trim();
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    // ─────────────────────────── Plan iteration (T108-2) ───────────────────────────

    /**
     * Reserved slot holding the id of the plan item an {@code iteratePlan}
     * node is currently executing. Written when the engine descends into the
     * body sub-flow, read on ascent to know which item to complete, and
     * cleared when the iteration finishes. Exposed to the body sub-flow so it
     * can branch on the specific item.
     *
     * @since 2026.3.1
     */
    public static final String PLAN_ITEM_ID_SLOT = "__planItemId";

    /**
     * Reserved slot holding the title of the in-flight plan item — the
     * human-readable step text the body sub-flow executes. Interpolate it with
     * {@code {{__planItemTitle}}} in a body node's instruction.
     *
     * @since 2026.3.1
     */
    public static final String PLAN_ITEM_TITLE_SLOT = "__planItemTitle";

    /** {@code completionMode} value: flip the completed item's status to done (default). */
    public static final String COMPLETION_MODE_MARK_DONE = "mark_done";

    /** {@code completionMode} value: remove the completed item from the plan entirely. */
    public static final String COMPLETION_MODE_REMOVE = "remove";

    /**
     * First still-{@link PlanItem#isPending() pending} item of {@code plan},
     * or {@link Optional#empty()} when the plan is null/empty or every item is
     * already done — the signal an {@code iteratePlan} node uses to stop
     * iterating and advance on its outgoing edge.
     *
     * @since 2026.3.1
     */
    public static Optional<PlanItem> firstPendingItem(List<PlanItem> plan) {
        if (plan == null) {
            return Optional.empty();
        }
        return plan.stream().filter(PlanItem::isPending).findFirst();
    }

    /**
     * Writes the reserved {@link #PLAN_ITEM_ID_SLOT} / {@link #PLAN_ITEM_TITLE_SLOT}
     * markers for {@code item} into {@code state} so the body sub-flow about to
     * run sees which plan item it's executing. No-op for a null item.
     *
     * @since 2026.3.1
     */
    public static void setPlanIterationMarkers(TurChatFlowState state, PlanItem item) {
        if (state == null || item == null) {
            return;
        }
        Map<String, String> variables = readVariables(state);
        variables.put(PLAN_ITEM_ID_SLOT, item.id() == null ? "" : item.id());
        variables.put(PLAN_ITEM_TITLE_SLOT, item.title() == null ? "" : item.title());
        writeVariables(state, variables);
    }

    /**
     * Removes the reserved plan-iteration markers from {@code state} — called
     * when an {@code iteratePlan} node finishes (no more pending items) so
     * downstream nodes don't read a stale {@code __planItemId} /
     * {@code __planItemTitle}.
     *
     * @since 2026.3.1
     */
    public static void clearPlanIterationMarkers(TurChatFlowState state) {
        if (state == null) {
            return;
        }
        Map<String, String> variables = readVariables(state);
        boolean changed = variables.remove(PLAN_ITEM_ID_SLOT) != null;
        changed |= variables.remove(PLAN_ITEM_TITLE_SLOT) != null;
        if (changed) {
            writeVariables(state, variables);
        }
    }

    /**
     * Completes the plan item identified by {@code itemId} in the plan stored
     * at {@code planSlot} of {@code state}, per {@code completionMode}:
     * {@link #COMPLETION_MODE_REMOVE} drops the item, anything else (including
     * {@code null}) marks it {@link PlanItem#DONE done}. Persists the rewritten
     * plan. Returns {@code true} when the plan actually changed (item found and
     * mutated), {@code false} otherwise — so callers can skip a redundant SSE
     * publish.
     *
     * <p>Both modes are idempotent and monotonic: re-completing an item that's
     * already done leaves a done item; removing an absent item is a no-op. This
     * is what makes the {@code iteratePlan} loop terminate regardless of how
     * the body sub-flow behaves.
     *
     * @since 2026.3.1
     */
    public static boolean completePlanItem(TurChatFlowState state, String planSlot,
            String itemId, String completionMode) {
        if (state == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        String key = (planSlot == null || planSlot.isBlank()) ? DEFAULT_PLAN_SLOT : planSlot.trim();
        Map<String, String> variables = readVariables(state);
        List<PlanItem> plan = parsePlan(variables.get(key));
        if (plan.isEmpty()) {
            return false;
        }
        boolean remove = COMPLETION_MODE_REMOVE.equalsIgnoreCase(
                completionMode == null ? "" : completionMode.trim());
        List<PlanItem> updated = new java.util.ArrayList<>(plan.size());
        boolean changed = false;
        for (PlanItem item : plan) {
            if (item != null && itemId.equals(item.id())) {
                changed = true;
                if (remove) {
                    continue; // drop it
                }
                updated.add(new PlanItem(item.id(), item.title(), PlanItem.DONE));
            } else {
                updated.add(item);
            }
        }
        if (!changed) {
            return false;
        }
        variables.put(key, serializePlan(updated));
        writeVariables(state, variables);
        return true;
    }

    // ─────────────────────────── Suggested options lookup ───────────────────────────

    /**
     * Suggested answers to surface as clickable chips below the current AI Question, when
     * applicable. Two sources, in priority order:
     * <ol>
     *   <li>The node's own {@code inlineOptions} — free-form chips that don't branch the
     *       flow; picking one sends that label as the user message.</li>
     *   <li>The labels of the immediate downstream {@code switch} node's
     *       {@code switchOptions} — picking one routes the flow exactly as typing that
     *       label would (Tier-1 exact match in {@link #walkThroughSwitches}).</li>
     * </ol>
     *
     * <p>Returns an empty list when {@code current} is not an {@code aiQuestion}, when no
     * options are configured, or when the immediate downstream isn't a switch. Order in
     * the returned list mirrors the source order — the UI renders the chips in that
     * order.
     *
     * @since 2026.2.7
     */
    public static List<String> suggestedOptions(ChatFlowGraph graph, ChatFlowNode current) {
        if (graph == null || current == null || !isQuestionLike(current)) {
            return List.of();
        }
        if (!current.inlineOptions().isEmpty()) {
            return List.copyOf(current.inlineOptions());
        }
        List<ChatFlowEdge> outgoing = graph.outgoingEdges(current.id());
        if (outgoing.size() != 1) {
            // Ambiguous downstream — don't guess which switch (if any) to expose.
            return List.of();
        }
        ChatFlowNode downstream = graph.nodeById(outgoing.get(0).target()).orElse(null);
        if (downstream == null || !"switch".equals(downstream.type())) {
            return List.of();
        }
        List<String> labels = new java.util.ArrayList<>();
        for (ChatFlowNode.SwitchOption option : downstream.switchOptions()) {
            if (option != null && option.label() != null && !option.label().isBlank()) {
                labels.add(option.label());
            }
        }
        return labels;
    }

    // ─────────────────────────── Switch walker ───────────────────────────

    /**
     * Hard cap on consecutive switch hops in {@link #walkThroughSwitches}. As with
     * {@link #CONDITION_WALK_MAX}, authored flows never need this many; the cap
     * just keeps pathological cycles non-fatal.
     */
    private static final int SWITCH_WALK_MAX = 8;

    /**
     * Walks the state through any chain of {@code switch} nodes, classifying the configured
     * {@code switchVariable} against each node's {@code switchOptions} and following the matching
     * edge ({@code sourceHandle} = option id). Returns when the state lands on a non-switch node,
     * the chain dead-ends, or the safety cap is hit. Mutates {@code state.currentNodeId}.
     *
     * <p>Like conditions, switch nodes are transparent to the chat — the LLM never sees them as a
     * "Goal". The engine resolves them between turns: it reads the variable, picks the matching
     * option, and advances.
     *
     * @since 2026.2.7
     */
    public static void walkThroughSwitches(TurChatFlowState state,
            ChatFlowGraph graph,
            ChatModel auxModel) {
        if (state == null || graph == null) {
            return;
        }
        for (int i = 0; i < SWITCH_WALK_MAX; i++) {
            Optional<ChatFlowNode> currentOpt = currentNode(state, graph);
            if (currentOpt.isEmpty()) {
                return;
            }
            ChatFlowNode current = currentOpt.get();
            if (!"switch".equals(current.type())) {
                return;
            }
            Map<String, String> variables = readVariables(state);
            // When the author opted in to {@code overrideExistingValue=true}, clear the slot
            // before evaluating so the switch falls through to its wildcard branch — the same
            // branch authors typically wire back to an aiQuestion to re-capture the value.
            // Mirrors the always-ask escape hatch aiQuestion already exposes.
            String varName = current.switchVariable();
            if (Boolean.TRUE.equals(current.overrideExistingValue())
                    && varName != null && !varName.isBlank()
                    && variables.containsKey(varName.trim())) {
                log.info("[FlowOps] Switch '{}' override=true — clearing slot '{}' so wildcard fires",
                        current.id(), varName.trim());
                variables.remove(varName.trim());
                writeVariables(state, variables);
            }
            String resolvedHandle = resolveSwitchHandle(auxModel, current, variables);
            ChatFlowEdge edge = pickConditionEdge(graph, current.id(), resolvedHandle);
            if (edge == null) {
                log.warn("[FlowOps] Switch '{}' has no edge for handle '{}' and no wildcard — staying",
                        current.id(), resolvedHandle);
                return;
            }
            Optional<ChatFlowNode> target = graph.nodeById(edge.target());
            if (target.isEmpty()) {
                log.warn("[FlowOps] Switch edge '{}' points at missing node '{}'",
                        edge.id(), edge.target());
                return;
            }
            log.info("[FlowOps] Switch '{}' (var='{}') → option '{}' → node '{}'",
                    current.id(), current.switchVariable(), resolvedHandle, target.get().id());
            state.setCurrentNodeId(target.get().id());
        }
        log.warn("[FlowOps] walkThroughSwitches hit the {}-hop safety cap", SWITCH_WALK_MAX);
    }

    /**
     * Picks the {@code switchOptions} entry that best matches the variable value, returning its
     * {@code id} so the caller can find the matching outgoing edge by {@code sourceHandle}.
     *
     * <p>Resolution strategy, biased toward zero-cost classification first:
     * <ol>
     *   <li><b>Exact match</b>: variable value equals an option label (case-insensitive, trimmed).</li>
     *   <li><b>Substring match</b>: the value contains an option label, or the label contains the
     *       value. Picks the option with the longest matching segment (so "Beach Getaway"
     *       beats "Beach" when both labels start with "Beach").</li>
     *   <li><b>LLM classifier</b>: when the cheap matches don't find anything and {@code auxModel}
     *       is available, ask the LLM to pick the best option id. Returns the literal
     *       {@code NONE} when no option fits — that falls through to the wildcard edge.</li>
     * </ol>
     *
     * <p>When the variable is missing, the options list is empty, or every strategy gives up,
     * returns {@code null} so {@link #pickConditionEdge} resolves to the wildcard fallback (an
     * edge with no {@code sourceHandle}). Authors who care about coverage should keep one such
     * fallback wired on every switch.
     */
    private static String resolveSwitchHandle(ChatModel auxModel,
            ChatFlowNode switchNode,
            Map<String, String> variables) {
        return resolveSwitchOption(auxModel, switchNode, variables)
                .map(ChatFlowNode.SwitchOption::id)
                .orElse(null);
    }

    /**
     * Same classification cascade as {@link #resolveSwitchHandle} but returns the matched
     * {@link ChatFlowNode.SwitchOption} so callers can read other fields besides {@code id} —
     * notably {@link ChatFlowNode.SwitchOption#subFlowId()} for the T47 {@code subFlowSwitch}
     * node, which dispatches into a sub-flow per matched option instead of an outgoing edge.
     *
     * <p>Cascade is identical: exact label → substring (longest wins) → LLM classifier. Empty
     * value, no options, or no fit at any tier returns {@link Optional#empty()} so the caller
     * decides what fallback to apply (wildcard edge for {@code switch}; advance-without-descent
     * for {@code subFlowSwitch}).
     *
     * @since 2026.3.1
     */
    public static Optional<ChatFlowNode.SwitchOption> resolveSwitchOption(ChatModel auxModel,
            ChatFlowNode switchNode,
            Map<String, String> variables) {
        List<ChatFlowNode.SwitchOption> options = switchNode.switchOptions();
        if (options == null || options.isEmpty()) {
            log.warn("[FlowOps] Switch '{}' has no options configured", switchNode.id());
            return Optional.empty();
        }
        String variableName = switchNode.switchVariable();
        String rawValue = variableName == null || variableName.isBlank()
                ? null
                : variables.get(variableName);
        if (rawValue == null || rawValue.isBlank()) {
            log.info("[FlowOps] Switch '{}' has no usable value (var='{}') — falling back to wildcard",
                    switchNode.id(), variableName);
            return Optional.empty();
        }
        String value = rawValue.trim();
        String lowerValue = value.toLowerCase();

        // 1) Exact label match.
        for (ChatFlowNode.SwitchOption option : options) {
            if (option == null || option.label() == null) {
                continue;
            }
            if (option.label().trim().equalsIgnoreCase(value)) {
                log.info("[FlowOps] Switch '{}' exact match: '{}' → option '{}'",
                        switchNode.id(), value, option.id());
                return Optional.of(option);
            }
        }

        // 2) Substring match — prefer the longest option label that fits, so multi-word labels
        //    take precedence over their own prefixes ("Beach Getaway" > "Beach").
        ChatFlowNode.SwitchOption best = null;
        int bestLen = 0;
        for (ChatFlowNode.SwitchOption option : options) {
            if (option == null || option.label() == null || option.label().isBlank()) {
                continue;
            }
            String lowerLabel = option.label().trim().toLowerCase();
            if (lowerValue.contains(lowerLabel) || lowerLabel.contains(lowerValue)) {
                int len = Math.min(lowerLabel.length(), lowerValue.length());
                if (len > bestLen) {
                    best = option;
                    bestLen = len;
                }
            }
        }
        if (best != null) {
            log.info("[FlowOps] Switch '{}' substring match: '{}' → option '{}' (label='{}')",
                    switchNode.id(), value, best.id(), best.label());
            return Optional.of(best);
        }

        // 3) LLM classifier — the user phrased their answer in a way the cheap matches missed
        //    ("I want to climb something" vs "Mountain Expedition"). Ask the aux model to pick.
        if (auxModel == null) {
            log.info("[FlowOps] Switch '{}' has no aux model for classification — falling back to wildcard",
                    switchNode.id());
            return Optional.empty();
        }
        String classifiedId = classifySwitchWithLlm(auxModel, switchNode, options, variableName, value);
        if (classifiedId == null) {
            return Optional.empty();
        }
        for (ChatFlowNode.SwitchOption option : options) {
            if (option != null && classifiedId.equals(option.id())) {
                return Optional.of(option);
            }
        }
        return Optional.empty();
    }

    /**
     * Asks {@code auxModel} to classify {@code value} into one of the supplied option ids. Returns
     * the chosen id (when valid) or {@code null} when the model can't decide, returns a malformed
     * answer, or replies with the sentinel {@code NONE}.
     */
    private static String classifySwitchWithLlm(ChatModel auxModel,
            ChatFlowNode switchNode,
            List<ChatFlowNode.SwitchOption> options,
            String variableName,
            String value) {
        StringBuilder optionList = new StringBuilder();
        Map<String, ChatFlowNode.SwitchOption> byId = new LinkedHashMap<>();
        for (ChatFlowNode.SwitchOption option : options) {
            if (option == null || option.id() == null || option.id().isBlank()) {
                continue;
            }
            byId.put(option.id(), option);
            optionList.append("  - ").append(option.id());
            if (option.label() != null && !option.label().isBlank()) {
                optionList.append(": ").append(option.label().trim());
            }
            optionList.append('\n');
        }
        if (byId.isEmpty()) {
            return null;
        }
        String sys = """
                You classify a user's answer into one of several chat-flow branches.

                Reply with EXACTLY one option id from the list below — no quotes, no JSON, no
                explanation. If no option fits, reply with the literal word: NONE.

                Match liberally: free-text answers like "I want a beach holiday" should map to a
                "Beach Getaway" option. Honour the user's language; treat translations and
                synonyms as matches when they're clearly equivalent.
                """;
        String user = "Variable name: " + safe(variableName) + "\n"
                + "Variable value: \"" + value + "\"\n"
                + "Options (id: label):\n" + optionList
                + "Answer (one id or NONE):";
        try {
            Prompt prompt = new Prompt(List.of(new SystemMessage(sys), new UserMessage(user)));
            var response = auxModel.call(prompt);
            String reply = response.getResult() != null
                    && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null
                            ? response.getResult().getOutput().getText().trim()
                            : "";
            // The model occasionally wraps the answer in backticks or quotes; strip them before
            // the lookup. We also accept the id case-insensitively to be lenient.
            String cleaned = reply.replaceAll("^[`\"']+|[`\"']+$", "").trim();
            if (cleaned.isEmpty() || "NONE".equalsIgnoreCase(cleaned)) {
                log.info("[FlowOps] Switch '{}' LLM classifier returned NONE for '{}'",
                        switchNode.id(), value);
                return null;
            }
            for (Map.Entry<String, ChatFlowNode.SwitchOption> entry : byId.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(cleaned)) {
                    log.info("[FlowOps] Switch '{}' LLM classifier: '{}' → option '{}'",
                            switchNode.id(), value, entry.getKey());
                    return entry.getKey();
                }
            }
            log.warn("[FlowOps] Switch '{}' LLM classifier returned unknown id '{}' (reply='{}')",
                    switchNode.id(), cleaned, reply);
            return null;
        } catch (Exception e) {
            log.warn("[FlowOps] Switch '{}' LLM classifier failed: {} — falling back to wildcard",
                    switchNode.id(), e.getMessage());
            return null;
        }
    }

    /**
     * Evaluates {@code expression} against the collected {@code variables}.
     * <p>
     * Strategy: try {@link #trySpel(String, Map) SpEL} first — covers the
     * 90%+ case ({@code var == 'value'}, {@code var > N}, boolean
     * combinations) deterministically, with no extra LLM call. When SpEL
     * cannot parse or evaluate the expression (typically because the author
     * wrote it in plain language — "user said yes to scheduling"), fall
     * back to the LLM-as-judge.
     * <p>
     * Default on every dead end is {@code true} (the {@code "yes"} branch)
     * — biased toward NOT stalling the flow.
     */
    public static boolean evaluateCondition(ChatModel auxModel,
            String expression,
            Map<String, String> variables) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        String varsJsonForLog = serializeVariables(variables);
        Boolean spelResult = trySpel(expression, variables);
        if (spelResult != null) {
            log.info("[FlowOps] Condition (SpEL): expr='{}' vars={} result={}",
                    expression, varsJsonForLog, spelResult);
            return spelResult;
        }
        log.info("[FlowOps] Condition fell back to LLM judge: expr='{}' vars={}",
                expression, varsJsonForLog);
        if (auxModel == null) {
            // No SpEL match and no judge model — bias toward yes.
            return true;
        }
        String varsJson = serializeVariables(variables);
        String sys = """
                You evaluate boolean conditions for a chat-flow engine. You receive a
                variables map (JSON object collected by previous flow steps) and a
                condition. Reply with EXACTLY one word: true or false. No quotes, no
                JSON, no explanation.

                Treat string values case-insensitively for yes/no equality:
                  "sim" / "Sim" / "yes" / "yep" all equal "sim" or "yes".
                  "não" / "Não" / "nao" / "no" / "nope" all equal "não" or "no".

                If the condition references a variable that is missing or has an empty
                string value, reply false. If the condition is genuinely ambiguous,
                reply false.

                Examples:
                  Variables: {"stuffedCrust": "sim"}
                  Condition: stuffedCrust == 'sim'
                  Answer: true

                  Variables: {"stuffedCrust": "Não"}
                  Condition: stuffedCrust == 'sim'
                  Answer: false

                  Variables: {"company_size": "120"}
                  Condition: company_size > 50
                  Answer: true
                """;
        String user = "Variables: " + varsJson + "\n"
                + "Condition: " + expression.trim() + "\n"
                + "Answer (true|false):";
        try {
            Prompt prompt = new Prompt(List.of(new SystemMessage(sys), new UserMessage(user)));
            var response = auxModel.call(prompt);
            String reply = response.getResult() != null
                    && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null
                            ? response.getResult().getOutput().getText().trim().toLowerCase()
                            : "";
            log.info("[FlowOps] Condition judge: vars={} expr='{}' reply='{}'",
                    varsJson, expression, reply);
            // Default to the yes branch on any unexpected output — biased
            // toward not stalling.
            return !reply.startsWith("false");
        } catch (Exception e) {
            log.warn("[FlowOps] Condition judge failed: {} — defaulting to yes branch", e.getMessage());
            return true;
        }
    }

    /**
     * Attempts to evaluate {@code expression} as a SpEL expression against
     * {@code variables} as the root object (with {@link MapAccessor} so
     * plain identifiers like {@code stuffedCrust} resolve to map entries —
     * no need for the {@code #var} prefix).
     * <p>
     * Returns the boolean result on success, or {@code null} when SpEL
     * cannot parse / evaluate / coerce-to-boolean — that signals the caller
     * to fall back to the LLM judge. Common reasons to return {@code null}:
     * <ul>
     *   <li>The expression is plain language (no operators).</li>
     *   <li>A referenced variable is missing from the map.</li>
     *   <li>The expression evaluates to a non-boolean value
     *       (e.g. a string or number).</li>
     * </ul>
     */
    private static Boolean trySpel(String expression, Map<String, String> variables) {
        try {
            Map<String, String> root = variables == null ? Map.of() : variables;
            StandardEvaluationContext ctx = new StandardEvaluationContext(root);
            ctx.addPropertyAccessor(new MapAccessor());
            Object result = SPEL_PARSER.parseExpression(expression).getValue(ctx);
            return result instanceof Boolean b ? b : null;
        } catch (RuntimeException e) {
            log.debug("[FlowOps] SpEL could not evaluate '{}': {}", expression, e.getMessage());
            return null;
        }
    }

    /** Reused parser — SpEL parsers are thread-safe and stateless. */
    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();

    /**
     * Serializes the runtime variables as a compact JSON object — the format
     * the LLM judge in {@link #evaluateCondition} expects. Falls back to
     * {@link Map#toString()} only when Jackson somehow throws on a plain
     * String→String map (effectively unreachable, kept for safety).
     */
    private static String serializeVariables(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(variables);
        } catch (JacksonException e) {
            return variables.toString();
        }
    }

    // ─────────────────────────── Variable I/O ───────────────────────────

    /** Reads {@code state.variablesJson} into a mutable map. */
    public static Map<String, String> readVariables(TurChatFlowState state) {
        return readVariablesJson(state.getVariablesJson());
    }

    /** Reads a raw JSON object into a mutable map. */
    public static Map<String, String> readVariablesJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, String> parsed = OBJECT_MAPPER.readValue(json, VARIABLES_TYPE);
            Map<String, String> result = parsed != null ? new LinkedHashMap<>(parsed) : new LinkedHashMap<>();
            // T61: decrypt pii_* slots transparently. INSTANCE is null in
            // pure unit tests, in which case the map is returned as-is.
            com.viglet.turing.service.chatslots.TurPiiSlotService pii =
                    com.viglet.turing.service.chatslots.TurPiiSlotService.getInstance();
            if (pii != null) pii.decryptInPlace(result);
            return result;
        } catch (JacksonException e) {
            log.warn("[FlowOps] Variables JSON is invalid; resetting");
            return new LinkedHashMap<>();
        }
    }

    /** Serializes {@code variables} back into {@code state.variablesJson}. */
    public static void writeVariables(TurChatFlowState state, Map<String, String> variables) {
        try {
            Map<String, String> toSerialize = variables != null
                    ? new LinkedHashMap<>(variables) : new LinkedHashMap<>();
            // T61: encrypt pii_* slots before they hit the persistence
            // layer. INSTANCE is null in pure unit tests — the map is
            // serialized as-is, preserving legacy semantics.
            com.viglet.turing.service.chatslots.TurPiiSlotService pii =
                    com.viglet.turing.service.chatslots.TurPiiSlotService.getInstance();
            if (pii != null) pii.encryptInPlace(toSerialize);
            state.setVariablesJson(OBJECT_MAPPER.writeValueAsString(toSerialize));
        } catch (JacksonException e) {
            log.warn("[FlowOps] Failed to serialize variables for state '{}'", state.getId());
            state.setVariablesJson("{}");
        }
    }

    // ─────────────────────────── Regex validation ───────────────────────────

    /**
     * Tries to extract a value matching the node's validation rule from a
     * user message. Returns the matched substring, or null if nothing fits.
     */
    public static String extractValue(String validationRule, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        if (validationRule == null || validationRule.isBlank()
                || "none".equalsIgnoreCase(validationRule)) {
            String trimmed = userMessage.trim();
            return trimmed.length() >= 2 ? trimmed : null;
        }
        String rule = validationRule.trim().toLowerCase();
        Pattern pattern = VALIDATION_PATTERNS.get(rule);
        if (pattern == null) {
            String trimmed = userMessage.trim();
            return trimmed.length() >= 2 ? trimmed : null;
        }
        var matcher = pattern.matcher(userMessage);
        if (!matcher.find()) return null;
        String candidate = matcher.group();
        // BR document rules need a second pass: the regex pins the format
        // (correct length + punctuation), but the mathematical checksum is
        // what separates a real CPF/CNPJ from a syntactically-correct
        // random number (111.111.111-11, 12345678900, etc.). Returning null
        // when the checksum fails causes the strategy to re-ask, same as
        // any other validation miss — the user retypes.
        if ("cpf".equals(rule)  && !isValidCpfChecksum(candidate))  return null;
        if ("cnpj".equals(rule) && !isValidCnpjChecksum(candidate)) return null;
        return candidate;
    }

    /**
     * Brazilian CPF checksum (módulo 11). Caller must have already
     * confirmed the digit string matches the {@code cpf} regex — this
     * method only adds the math check. Rejects sequences of repeated
     * digits ({@code 111.111.111-11}, {@code 000.000.000-00}, …) which
     * mathematically satisfy mod 11 but are flagged as test/invalid
     * by Receita Federal.
     *
     * <p>Package-private for unit testing.
     */
    static boolean isValidCpfChecksum(String cpf) {
        if (cpf == null) return false;
        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() != 11) return false;
        if (isAllSameDigit(digits)) return false;
        // Check digit 1: weighted sum of positions 1..9 by 10..2
        int dv1 = computeMod11CheckDigit(digits, 9, 10);
        if (dv1 != Character.digit(digits.charAt(9), 10)) return false;
        // Check digit 2: weighted sum of positions 1..10 by 11..2
        int dv2 = computeMod11CheckDigit(digits, 10, 11);
        return dv2 == Character.digit(digits.charAt(10), 10);
    }

    /**
     * Brazilian CNPJ checksum (módulo 11). Same shape as CPF but with
     * 14 digits and a different weight cycle (5..2 then 9..2 for digit
     * 13, shifted by one for digit 14). Repeated-digit sequences are
     * rejected for the same reason as CPF.
     *
     * <p>Package-private for unit testing.
     */
    static boolean isValidCnpjChecksum(String cnpj) {
        if (cnpj == null) return false;
        String digits = cnpj.replaceAll("\\D", "");
        if (digits.length() != 14) return false;
        if (isAllSameDigit(digits)) return false;
        // CNPJ uses a 6,5,4,3,2,9,8,7,6,5,4,3,2 weight cycle (going right→left)
        // — implemented as positions 1..12 weighted 5,4,3,2,9,8,7,6,5,4,3,2.
        int[] weights1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int dv1 = computeMod11WithWeights(digits, weights1);
        if (dv1 != Character.digit(digits.charAt(12), 10)) return false;
        int[] weights2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int dv2 = computeMod11WithWeights(digits, weights2);
        return dv2 == Character.digit(digits.charAt(13), 10);
    }

    /**
     * CPF-style mod-11 check digit: weights decrement from {@code startWeight}
     * down to 2 across {@code len} positions starting at index 0.
     */
    private static int computeMod11CheckDigit(String digits, int len, int startWeight) {
        int sum = 0;
        for (int i = 0; i < len; i++) {
            sum += Character.digit(digits.charAt(i), 10) * (startWeight - i);
        }
        int rem = sum % 11;
        return rem < 2 ? 0 : 11 - rem;
    }

    /**
     * CNPJ-style mod-11 check digit: explicit weight array per position
     * (the cycle isn't a simple decrement — see {@link #isValidCnpjChecksum}).
     */
    private static int computeMod11WithWeights(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += Character.digit(digits.charAt(i), 10) * weights[i];
        }
        int rem = sum % 11;
        return rem < 2 ? 0 : 11 - rem;
    }

    private static boolean isAllSameDigit(String digits) {
        char first = digits.charAt(0);
        for (int i = 1; i < digits.length(); i++) {
            if (digits.charAt(i) != first) return false;
        }
        return true;
    }

    /**
     * Folds an obvious yes/no answer to a canonical lowercase token —
     * {@code "Sim"}/{@code "SIM"}/{@code "yep"} → {@code "sim"};
     * {@code "Não"}/{@code "Nope"} → {@code "não"}; {@code "Yes"} → {@code "yes"};
     * {@code "No"} → {@code "no"}. Anything else passes through unchanged
     * (preserves names, addresses, free text). The point is to make
     * downstream SpEL conditions like {@code stuffedCrust == 'sim'} match
     * regardless of how the model cased the captured value.
     */
    public static String normalizeYesNo(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return trimmed;
        String lower = trimmed.toLowerCase();
        return switch (lower) {
            case "sim", "s", "claro" -> "sim";
            case "não", "nao", "n" -> "não";
            case "yes", "yep", "yeah", "y" -> "yes";
            case "no", "nope" -> "no";
            default -> trimmed;
        };
    }

    /**
     * T23 / §IV.6 — maximum Levenshtein edit distance accepted when
     * canonicalizing a slot value against a node's {@code inlineOptions}.
     * Tries 0 (exact) → 1 (single-char typo / diacritic) → 2 (multi-char
     * typo) in order, stopping at the first option that matches.
     *
     * <p>Edit distance 2 catches the most common typos and diacritic
     * issues ("Senior" ↔ "Sênior", "Gestor" ↔ "Geestor") without
     * over-triggering on genuinely different options ("Gestor" vs
     * "C-level" stays a non-match even at 2 edits because the character
     * sets diverge after the first few positions).
     */
    private static final int MAX_FUZZY_EDIT_DISTANCE = 2;

    /**
     * T23 / §IV.6 — canonicalize a slot value against the node's
     * {@code inlineOptions}. Slot values get captured loosely by the LLM
     * judge (or as a literal user reply on the heuristic path) and then
     * stored into {@code variables} — downstream consumers (switch nodes,
     * conditions, telemetry dashboards) expect canonical option labels.
     * Variants like "Gerente Senior" / "Gerente Sênior" or "Sr Comprador"
     * / "Comprador" used to silently miss switch matching; this helper
     * normalises them.
     *
     * <h2>Match cascade</h2>
     *
     * <ol>
     *   <li><b>Exact match</b> (case-insensitive, trimmed) — when the
     *       value already equals an option, return that option's canonical
     *       casing.</li>
     *   <li><b>Substring containment</b> in either direction — handles
     *       "Sr Comprador" ↔ "Comprador" (prefix/suffix noise) where the
     *       extra tokens push Levenshtein distance past 2. Prefers the
     *       LONGEST matching option so "Gerente Senior" prefers "Gerente
     *       Senior" over "Senior" when both are options.</li>
     *   <li><b>Lucene {@link LevenshteinAutomata}</b> edit-distance 1
     *       — catches diacritics ("Sênior"/"Senior") and single-char
     *       typos ("Compradoor"/"Comprador").</li>
     *   <li><b>Lucene {@link LevenshteinAutomata}</b> edit-distance 2
     *       — catches transpositions and double-typos
     *       ("Gerente Senir"/"Gerente Sênior").</li>
     * </ol>
     *
     * <p>Returns the original {@code value} unchanged when no option
     * matches at any tier OR when the node has no {@code inlineOptions}
     * configured. Never returns null for a non-null input — the contract
     * is "canonicalize or pass through".
     *
     * @param value    extracted slot value (may be null/blank → passed through)
     * @param options  the node's {@code inlineOptions} list (may be empty)
     * @return canonical option label when a match is found, otherwise
     *         {@code value} verbatim
     * @since 2026.2.7
     */
    public static String canonicalizeAgainstInlineOptions(String value, List<String> options) {
        if (value == null || value.isBlank() || options == null || options.isEmpty()) {
            return value;
        }
        String valueTrimmed = value.trim();
        String valueLower = valueTrimmed.toLowerCase(Locale.ROOT);

        String exactMatch = matchExact(valueLower, options);
        if (exactMatch != null) {
            return exactMatch;
        }
        String substringMatch = matchBySubstring(valueLower, options);
        if (substringMatch != null) {
            return substringMatch;
        }
        for (int edits = 1; edits <= MAX_FUZZY_EDIT_DISTANCE; edits++) {
            String fuzzyMatch = matchByFuzzy(valueLower, options, edits);
            if (fuzzyMatch != null) {
                log.info("[FlowOps] inlineOption canonicalize: '{}' → '{}' (edit distance ≤ {})",
                        valueTrimmed, fuzzyMatch, edits);
                return fuzzyMatch;
            }
        }
        return value;
    }

    private static String matchExact(String valueLower, List<String> options) {
        for (String option : options) {
            if (option != null && option.trim().toLowerCase(Locale.ROOT).equals(valueLower)) {
                return option.trim();
            }
        }
        return null;
    }

    private static String matchBySubstring(String valueLower, List<String> options) {
        // When multiple options match by substring, prefer the LONGER option
        // (more specific label) — "Gerente Senior" beats "Senior" even though
        // both technically substring-match the same value. Using the option
        // length (not max of value and option) makes the ranking specific to
        // the label catalog instead of accidentally favoring options whose
        // length happens to be close to the user input.
        String best = null;
        int bestLen = 0;
        for (String option : options) {
            if (option == null || option.isBlank()) {
                continue;
            }
            String optionLower = option.trim().toLowerCase(Locale.ROOT);
            if (valueLower.contains(optionLower) || optionLower.contains(valueLower)) {
                int len = optionLower.length();
                if (len > bestLen) {
                    best = option.trim();
                    bestLen = len;
                }
            }
        }
        return best;
    }

    /**
     * Searches {@code options} for an entry that's within {@code maxEdits}
     * Levenshtein edits of {@code valueLower}. Builds a fresh
     * {@link LevenshteinAutomata} per option (cheap — these strings are
     * short admin-configured labels, not full documents) and runs the
     * value against the byte automaton. When multiple options match at
     * the same edit budget, the LONGER one wins (more specific label
     * over a short prefix).
     */
    private static String matchByFuzzy(String valueLower, List<String> options, int maxEdits) {
        byte[] valueBytes = valueLower.getBytes(StandardCharsets.UTF_8);
        String best = null;
        int bestLen = 0;
        for (String option : options) {
            if (option == null || option.isBlank()) {
                continue;
            }
            String optionLower = option.trim().toLowerCase(Locale.ROOT);
            // LevenshteinAutomata(targetString, allowTranspositions) — true
            // makes "Sênior"/"Sêinor" count as 1 edit (single transposition)
            // instead of 2. Admin-friendly default.
            LevenshteinAutomata la = new LevenshteinAutomata(optionLower, true);
            ByteRunAutomaton matcher = new ByteRunAutomaton(la.toAutomaton(maxEdits));
            if (matcher.run(valueBytes, 0, valueBytes.length)) {
                if (optionLower.length() > bestLen) {
                    best = option.trim();
                    bestLen = optionLower.length();
                }
            }
        }
        return best;
    }

    /**
     * Whether {@code value} satisfies {@code validationRule}. {@code null}
     * or blank rules (or {@code "none"}) accept any non-empty value. The LLM
     * strategies call this AFTER the model returns a verdict, to override
     * {@code ready_to_advance} when the model was too permissive (e.g.
     * accepting "pizza" as a phone number).
     */
    public static boolean valueMatchesRule(String validationRule, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (validationRule == null || validationRule.isBlank()
                || "none".equalsIgnoreCase(validationRule)) {
            return true;
        }
        Pattern pattern = VALIDATION_PATTERNS.get(validationRule.trim().toLowerCase());
        if (pattern == null) {
            return true;
        }
        return pattern.matcher(value).find();
    }

    /**
     * Very small "did the assistant address the goal?" heuristic: at least
     * one informational word from {@code aiInstruction} appears in the
     * response. Replaceable by a real classifier in {@code LLM_JUDGE}.
     */
    public static boolean looksAligned(String aiInstruction, String assistantMessage) {
        if (assistantMessage == null || assistantMessage.isBlank()) {
            return false;
        }
        if (aiInstruction == null || aiInstruction.isBlank()) {
            return assistantMessage.length() >= 10;
        }
        String lowerReply = assistantMessage.toLowerCase();
        for (String token : aiInstruction.toLowerCase().split("\\W+")) {
            if (token.length() >= 5 && lowerReply.contains(token)) {
                return true;
            }
        }
        return assistantMessage.length() >= 40;
    }

    // ─────────────────────────── String utilities ───────────────────────────

    /** Returns {@code s} or {@code ""} if null. */
    public static String safe(String s) {
        return s == null ? "" : s;
    }

    /** Strips Markdown code fences ({@code ```json … ```}) from a model reply. */
    public static String stripFences(String s) {
        String trimmed = s.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int closing = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && closing > firstNewline) {
                return trimmed.substring(firstNewline + 1, closing).trim();
            }
        }
        return trimmed;
    }
}

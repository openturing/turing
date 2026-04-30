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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

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
    private static final Map<String, Pattern> VALIDATION_PATTERNS = Map.of(
            "email", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"),
            "phone", Pattern.compile("\\+?[0-9][0-9 ()\\-]{6,}\\b"),
            "url", Pattern.compile("https?://[\\w.\\-/?#=&%+]+", Pattern.CASE_INSENSITIVE),
            "number", Pattern.compile("-?\\d+(?:[.,]\\d+)?"),
            "date", Pattern.compile("\\b\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}\\b"));

    private ChatFlowOps() {
        // utility class — not instantiable
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
        Map<String, String> variables = readVariables(state);
        if (!variables.isEmpty()) {
            sb.append("Already collected: ").append(variables).append('\n');
        }
        appendNextStepHint(sb, graph, node);
        sb.append(HARD_RULES);
        return sb.toString();
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
        if (target.aiInstruction() == null || target.aiInstruction().isBlank()) {
            return;
        }
        sb.append("Next step preview (for chaining your reply): ")
                .append(target.aiInstruction().trim())
                .append("\n");
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
            return parsed != null ? new LinkedHashMap<>(parsed) : new LinkedHashMap<>();
        } catch (JacksonException e) {
            log.warn("[FlowOps] Variables JSON is invalid; resetting");
            return new LinkedHashMap<>();
        }
    }

    /** Serializes {@code variables} back into {@code state.variablesJson}. */
    public static void writeVariables(TurChatFlowState state, Map<String, String> variables) {
        try {
            state.setVariablesJson(OBJECT_MAPPER.writeValueAsString(
                    variables != null ? variables : Map.of()));
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
        Pattern pattern = VALIDATION_PATTERNS.get(validationRule.trim().toLowerCase());
        if (pattern == null) {
            String trimmed = userMessage.trim();
            return trimmed.length() >= 2 ? trimmed : null;
        }
        var matcher = pattern.matcher(userMessage);
        return matcher.find() ? matcher.group() : null;
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

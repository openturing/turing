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
     * variables, and the conversational HARD RULES that all strategies enforce.
     * <p>
     * Strategies append their strategy-specific tail (the heuristic adds a hint
     * about validation; the structured-output strategy adds the JSON contract).
     * Returns an empty string when the node is terminal — once the flow ended,
     * the agent should chat freely.
     */
    public static String buildBaseAddendum(ChatFlowNode node, TurChatFlowState state) {
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
        sb.append(HARD_RULES);
        return sb.toString();
    }

    /**
     * Conversational rules every guardrail strategy enforces. Kept in a
     * single place so wording fixes (e.g. politeness ≠ abandonment, no
     * improvising the next step) propagate to all three strategies at once.
     */
    private static final String HARD_RULES = """

            Hard rules (the Goal above is non-negotiable):
            - Ask EXACTLY the question implied by Goal. Do NOT improvise the next
              step ahead of time, do NOT volunteer information not in Goal, do NOT
              try to be helpful by jumping to a later question. One step at a time.
            - Stay strictly on Goal. If the user deviates, briefly redirect them
              back to this step inside your reply.
            - Accept short, bare answers as valid. If you asked for a name and the
              user replies with just "Alexandre", that IS the name — acknowledge
              it and move on. The same applies to emails, phone numbers, dates,
              etc. Never ask the same question again when the user already
              supplied a value, even briefly.
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

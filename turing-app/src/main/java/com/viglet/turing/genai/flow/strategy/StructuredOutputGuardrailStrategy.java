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

import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.TurChatFlowStaticPromptCache;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Single-call strategy: the chat LLM is instructed to wrap its reply and
 * the verdict fields in one JSON object — the engine extracts {@code reply}
 * for display and applies the rest. Same enforcement as
 * {@link LlmJudgeGuardrailStrategy} without the extra round-trip; falls
 * back to {@link HeuristicGuardrailStrategy} when the JSON contract is
 * not honored.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Slf4j
@Component
public class StructuredOutputGuardrailStrategy implements TurChatFlowGuardrailStrategy {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Preferred fallback when the chat LLM ignores the JSON contract — we
     * make a dedicated judge call (one extra round-trip but with a focused
     * prompt that gpt-4o-mini follows reliably). The judge in turn falls
     * back to the heuristic if its own call fails. Real-world observation
     * with the agent chat: the main reply is conversational ~80% of the
     * time on Portuguese flows, so the judge call is the path that lands
     * the right values.
     */
    private final LlmJudgeGuardrailStrategy judgeFallback;
    /** Last-resort fallback if both JSON parsing AND the judge call fail. */
    private final HeuristicGuardrailStrategy heuristicFallback;
    private final TurChatFlowStaticPromptCache staticPromptCache;

    public StructuredOutputGuardrailStrategy(LlmJudgeGuardrailStrategy judgeFallback,
            HeuristicGuardrailStrategy heuristicFallback,
            TurChatFlowStaticPromptCache staticPromptCache) {
        this.judgeFallback = judgeFallback;
        this.heuristicFallback = heuristicFallback;
        this.staticPromptCache = staticPromptCache;
    }

    @Override
    public TurChatFlowGuardrailMethod getMethod() {
        return TurChatFlowGuardrailMethod.STRUCTURED_OUTPUT;
    }

    @Override
    public String buildSystemPromptAddendum(TurChatFlow flow, ChatFlowNode node,
            TurChatFlowState state, ChatFlowGraph graph) {
        // Shared base (active-step header + goal/collect/validation + already-collected
        // variables + HARD RULES) lives in ChatFlowOps so wording fixes propagate to
        // every strategy at once. We just bolt the JSON contract on top.
        // T31 / §IV.5 — base routed through the (flowId, nodeId) cache.
        String base = staticPromptCache.buildAddendum(flow, node, state, graph, true);
        if (base.isEmpty()) {
            return "";
        }
        return base + JSON_CONTRACT;
    }

    /**
     * The JSON envelope the chat LLM must emit on every turn. The schema is
     * the contract between the model and {@link #parseStructuredVerdict};
     * keeping it in a constant makes the {@code @Override} above tiny and
     * highlights that everything ABOVE this contract comes from the
     * heuristic addendum (single source of truth for the conversational
     * rules).
     */
    private static final String JSON_CONTRACT = """

            You MUST reply with a SINGLE JSON OBJECT on one line — nothing else, no
            code fences, no prose around it. Schema:

            {
              "reply": "<the message the user will see, written in their language>",
              "on_topic": <true|false>,
              "collected_value": "<value extracted from the user's last message, or null>",
              "ready_to_advance": <true|false>,
              "abandoned": <true|false>
            }

            Rules for each field:
            - reply: friendly conversational message addressed to the user, in their
              language. Keep it natural — never mention internal terms like "step",
              "goal" or "output variable". Accept short bare answers ("Alexandre",
              "foo@bar.com") as valid replies and acknowledge them.
            - on_topic: false ONLY when YOUR reply went off-goal. If the USER asked
              something off-goal but YOUR reply politely brought them back, that is
              on_topic=true.
            - collected_value: extract whatever ANSWERS the Goal. Be permissive
              about FORMAT — bare answers count, no need for a "my name is X" prefix.
                Goal asks for a name → "Alexandre", "João Silva"
                Goal asks for an email → "foo@bar.com"
                Goal asks for a phone → "+5511999999999", "11 99999-9999"
                Goal asks a yes/no question → "sim", "não", "yes", "no", "yep",
                  "nope" — these ARE the values to collect; do NOT return null
                  for them just because they are short.
                Goal asks for a flavor / option → the option name verbatim
                  ("portuguesa", "calabresa", "média").
              POLITENESS MARKERS in isolation are NEVER values regardless of Goal:
                "obrigado", "valeu", "ok", "thanks", "thank you", "got it".
              CONVERSATIONAL INTENTS — phrases that say "I want to start X" but
              do NOT answer the Goal — are NEVER values:
                Goal: "ask for the user's name" + user: "quero me inscrever" → null
                Goal: "ask for an email" + user: "olá" → null
              When the user's message clearly answers the Goal, extract it; when in
              doubt, return null and let the next turn decide.
            - ready_to_advance: true when collected_value is non-null and (when a
              validation rule applies) matches the rule. For steps without an output
              variable, true when the goal is clearly addressed. NEVER true on the
              same turn where collected_value is null for a step that requires a value.
            - abandoned: true ONLY when the user uses an EXPLICIT cancellation verb:
              "cancelar", "desisto", "quero parar", "cancel", "stop", "quit", "nevermind",
              "I give up". Politeness markers in isolation — "obrigado", "valeu", "ok",
              "thanks", "thank you", "got it" — are NEVER abandonment; they acknowledge
              the assistant's previous message. If the user just supplied a valid
              collected_value, abandoned MUST be false. When in doubt, return false.
              When abandoned is true, write a graceful goodbye in `reply`.

            Example: {"reply":"Prazer, Alexandre! Qual seu e-mail?","on_topic":true,"collected_value":"Alexandre","ready_to_advance":true,"abandoned":false}
            """;

    @Override
    public String advance(AdvanceContext ctx) {
        ChatFlowNode node = ctx.currentNode();
        if ("end".equals(node.type())) {
            return null;
        }

        StructuredVerdict verdict = parseStructuredVerdict(ctx.assistantMessage());
        if (verdict == null) {
            // Log the raw response so the next failure tells us WHY the LLM
            // ignored the JSON contract — usually the model produced a normal
            // conversational reply, sometimes it wrapped JSON in fences, etc.
            log.warn("[StructuredOutput] Could not parse JSON on node '{}'. Raw response ({} chars): {}",
                    node.id(),
                    ctx.assistantMessage() == null ? 0 : ctx.assistantMessage().length(),
                    truncate(ctx.assistantMessage(), 500));
            // Delegate to the judge: a focused, JSON-only LLM call. It runs
            // its own heuristic fallback if the judge call also fails.
            if (judgeFallback != null && ctx.auxiliaryModel() != null) {
                log.info("[StructuredOutput] Falling back to dedicated judge call");
                return judgeFallback.advance(ctx);
            }
            log.info("[StructuredOutput] No judge model available — falling back to heuristic");
            return heuristicFallback.advance(ctx);
        }

        TurChatFlowState state = ctx.state();
        Map<String, String> variables = ChatFlowOps.readVariables(state);
        String reply = verdict.reply() == null || verdict.reply().isBlank()
                ? ctx.assistantMessage()
                : verdict.reply().trim();

        if (verdict.abandoned()) {
            log.info("[StructuredOutput] User abandoned on node '{}'", node.id());
            ChatFlowOps.writeVariables(state, variables);
            ChatFlowOps.transitionToEnd(state, ctx.graph());
            return reply;
        }

        boolean hasOutputVar = node.outputVariable() != null && !node.outputVariable().isBlank();
        String collected = verdict.collectedValue() == null ? null : verdict.collectedValue().trim();
        boolean hasCollected = collected != null && !collected.isBlank();

        // Defensive fallback: gpt-4o-mini sometimes marks ready_to_advance=true
        // but skips collected_value (especially for short yes/no answers like
        // "sim"). When the model thought we should advance and there IS an
        // output variable to fill, use the user's raw message as the value
        // instead of dropping the turn — better than ending up with an empty
        // variable that breaks downstream conditions.
        if (!hasCollected && hasOutputVar && verdict.readyToAdvance()
                && ctx.userMessage() != null && !ctx.userMessage().isBlank()) {
            collected = ctx.userMessage().trim();
            hasCollected = true;
            log.info("[StructuredOutput] LLM said advance but skipped extraction — "
                    + "using user message '{}' as value for '{}'",
                    collected, node.outputVariable());
        }

        // Normalize obvious yes/no tokens to lowercase so downstream SpEL
        // conditions like `stuffedCrust == 'sim'` match regardless of how the
        // model cased the value.
        if (hasCollected) {
            collected = ChatFlowOps.normalizeYesNo(collected);
        }
        if (hasCollected && hasOutputVar) {
            variables.put(node.outputVariable().trim(), collected);
            log.info("[StructuredOutput] Captured '{}' = '{}'", node.outputVariable(), collected);
        }

        // Trust-but-verify: re-check the validation rule so a single permissive
        // verdict cannot push the flow past a required field (e.g. "pizza" as
        // a phone).
        boolean valid = !hasOutputVar
                || (hasCollected
                        && ChatFlowOps.valueMatchesRule(node.validationRule(), collected));
        if (!verdict.readyToAdvance() || !valid) {
            log.info("[StructuredOutput] Stay on node '{}' (advance={}, valid={})",
                    node.id(), verdict.readyToAdvance(), valid);
            ChatFlowOps.writeVariables(state, variables);
            return reply;
        }

        String previous = node.id();
        String next = ChatFlowOps.advanceToFirstEdge(state, ctx.graph(), node);
        if (!previous.equals(next)) {
            log.info("[StructuredOutput] Advanced '{}' → '{}'", previous, next);
        }
        ChatFlowOps.writeVariables(state, variables);
        return reply;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** Fields extracted from the structured-output JSON. */
    private record StructuredVerdict(String reply, boolean onTopic, String collectedValue,
            boolean readyToAdvance, boolean abandoned) {
    }

    private static StructuredVerdict parseStructuredVerdict(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String json = ChatFlowOps.stripFences(raw);
        int braceStart = json.indexOf('{');
        int braceEnd = json.lastIndexOf('}');
        if (braceStart < 0 || braceEnd <= braceStart) {
            return null;
        }
        json = json.substring(braceStart, braceEnd + 1);
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(json,
                    new TypeReference<Map<String, Object>>() {
                    });
            String reply = parsed.get("reply") instanceof String s ? s : null;
            boolean onTopic = parsed.get("on_topic") instanceof Boolean b ? b : true;
            String collected = parsed.get("collected_value") instanceof String s ? s : null;
            boolean ready = parsed.get("ready_to_advance") instanceof Boolean b && b;
            boolean abandoned = parsed.get("abandoned") instanceof Boolean b && b;
            return new StructuredVerdict(reply, onTopic, collected, ready, abandoned);
        } catch (JacksonException e) {
            log.warn("[StructuredOutput] JSON parse failed: {}", e.getMessage());
            return null;
        }
    }
}

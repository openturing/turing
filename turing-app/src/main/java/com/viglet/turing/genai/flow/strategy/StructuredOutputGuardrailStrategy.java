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

import com.viglet.turing.genai.flow.ChatFlowNode;
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

    private final HeuristicGuardrailStrategy fallback;

    public StructuredOutputGuardrailStrategy(HeuristicGuardrailStrategy fallback) {
        this.fallback = fallback;
    }

    @Override
    public TurChatFlowGuardrailMethod getMethod() {
        return TurChatFlowGuardrailMethod.STRUCTURED_OUTPUT;
    }

    @Override
    public String buildSystemPromptAddendum(ChatFlowNode node, TurChatFlowState state) {
        // Shared base (active-step header + goal/collect/validation + already-collected
        // variables + HARD RULES) lives in ChatFlowOps so wording fixes propagate to
        // every strategy at once. We just bolt the JSON contract on top.
        String base = ChatFlowOps.buildBaseAddendum(node, state);
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
            - collected_value: extract a value of the requested kind. Be permissive
              about FORMAT — bare answers count, no need for a "my name is X" prefix.
              But be SMART about INTENT: conversational requests, intents, greetings,
              or commands like "quero me inscrever", "I want to subscribe", "ajuda",
              "olá", "hi", "yes please" are NEVER values — return null for those even
              when the step asks for a name. A name is a person's name; an email is an
              email address; a phone is a phone number. Do NOT conflate the user's
              intent with a value. When in doubt, return null and let the next turn
              decide.
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
            log.warn("[StructuredOutput] Could not parse JSON, falling back to heuristic");
            return fallback.advance(ctx);
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
        boolean hasCollected = verdict.collectedValue() != null && !verdict.collectedValue().isBlank();
        if (hasCollected && hasOutputVar) {
            variables.put(node.outputVariable().trim(), verdict.collectedValue().trim());
        }

        // Trust-but-verify: gpt-4o-mini happily marks ready_to_advance=true even
        // when collected_value flagrantly fails the validation rule (e.g. "pizza"
        // as a phone). Re-check here so a single permissive verdict cannot push
        // the flow past a required field.
        boolean valid = !hasOutputVar
                || (hasCollected
                        && ChatFlowOps.valueMatchesRule(node.validationRule(), verdict.collectedValue().trim()));
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

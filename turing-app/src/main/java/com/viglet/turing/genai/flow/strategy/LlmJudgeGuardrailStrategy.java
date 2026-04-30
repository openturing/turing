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

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Two-call strategy: same prompt-level contract as
 * {@link HeuristicGuardrailStrategy} (so it shares the addendum), plus a
 * second LLM call after the chat reply that returns a small JSON verdict
 * judging whether the conversation stayed on track.
 *
 * <p>~200 extra tokens per turn while a flow is active. Falls back to the
 * heuristic strategy when the judge call fails or the JSON cannot be
 * parsed.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Slf4j
@Component
public class LlmJudgeGuardrailStrategy implements TurChatFlowGuardrailStrategy {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String NOT_SET = "(none)";

    private final HeuristicGuardrailStrategy fallback;

    public LlmJudgeGuardrailStrategy(HeuristicGuardrailStrategy fallback) {
        this.fallback = fallback;
    }

    @Override
    public TurChatFlowGuardrailMethod getMethod() {
        return TurChatFlowGuardrailMethod.LLM_JUDGE;
    }

    @Override
    public String buildSystemPromptAddendum(ChatFlowNode node, TurChatFlowState state) {
        // Layer 1 (the prompt-side contract) is shared with every other strategy
        // via ChatFlowOps; the judge only adds layer 2 inside `advance`.
        return ChatFlowOps.buildBaseAddendum(node, state);
    }

    @Override
    public String advance(AdvanceContext ctx) {
        ChatFlowNode node = ctx.currentNode();
        if ("end".equals(node.type())) {
            return null;
        }
        ChatModel judgeModel = ctx.auxiliaryModel();
        if (judgeModel == null) {
            log.warn("[LlmJudge] No model available — falling back to heuristic");
            return fallback.advance(ctx);
        }

        JudgeVerdict verdict = askJudge(judgeModel, node, ctx.userMessage(), ctx.assistantMessage());
        if (verdict == null) {
            log.warn("[LlmJudge] No usable verdict — falling back to heuristic");
            return fallback.advance(ctx);
        }

        TurChatFlowState state = ctx.state();
        Map<String, String> variables = ChatFlowOps.readVariables(state);

        // Abandon → end gracefully and use the judge's farewell as the reply.
        if (verdict.abandoned()) {
            log.info("[LlmJudge] Abandon detected on node '{}' — ending flow", node.id());
            ChatFlowOps.writeVariables(state, variables);
            ChatFlowOps.transitionToEnd(state, ctx.graph());
            String farewell = verdict.redirectMessage();
            return (farewell != null && !farewell.isBlank()) ? farewell.trim() : null;
        }

        // Off-topic → stay on the node; substitute the reply only when the
        // judge supplied a polite redirect.
        if (!verdict.onTopic()) {
            ChatFlowOps.writeVariables(state, variables);
            String redirect = verdict.redirectMessage();
            if (redirect != null && !redirect.isBlank()) {
                log.info("[LlmJudge] Off-topic on node '{}' — using judge-supplied redirect", node.id());
                return redirect.trim();
            }
            log.info("[LlmJudge] Off-topic on node '{}' but no redirect supplied — keeping LLM reply",
                    node.id());
            return null;
        }

        boolean hasOutputVar = node.outputVariable() != null && !node.outputVariable().isBlank();
        boolean hasCollected = verdict.collectedValue() != null && !verdict.collectedValue().isBlank();
        if (hasCollected && hasOutputVar) {
            variables.put(node.outputVariable().trim(), verdict.collectedValue().trim());
        }

        // Re-check the validation rule: the judge sometimes marks ready_to_advance
        // even when the collected value clearly fails the rule (e.g. "pizza" as
        // a phone). Treat a rule mismatch as "stay on this step" — the next turn
        // gets another shot.
        boolean valid = !hasOutputVar
                || (hasCollected
                        && ChatFlowOps.valueMatchesRule(node.validationRule(), verdict.collectedValue().trim()));
        if (!verdict.readyToAdvance() || !valid) {
            log.info("[LlmJudge] Stay on node '{}' (advance={}, valid={})",
                    node.id(), verdict.readyToAdvance(), valid);
            ChatFlowOps.writeVariables(state, variables);
            return null;
        }

        String previous = node.id();
        String next = ChatFlowOps.advanceToFirstEdge(state, ctx.graph(), node);
        if (!previous.equals(next)) {
            log.info("[LlmJudge] Advanced '{}' → '{}' on conversation '{}'",
                    previous, next, state.getConversationId());
        }
        ChatFlowOps.writeVariables(state, variables);
        return null;
    }

    // ─────────────────────────── Judge call ───────────────────────────

    /**
     * Single-shot judge call. Returns null when the call fails or the reply
     * cannot be parsed as the expected JSON shape.
     */
    private JudgeVerdict askJudge(ChatModel model,
            ChatFlowNode node,
            String userMessage,
            String assistantMessage) {
        String sys = """
                You are a strict conversation auditor. You receive the active step of a guided
                chat flow plus the most recent user/assistant exchange and decide five things:

                1. on_topic: did the assistant stay on the goal of the active step?
                2. collected_value: did the *user's last message* supply a value the step is
                   collecting? Be permissive about FORMAT — a bare reply that fits the value
                   type counts:
                     - "Alexandre" or "Alexandre Oliveira" for a name
                     - "foo@bar.com" for an email
                     - "+5511999999999" or "11 99999-9999" for a phone
                     - "2026-04-28" or "28/04/2026" for a date
                   The user does NOT need to use a full sentence like "my name is X".
                   But be SMART about INTENT: conversational requests, intents, greetings,
                   or commands like "quero me inscrever", "I want to subscribe", "ajuda",
                   "olá", "hi", "yes please" are NEVER values — return null for those even
                   when the step asks for a name. A name is a person's name; an email is an
                   email address; a phone is a phone number. Do NOT conflate the user's
                   intent with a value. When in doubt, return null.
                3. ready_to_advance: should the conversation move to the next step?
                   true when collected_value is non-null AND, when a validation rule applies,
                   the value matches the rule (a syntactically valid email for the "email"
                   rule, etc.). For steps without an output variable, true when the goal is
                   clearly addressed by the assistant. NEVER true on the same turn where
                   collected_value is null for a step that requires a value.
                4. abandoned: true ONLY when the user clearly wants to quit, cancel, give up
                   on, or skip this flow ("desisto", "cancelar", "quero parar", "no thanks",
                   "I don't want to continue", etc.). When abandoned is true, the flow ends
                   and the conversation continues freely.
                5. redirect_message: written in the SAME LANGUAGE as the user's last message,
                   short and polite, addressed to the user. Use it for:
                   - on_topic=false (not abandoned): a one-sentence nudge back to the goal.
                     Do NOT quote the goal verbatim, do NOT mention internal terms like
                     "step", "goal" or "output variable".
                   - abandoned=true: a graceful goodbye that lets them go without pressure
                     (e.g. "Sem problema, qualquer coisa é só me chamar.").
                   Empty string in all other cases.

                Reply with EXACTLY one JSON object on a single line, no prose, no code fences,
                no explanations. Example:
                {"on_topic": true, "collected_value": "x@y.com", "ready_to_advance": true, "abandoned": false, "redirect_message": ""}

                Active step: %s
                Goal: %s
                Output variable to collect: %s
                Validation rule: %s
                """.formatted(
                ChatFlowOps.safe(node.label()),
                node.aiInstruction() == null ? NOT_SET : node.aiInstruction().trim(),
                node.outputVariable() == null || node.outputVariable().isBlank()
                        ? NOT_SET : node.outputVariable().trim(),
                node.validationRule() == null || node.validationRule().isBlank()
                        ? NOT_SET : node.validationRule().trim());

        String user = "User said: " + (userMessage == null ? "" : userMessage.trim()) + "\n"
                + "Assistant said: " + (assistantMessage == null ? "" : assistantMessage.trim());

        String reply;
        try {
            Prompt prompt = new Prompt(List.of(new SystemMessage(sys), new UserMessage(user)));
            var response = model.call(prompt);
            reply = response.getResult() != null
                    && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null
                            ? response.getResult().getOutput().getText().trim()
                            : "";
        } catch (Exception e) {
            log.warn("[LlmJudge] Judge call failed: {}", e.getMessage());
            return null;
        }
        if (reply.isEmpty()) {
            return null;
        }
        log.info("[LlmJudge] Raw verdict: '{}'", reply);
        return parseJudgeVerdict(reply);
    }

    private static JudgeVerdict parseJudgeVerdict(String reply) {
        String json = ChatFlowOps.stripFences(reply);
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
            boolean onTopic = parsed.get("on_topic") instanceof Boolean b && b;
            String collected = parsed.get("collected_value") instanceof String s ? s : null;
            boolean ready = parsed.get("ready_to_advance") instanceof Boolean b && b;
            String redirect = parsed.get("redirect_message") instanceof String s ? s : null;
            boolean abandoned = parsed.get("abandoned") instanceof Boolean b && b;
            return new JudgeVerdict(onTopic, collected, ready, redirect, abandoned);
        } catch (JacksonException e) {
            log.warn("[LlmJudge] Could not parse JSON '{}': {}", json, e.getMessage());
            return null;
        }
    }

    /**
     * Defensively typed verdict — every field defaults conservatively when
     * absent. {@code redirectMessage} is only used when {@code onTopic} is
     * false or {@code abandoned} is true.
     */
    private record JudgeVerdict(boolean onTopic, String collectedValue, boolean readyToAdvance,
            String redirectMessage, boolean abandoned) {
    }
}

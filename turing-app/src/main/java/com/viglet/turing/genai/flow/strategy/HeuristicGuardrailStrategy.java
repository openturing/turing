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

/**
 * Prompt-only guard rail with regex/keyword heuristics. Cheapest strategy
 * — no extra LLM calls. Also acts as the safety-net fallback for
 * {@code LLM_JUDGE} and {@code STRUCTURED_OUTPUT} when their auxiliary
 * calls fail or produce unparseable output.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Slf4j
@Component
public class HeuristicGuardrailStrategy implements TurChatFlowGuardrailStrategy {

    @Override
    public TurChatFlowGuardrailMethod getMethod() {
        return TurChatFlowGuardrailMethod.HEURISTIC;
    }

    @Override
    public String buildSystemPromptAddendum(ChatFlowNode node, TurChatFlowState state) {
        // Once the flow has reached its END node, drop the guard rail entirely
        // so the user can keep chatting freely with the agent. The captured
        // variables remain in the conversation history, so the LLM still has
        // context if the user follows up about them.
        if ("end".equals(node.type())) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== ACTIVE FLOW STEP: ")
                .append(ChatFlowOps.safe(node.label()))
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
        Map<String, String> variables = ChatFlowOps.readVariables(state);
        if (!variables.isEmpty()) {
            sb.append("Already collected: ").append(variables).append('\n');
        }
        sb.append("""

                Hard rules:
                - Stay strictly on the goal above. Do not answer off-topic questions.
                - Accept short, bare answers as valid replies. If you asked for a name and the
                  user replies with just "Alexandre", that IS the name — acknowledge it and
                  move on. The same applies to emails, phone numbers, dates, etc. Never ask the
                  same question again when the user already supplied a value, even briefly.
                - If the user deviates, briefly redirect them back to this step.
                - Reply only in the language the user used.
                """);
        if (node.outputVariable() != null && !node.outputVariable().isBlank()) {
            sb.append("- Do not advance until a valid ")
                    .append(node.outputVariable().trim())
                    .append(" has been collected.\n");
        }
        return sb.toString();
    }

    @Override
    public String advance(AdvanceContext ctx) {
        ChatFlowNode node = ctx.currentNode();

        // Terminal nodes: nothing to do.
        if ("end".equals(node.type())) {
            return null;
        }

        TurChatFlowState state = ctx.state();
        Map<String, String> variables = ChatFlowOps.readVariables(state);
        boolean shouldAdvance;

        if (node.outputVariable() != null && !node.outputVariable().isBlank()) {
            String collected = ChatFlowOps.extractValue(node.validationRule(), ctx.userMessage());
            if (collected != null) {
                variables.put(node.outputVariable().trim(), collected);
                shouldAdvance = true;
            } else {
                shouldAdvance = false;
            }
        } else {
            // No specific value to collect: advance whenever the assistant
            // produced a substantive reply that mentions something from the
            // node's instruction (very weak signal — fine for step 1).
            shouldAdvance = ChatFlowOps.looksAligned(node.aiInstruction(), ctx.assistantMessage());
        }

        if (!shouldAdvance) {
            log.info("[Heuristic] Staying on node '{}' — no advance signal", node.id());
            ChatFlowOps.writeVariables(state, variables);
            return null;
        }

        String previous = node.id();
        String next = ChatFlowOps.advanceToFirstEdge(state, ctx.graph(), node);
        if (!previous.equals(next)) {
            log.info("[Heuristic] Advancing '{}' → '{}' on conversation '{}'",
                    previous, next, state.getConversationId());
        }
        ChatFlowOps.writeVariables(state, variables);
        return null;
    }
}

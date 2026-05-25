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

    private final TurChatFlowStaticPromptCache staticPromptCache;

    public HeuristicGuardrailStrategy(TurChatFlowStaticPromptCache staticPromptCache) {
        this.staticPromptCache = staticPromptCache;
    }

    @Override
    public TurChatFlowGuardrailMethod getMethod() {
        return TurChatFlowGuardrailMethod.HEURISTIC;
    }

    @Override
    public String buildSystemPromptAddendum(TurChatFlow flow, ChatFlowNode node,
            TurChatFlowState state, ChatFlowGraph graph) {
        // Once the flow has reached its END node, drop the guard rail entirely
        // so the user can keep chatting freely with the agent. The captured
        // variables remain in the conversation history, so the LLM still has
        // context if the user follows up about them.
        // T31 / §IV.5 — the static head and tail of the base addendum
        // are served from the per-(flowId,nodeId) Spring cache; only the
        // "Already collected" line is built per turn.
        String base = staticPromptCache.buildAddendum(flow, node, state, graph, true);
        if (base.isEmpty()) {
            return "";
        }
        if (node.outputVariable() != null && !node.outputVariable().isBlank()) {
            return base + "- Do not advance until a valid "
                    + node.outputVariable().trim()
                    + " has been collected.\n";
        }
        return base;
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
                // T23 / §IV.6 — canonicalize against inlineOptions so the
                // stored value matches the chip label downstream consumers
                // (switch nodes, SpEL conditions, analytics) expect. Same
                // helper used by the LLM judge path.
                collected = ChatFlowOps.canonicalizeAgainstInlineOptions(collected, node.inlineOptions());
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

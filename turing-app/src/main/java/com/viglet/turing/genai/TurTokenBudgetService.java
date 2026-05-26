/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAgentOverBudgetBehavior;

import lombok.extern.slf4j.Slf4j;

/**
 * T123 / §IX.7 — enforceable per-agent prompt-token budget. Called by
 * {@link TurAgentChatExecutor} right after {@link TurChatPromptAssembler}
 * builds the {@code List<Message>} and BEFORE the LLM call. When the agent
 * has {@code maxPromptTokens > 0} and the estimate exceeds the budget, the
 * {@link TurAgentOverBudgetBehavior} configured on the agent decides
 * whether to short-circuit the call (ERROR), log + proceed (WARN), or
 * compact (V1 degrades COMPACT to WARN with a notice — T115 will land
 * the real compression).
 *
 * <p>Estimator: simple {@code chars / 4} heuristic. The OpenAI {@code
 * cl100k_base} tokenizer averages ~4 chars/token across English+code;
 * Anthropic / Gemini are within ±15%. A precise per-provider count would
 * need {@code jtokkit} or a vendor SDK call — for a budget check (where
 * "16k ± 1k" is the decision boundary) the heuristic is good enough and
 * keeps the path zero-allocation per Message.
 *
 * <p>Why not use Spring AI's token estimator: Spring AI 2.0.0-M2 ships
 * {@code JTokkitTokenCountEstimator} only as a fragment of
 * {@code MessageWindowChatMemory}; pulling it directly would couple the
 * budget check to the chat-memory module's lifecycle. The 4-chars/token
 * approximation is provider-agnostic and removes the dep.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurTokenBudgetService {

    /** Outcome of a budget check. */
    public enum Decision {
        /** Budget disabled or prompt fits. */
        WITHIN_BUDGET,
        /** Over budget; logged + proceeding. */
        WARN,
        /** Over budget; caller must short-circuit (throw or surface error). */
        ERROR
    }

    /** Result envelope returned by {@link #check(TurAIAgent, List)}. */
    public record CheckResult(Decision decision, int estimatedTokens, int budget) {
    }

    /**
     * Estimates the prompt token count and applies the agent's
     * over-budget behaviour. Always returns a {@link CheckResult} — the
     * caller branches on {@link Decision} (ERROR is the only one that
     * requires aborting the LLM call).
     */
    public CheckResult check(TurAIAgent agent, List<Message> messages) {
        int budget = agent == null ? 0 : agent.getMaxPromptTokens();
        if (budget <= 0) {
            return new CheckResult(Decision.WITHIN_BUDGET, 0, 0);
        }
        int estimate = estimateTokens(messages);
        if (estimate <= budget) {
            return new CheckResult(Decision.WITHIN_BUDGET, estimate, budget);
        }
        TurAgentOverBudgetBehavior behavior = agent.getOverBudgetBehavior() != null
                ? agent.getOverBudgetBehavior()
                : TurAgentOverBudgetBehavior.WARN;
        return switch (behavior) {
            case ERROR -> {
                log.warn("[TokenBudget] agent='{}' prompt over budget: estimate={} > max={} — ERROR mode, aborting LLM call",
                        agent.getId(), estimate, budget);
                yield new CheckResult(Decision.ERROR, estimate, budget);
            }
            case COMPACT -> {
                // V1: T115 workspace-backed compression not shipped yet —
                // degrade to WARN with a notice so operators know what to
                // expect on rollout.
                log.warn("[TokenBudget] agent='{}' prompt over budget: estimate={} > max={} — COMPACT requested but T115 not yet shipped; proceeding as WARN",
                        agent.getId(), estimate, budget);
                yield new CheckResult(Decision.WARN, estimate, budget);
            }
            case WARN -> {
                log.warn("[TokenBudget] agent='{}' prompt over budget: estimate={} > max={} — WARN mode, proceeding",
                        agent.getId(), estimate, budget);
                yield new CheckResult(Decision.WARN, estimate, budget);
            }
        };
    }

    /**
     * Total character count of every message divided by 4 — the
     * commonly-cited average for OpenAI's {@code cl100k_base}, close
     * enough for Anthropic and Gemini for a budget check. Pure function,
     * exposed for tests + observability.
     */
    public static int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        long chars = 0;
        for (Message m : messages) {
            String text = m.getText();
            if (text != null) chars += text.length();
        }
        // chars/4 with ceiling so a 3-char message still counts as 1
        // token instead of 0 — keeps the integer pipeline honest.
        return (int) Math.min(Integer.MAX_VALUE, (chars + 3L) / 4L);
    }
}

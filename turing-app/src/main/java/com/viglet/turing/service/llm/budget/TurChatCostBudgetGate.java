/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.budget;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T291 / §XVI.3 (Block L) — the turn-time soft budget gate. Generalizes the
 * F.13 native-path cost guard to the provider-agnostic Spring AI path: it reads
 * the frozen per-turn {@code cost_usd} captured by T289, so it works on OpenAI,
 * Anthropic, Gemini, Ollama and embedded ($0) alike.
 *
 * <p>Two soft enforcement points, both opt-in per agent and neither ever aborts
 * a turn (a paying conversation is never dropped mid-flight):
 *
 * <ol>
 *   <li><b>Monthly budget (pre-call).</b> {@link #resolveInstance} sums the
 *       agent's month-to-date spend; once it reaches
 *       {@link TurAIAgent#monthlyBudgetUsd} it either swaps the turn to the
 *       configured cheaper {@link TurAIAgent#budgetDowngradeLlmId downgrade
 *       LLM} or logs a warning and proceeds on the original model.</li>
 *   <li><b>Per-turn cap (post-call).</b> {@link #warnIfTurnOverCap} logs when a
 *       completed turn's cost exceeds {@link TurAIAgent#perTurnSoftCapUsd} —
 *       an operator signal that one turn was unusually expensive.</li>
 * </ol>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurChatCostBudgetGate {

    private final TurLLMTokenUsageRepository tokenUsageRepository;
    private final TurLLMInstanceRepository llmInstanceRepository;

    public TurChatCostBudgetGate(TurLLMTokenUsageRepository tokenUsageRepository,
            TurLLMInstanceRepository llmInstanceRepository) {
        this.tokenUsageRepository = tokenUsageRepository;
        this.llmInstanceRepository = llmInstanceRepository;
    }

    /**
     * Resolve the LLM instance to actually use for this turn, applying the
     * monthly soft budget. Returns {@code current} unchanged when the gate is
     * disabled, the agent is under budget, or no usable downgrade is configured.
     * Best-effort — any failure degrades to "use the original instance".
     */
    public TurLLMInstance resolveInstance(TurAIAgent agent, TurLLMInstance current) {
        try {
            if (agent == null || current == null || agent.getId() == null) {
                return current;
            }
            Double budget = agent.getMonthlyBudgetUsd();
            if (budget == null || budget <= 0) {
                return current; // gate off
            }
            LocalDateTime monthStart = LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1).atStartOfDay();
            double spent = tokenUsageRepository.sumCostByAgentSince(agent.getId(), monthStart);
            if (spent < budget) {
                return current; // under budget
            }

            String downgradeId = agent.getBudgetDowngradeLlmId();
            if (downgradeId != null && !downgradeId.isBlank()
                    && !downgradeId.equals(current.getId())) {
                Optional<TurLLMInstance> downgrade = llmInstanceRepository.findById(downgradeId);
                if (downgrade.isPresent()) {
                    log.warn("[BudgetGate] agent '{}' over monthly budget "
                            + "(spent ${} >= cap ${}) — downgrading LLM '{}' -> '{}'",
                            agent.getId(), spent, budget,
                            current.getTitle(), downgrade.get().getTitle());
                    return downgrade.get();
                }
                log.warn("[BudgetGate] agent '{}' over monthly budget but downgrade LLM '{}' "
                        + "not found — proceeding on '{}'", agent.getId(), downgradeId,
                        current.getTitle());
                return current;
            }

            log.warn("[BudgetGate] agent '{}' over monthly budget (spent ${} >= cap ${}) — "
                    + "warn-only (no downgrade configured), proceeding on '{}'",
                    agent.getId(), spent, budget, current.getTitle());
            return current;
        } catch (RuntimeException e) {
            log.debug("[BudgetGate] monthly budget check failed for agent '{}': {}",
                    agent == null ? null : agent.getId(), e.getMessage());
            return current;
        }
    }

    /**
     * Post-turn per-turn soft-cap check: log a warning when a completed turn's
     * cost exceeds the agent's {@code perTurnSoftCapUsd}. No-op when disabled.
     */
    public void warnIfTurnOverCap(TurAIAgent agent, double turnCostUsd) {
        if (agent == null) {
            return;
        }
        Double cap = agent.getPerTurnSoftCapUsd();
        if (cap != null && cap > 0 && turnCostUsd > cap) {
            log.warn("[BudgetGate] agent '{}' turn cost ${} exceeded per-turn soft cap ${}",
                    agent.getId(), turnCostUsd, cap);
        }
    }
}

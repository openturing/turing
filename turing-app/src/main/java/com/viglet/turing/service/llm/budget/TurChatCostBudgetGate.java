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
import com.viglet.turing.persistence.model.gateway.TurGatewayKey;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;
import com.viglet.turing.properties.TurAbuseControlProperty;

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
    private final TurAbuseControlProperty abuseControlProperty;

    public TurChatCostBudgetGate(TurLLMTokenUsageRepository tokenUsageRepository,
            TurLLMInstanceRepository llmInstanceRepository,
            TurAbuseControlProperty abuseControlProperty) {
        this.tokenUsageRepository = tokenUsageRepository;
        this.llmInstanceRepository = llmInstanceRepository;
        this.abuseControlProperty = abuseControlProperty;
    }

    /**
     * T641 / §XXXVII.3 — the <b>hard</b> anonymous-chat kill-switch (distinct
     * from the soft monthly/per-turn gates above, which never abort). When
     * {@code turing.abuse.chat.hard-monthly-cap-usd > 0} and total month-to-date
     * LLM spend across all agents reaches it, this returns {@code true} and the
     * anonymous SN chat path refuses the turn <em>before</em> any upstream call
     * fires. Best-effort: any failure evaluates to "not exceeded" so a metering
     * hiccup never blocks legitimate chat.
     *
     * @return {@code true} when the anonymous turn must be refused.
     */
    public boolean isAnonymousChatHardCapExceeded() {
        try {
            double cap = abuseControlProperty.getChat().getHardMonthlyCapUsd();
            if (cap <= 0) {
                return false; // kill-switch off
            }
            LocalDateTime monthStart = LocalDate.now(ZoneId.systemDefault())
                    .withDayOfMonth(1).atStartOfDay();
            double spent = tokenUsageRepository.sumCostSince(monthStart);
            if (spent >= cap) {
                log.warn("[BudgetGate] anonymous-chat HARD cap reached (spent ${} >= cap ${}) — "
                        + "refusing new anonymous conversation", spent, cap);
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            log.debug("[BudgetGate] hard-cap check failed: {}", e.getMessage());
            return false;
        }
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
     * T742 / §XLIX — per-key <b>hard</b> month-to-date kill-switch for the
     * Governed LLM Gateway. When {@code key.hardMonthlyCapUsd > 0} and the key's
     * month-to-date spend reaches it, returns {@code true} and the gateway refuses
     * the call before any upstream LLM request. Best-effort → "not exceeded" on
     * any failure.
     */
    public boolean isKeyHardCapExceeded(TurGatewayKey key) {
        try {
            if (key == null || key.getId() == null) {
                return false;
            }
            Double cap = key.getHardMonthlyCapUsd();
            if (cap == null || cap <= 0) {
                return false;
            }
            LocalDateTime monthStart = LocalDate.now(ZoneId.systemDefault())
                    .withDayOfMonth(1).atStartOfDay();
            double spent = tokenUsageRepository.sumCostByKeySince(key.getId(), monthStart);
            if (spent >= cap) {
                log.warn("[BudgetGate] gateway key '{}' HARD cap reached (spent ${} >= cap ${}) — "
                        + "refusing call", key.getId(), spent, cap);
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            log.debug("[BudgetGate] key hard-cap check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * T742 / §XLIX — per-key monthly soft budget + auto-downgrade, mirroring the
     * per-agent {@link #resolveInstance}. Returns {@code current} unchanged when
     * the key has no soft budget, is under budget, or has no usable downgrade
     * LLM. Best-effort — degrades to the original instance on any failure.
     */
    public TurLLMInstance resolveInstanceForKey(TurGatewayKey key, TurLLMInstance current) {
        try {
            if (key == null || current == null || key.getId() == null) {
                return current;
            }
            Double budget = key.getMonthlyBudgetUsd();
            if (budget == null || budget <= 0) {
                return current;
            }
            LocalDateTime monthStart = LocalDate.now(ZoneId.systemDefault())
                    .withDayOfMonth(1).atStartOfDay();
            double spent = tokenUsageRepository.sumCostByKeySince(key.getId(), monthStart);
            if (spent < budget) {
                return current;
            }
            String downgradeId = key.getBudgetDowngradeLlmId();
            if (downgradeId != null && !downgradeId.isBlank() && !downgradeId.equals(current.getId())) {
                Optional<TurLLMInstance> downgrade = llmInstanceRepository.findById(downgradeId);
                if (downgrade.isPresent()) {
                    log.warn("[BudgetGate] gateway key '{}' over monthly budget "
                            + "(spent ${} >= cap ${}) — downgrading '{}' -> '{}'",
                            key.getId(), spent, budget, current.getTitle(), downgrade.get().getTitle());
                    return downgrade.get();
                }
            }
            log.warn("[BudgetGate] gateway key '{}' over monthly budget (spent ${} >= cap ${}) — "
                    + "warn-only, proceeding on '{}'", key.getId(), spent, budget, current.getTitle());
            return current;
        } catch (RuntimeException e) {
            log.debug("[BudgetGate] key monthly budget check failed: {}", e.getMessage());
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

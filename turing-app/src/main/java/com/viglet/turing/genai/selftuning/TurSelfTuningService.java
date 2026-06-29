/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.selftuning;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.eval.TurAgentEvalRunnerService;
import com.viglet.turing.genai.selftuning.TurSelfTuningMiner.FailureSummary;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAgentSuggestion;
import com.viglet.turing.persistence.repository.agent.TurAgentSuggestionRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T447 / §XXIII.6 — the self-tuning loop for one agent: mine recent failures →
 * draft a revised system prompt → score the current vs proposed prompt against
 * the agent's golden sets (Block K) → open a PR-style suggestion ONLY if the
 * proposal scores better. Never applies the change (human approval required).
 *
 * <p>Pure composition: the mining, drafting and scoring are delegated, so this
 * class only owns the gating decision and is fully unit-testable with mocks.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurSelfTuningService {

    /** Proposed must beat baseline by at least this margin to be worth a suggestion. */
    static final double IMPROVEMENT_EPSILON = 0.01d;

    private final TurSelfTuningMiner miner;
    private final TurSelfTuningDrafter drafter;
    private final TurAgentEvalRunnerService evalRunner;
    private final TurAgentSuggestionRepository suggestionRepository;

    public TurSelfTuningService(TurSelfTuningMiner miner, TurSelfTuningDrafter drafter,
            TurAgentEvalRunnerService evalRunner,
            TurAgentSuggestionRepository suggestionRepository) {
        this.miner = miner;
        this.drafter = drafter;
        this.evalRunner = evalRunner;
        this.suggestionRepository = suggestionRepository;
    }

    /**
     * Run one tuning cycle for the agent. Returns the persisted suggestion when a
     * better prompt was found, else empty (no golden set, no failures, no draft,
     * or no improvement).
     */
    public Optional<TurAgentSuggestion> runCycle(TurAIAgent agent) {
        String agentId = agent.getId();
        // Need a golden set + LLM to score against, or there's no gate.
        if (!evalRunner.isAvailable(agentId)) {
            log.info("[SelfTuning] agent={} skipped — no golden set / LLM to score against", agentId);
            return Optional.empty();
        }
        FailureSummary failures = miner.mine(agentId);
        if (failures.isEmpty()) {
            log.info("[SelfTuning] agent={} no recent failures — nothing to tune", agentId);
            return Optional.empty();
        }
        Optional<String> proposed = drafter.draftSystemPrompt(agent, failures.digest());
        if (proposed.isEmpty()) {
            log.info("[SelfTuning] agent={} drafter produced no change", agentId);
            return Optional.empty();
        }
        double baseline = evalRunner.scoreWithSystemPrompt(agentId, null);
        if (baseline == TurAgentEvalRunnerService.SCORE_UNAVAILABLE) {
            return Optional.empty();
        }
        double proposedScore = evalRunner.scoreWithSystemPrompt(agentId, proposed.get());
        if (proposedScore <= baseline + IMPROVEMENT_EPSILON) {
            log.info("[SelfTuning] agent={} proposal not better (baseline={}, proposed={}) — discarded",
                    agentId, baseline, proposedScore);
            return Optional.empty();
        }
        return Optional.of(persist(agent, failures, proposed.get(), baseline, proposedScore));
    }

    private TurAgentSuggestion persist(TurAIAgent agent, FailureSummary failures, String proposed,
            double baseline, double proposedScore) {
        TurAgentSuggestion suggestion = new TurAgentSuggestion();
        suggestion.setAgentId(agent.getId());
        suggestion.setKind(TurAgentSuggestion.Kind.SYSTEM_PROMPT);
        suggestion.setCurrentValue(agent.getSystemPrompt());
        suggestion.setProposedValue(proposed);
        suggestion.setRationale("Drafted from %d failed session(s) in the last 7 days. "
                .formatted(failures.count())
                + "Scored %.3f vs current %.3f on the golden sets.".formatted(proposedScore, baseline));
        suggestion.setBaselineScore(baseline);
        suggestion.setProposedScore(proposedScore);
        suggestion.setStatus(TurAgentSuggestion.Status.PENDING);
        suggestion.setCreatedAt(System.currentTimeMillis());
        TurAgentSuggestion saved = suggestionRepository.save(suggestion);
        log.info("[SelfTuning] agent={} opened suggestion {} (baseline={}, proposed={})",
                agent.getId(), saved.getId(), baseline, proposedScore);
        return saved;
    }
}

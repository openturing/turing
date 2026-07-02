/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.distillation;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAgentSuggestion;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAgentSuggestionRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * T191 / §X.15.e — the "propose, don't apply" half of the overnight
 * self-improvement flywheel. Where T169 distillation auto-swaps a passing
 * candidate, the overnight run instead opens a PR-style
 * {@link TurAgentSuggestion}{@code (DISTILLATION_CANDIDATE)} an admin approves —
 * reusing the same T447 suggestion surface as the self-tuning prompt loop.
 *
 * <p>This service owns the two suggestion-side operations, kept out of
 * {@link TurOpenAiDistillationService} so they are unit-testable without the
 * OpenAI client: {@link #createProposal} (open the suggestion) and
 * {@link #applyCandidate} (the approve-time model swap, resolving the agent's
 * eval LLM instance the same way distillation does).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurDistillationProposalService {

    private final TurAgentSuggestionRepository suggestionRepository;
    private final TurAIAgentRepository agentRepository;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurGlobalSettingsService globalSettingsService;

    public TurDistillationProposalService(TurAgentSuggestionRepository suggestionRepository,
            TurAIAgentRepository agentRepository,
            TurLLMInstanceRepository llmInstanceRepository,
            TurGlobalSettingsService globalSettingsService) {
        this.suggestionRepository = suggestionRepository;
        this.agentRepository = agentRepository;
        this.llmInstanceRepository = llmInstanceRepository;
        this.globalSettingsService = globalSettingsService;
    }

    /**
     * Open a {@code DISTILLATION_CANDIDATE} suggestion for the agent: the
     * overnight fine-tune beat the incumbent on the eval gate and is offered for
     * approval (never auto-applied).
     */
    public TurAgentSuggestion createProposal(String agentId, String originalModel,
            String fineTunedModel, double baselineScore, double proposedScore, Integer exampleCount,
            long nowMillis) {
        TurAgentSuggestion suggestion = new TurAgentSuggestion();
        suggestion.setAgentId(agentId);
        suggestion.setKind(TurAgentSuggestion.Kind.DISTILLATION_CANDIDATE);
        suggestion.setCurrentValue(originalModel);
        suggestion.setProposedValue(fineTunedModel);
        suggestion.setBaselineScore(baselineScore);
        suggestion.setProposedScore(proposedScore);
        suggestion.setStatus(TurAgentSuggestion.Status.PENDING);
        suggestion.setCreatedAt(nowMillis);
        suggestion.setRationale(String.format(
                "Overnight distillation of %s production example(s) produced model '%s'. "
                        + "It scored %.3f on the eval gate vs the incumbent '%s' at %.3f. "
                        + "Approve to swap the agent's LLM instance model to the candidate.",
                exampleCount == null ? 0 : exampleCount, fineTunedModel, proposedScore,
                originalModel, baselineScore));
        TurAgentSuggestion saved = suggestionRepository.save(suggestion);
        log.info("[Overnight] opened distillation suggestion {} for agent {} ({} -> {}, {} vs {})",
                saved.getId(), agentId, originalModel, fineTunedModel, proposedScore, baselineScore);
        return saved;
    }

    /**
     * Apply an approved candidate: swap the agent's eval LLM instance model to
     * {@code fineTunedModel}. Resolves the instance the same way the distillation
     * pipeline did (first enabled agent LLM, else the default LLM).
     *
     * @return true when the model was swapped; false when no instance resolved
     */
    public boolean applyCandidate(String agentId, String fineTunedModel) {
        if (!StringUtils.hasText(fineTunedModel)) {
            return false;
        }
        TurAIAgent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return false;
        }
        TurLLMInstance instance = resolveLlm(agent);
        if (instance == null) {
            log.warn("[Overnight] cannot apply candidate for agent {} — no eval LLM instance resolved",
                    agentId);
            return false;
        }
        instance.setModelName(fineTunedModel);
        llmInstanceRepository.save(instance);
        log.info("[Overnight] applied distillation candidate '{}' to instance '{}' (agent {})",
                fineTunedModel, instance.getId(), agentId);
        return true;
    }

    /** Same resolution the distillation pipeline uses: first enabled agent LLM, else the default. */
    private TurLLMInstance resolveLlm(TurAIAgent agent) {
        if (agent.getLlmInstances() != null) {
            Optional<TurLLMInstance> agentLlm = agent.getLlmInstances().stream()
                    .filter(l -> l.getEnabled() == 1)
                    .findFirst();
            if (agentLlm.isPresent()) {
                return agentLlm.get();
            }
        }
        String defaultLlmId = globalSettingsService.getDefaultLlmId();
        if (!StringUtils.hasText(defaultLlmId)) {
            return null;
        }
        return llmInstanceRepository.findById(defaultLlmId)
                .filter(l -> l.getEnabled() == 1)
                .orElse(null);
    }
}

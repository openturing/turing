/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.agent;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAgentSuggestion;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAgentSuggestionRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T447 / §XXIII.6 — admin surface for the self-tuning loop's PR-style suggestions.
 * List the suggestions an agent's self-tuning loop opened, then approve (apply the
 * proposed system prompt to the agent) or reject. Approval is the ONLY path that
 * mutates the agent — the loop itself never auto-applies.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@RestController
@RequestMapping("/api/ai-agent/{agentId}/suggestion")
public class TurAgentSuggestionAPI {

    private final TurAgentSuggestionRepository suggestionRepository;
    private final TurAIAgentRepository agentRepository;
    private final com.viglet.turing.genai.distillation.TurDistillationProposalService proposalService;

    public TurAgentSuggestionAPI(TurAgentSuggestionRepository suggestionRepository,
            TurAIAgentRepository agentRepository,
            com.viglet.turing.genai.distillation.TurDistillationProposalService proposalService) {
        this.suggestionRepository = suggestionRepository;
        this.agentRepository = agentRepository;
        this.proposalService = proposalService;
    }

    @GetMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<TurAgentSuggestion> list(@PathVariable String agentId) {
        return suggestionRepository.findByAgentIdOrderByCreatedAtDesc(agentId);
    }

    @PostMapping("/{id}/approve")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    @Transactional
    public TurAgentSuggestion approve(@PathVariable String agentId, @PathVariable String id) {
        TurAgentSuggestion suggestion = require(agentId, id);
        if (suggestion.getStatus() != TurAgentSuggestion.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Suggestion already decided");
        }
        applyToAgent(agentId, suggestion);
        suggestion.setStatus(TurAgentSuggestion.Status.APPROVED);
        suggestion.setDecidedAt(System.currentTimeMillis());
        return suggestionRepository.save(suggestion);
    }

    @PostMapping("/{id}/reject")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    @Transactional
    public TurAgentSuggestion reject(@PathVariable String agentId, @PathVariable String id) {
        TurAgentSuggestion suggestion = require(agentId, id);
        suggestion.setStatus(TurAgentSuggestion.Status.REJECTED);
        suggestion.setDecidedAt(System.currentTimeMillis());
        return suggestionRepository.save(suggestion);
    }

    @DeleteMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public boolean delete(@PathVariable String agentId, @PathVariable String id) {
        require(agentId, id);
        suggestionRepository.deleteById(id);
        return true;
    }

    private void applyToAgent(String agentId, TurAgentSuggestion suggestion) {
        switch (suggestion.getKind()) {
            case SYSTEM_PROMPT -> {
                TurAIAgent agent = agentRepository.findById(agentId)
                        .orElseThrow(() ->
                                new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
                agent.setSystemPrompt(suggestion.getProposedValue());
                agentRepository.save(agent);
                log.info("[SelfTuning] applied prompt suggestion {} to agent {}",
                        suggestion.getId(), agentId);
            }
            // T191 / §X.15.e — swap the agent's eval LLM instance model to the
            // overnight-distilled candidate (resolved the same way distillation did).
            case DISTILLATION_CANDIDATE -> {
                boolean applied = proposalService.applyCandidate(agentId, suggestion.getProposedValue());
                if (!applied) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Could not apply the distilled model — no eval LLM instance resolved");
                }
                log.info("[Overnight] applied distillation suggestion {} to agent {}",
                        suggestion.getId(), agentId);
            }
        }
    }

    private TurAgentSuggestion require(String agentId, String id) {
        TurAgentSuggestion suggestion = suggestionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suggestion not found"));
        if (!agentId.equals(suggestion.getAgentId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Suggestion not found for agent");
        }
        return suggestion;
    }
}

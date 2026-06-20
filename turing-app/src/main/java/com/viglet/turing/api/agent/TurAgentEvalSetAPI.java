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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurAgentEvalSet;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAgentEvalSetRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T285 / §XV.1 — agent-scoped CRUD for golden {@link TurAgentEvalSet}s. Each
 * set carries its {@link TurAgentEvalCase} list inline (cascade-persisted),
 * so the editor can author the whole aggregate in one round-trip.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/ai-agent/{agentId}/eval-set")
@Tag(name = "Agent Eval Set", description = "AI Agent golden-set regression cases (Agent CI)")
public class TurAgentEvalSetAPI {

    private final TurAgentEvalSetRepository evalSetRepository;
    private final TurAIAgentRepository agentRepository;

    public TurAgentEvalSetAPI(TurAgentEvalSetRepository evalSetRepository,
            TurAIAgentRepository agentRepository) {
        this.evalSetRepository = evalSetRepository;
        this.agentRepository = agentRepository;
    }

    @Operation(summary = "List the agent's eval sets")
    @GetMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<TurAgentEvalSet> list(@PathVariable String agentId) {
        return evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(agentId);
    }

    @Operation(summary = "Show an eval set")
    @GetMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurAgentEvalSet get(@PathVariable String agentId, @PathVariable String id) {
        return evalSetRepository.findById(id)
                .filter(s -> belongsToAgent(s, agentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Eval set not found: " + id));
    }

    @Operation(summary = "Create an eval set")
    @PostMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE" })
    @Transactional
    public TurAgentEvalSet add(@PathVariable String agentId, @RequestBody TurAgentEvalSetRequest body) {
        TurAIAgent agent = requireAgent(agentId);
        TurAgentEvalSet set = new TurAgentEvalSet();
        set.setTurAIAgent(agent);
        applyScalars(set, body);
        replaceCases(set, body);
        return evalSetRepository.save(set);
    }

    @Operation(summary = "Update an eval set")
    @PutMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    @Transactional
    public TurAgentEvalSet update(@PathVariable String agentId, @PathVariable String id,
            @RequestBody TurAgentEvalSetRequest body) {
        TurAgentEvalSet set = evalSetRepository.findById(id)
                .filter(s -> belongsToAgent(s, agentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Eval set not found: " + id));
        applyScalars(set, body);
        replaceCases(set, body);
        return evalSetRepository.save(set);
    }

    @Operation(summary = "Delete an eval set")
    @DeleteMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_DELETE" })
    @Transactional
    public boolean delete(@PathVariable String agentId, @PathVariable String id) {
        evalSetRepository.findById(id)
                .filter(s -> belongsToAgent(s, agentId))
                .ifPresent(evalSetRepository::delete);
        return true;
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private TurAIAgent requireAgent(String agentId) {
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId));
    }

    private static boolean belongsToAgent(TurAgentEvalSet set, String agentId) {
        return set.getTurAIAgent() != null && agentId.equals(set.getTurAIAgent().getId());
    }

    private static void applyScalars(TurAgentEvalSet set, TurAgentEvalSetRequest body) {
        set.setName(body.name());
        set.setDescription(body.description());
        set.setEnabled(body.enabled());
        set.setBlocking(body.blocking());
    }

    /**
     * Replaces the set's cases with the incoming ones, wiring the back-ref and
     * a stable sort order. orphanRemoval drops cases the client dropped. Each
     * case is built fresh from its request DTO so JPA assigns the id on insert
     * and the aggregate stays self-consistent on every save.
     */
    private static void replaceCases(TurAgentEvalSet set, TurAgentEvalSetRequest body) {
        set.getCases().clear();
        if (body.cases() == null) {
            return;
        }
        int order = 0;
        for (TurAgentEvalCaseRequest source : body.cases()) {
            TurAgentEvalCase incoming = new TurAgentEvalCase();
            incoming.setName(source.name());
            incoming.setDescription(source.description());
            incoming.setSeedTurnsJson(source.seedTurnsJson());
            incoming.setExpectedSlotsJson(source.expectedSlotsJson());
            incoming.setExpectedOutcome(source.expectedOutcome() == null
                    ? TurAgentEvalExpectedOutcome.ANY : source.expectedOutcome());
            incoming.setExpectedNodeId(source.expectedNodeId());
            incoming.setRubric(source.rubric());
            incoming.setTurAgentEvalSet(set);
            incoming.setSortOrder(order++);
            set.getCases().add(incoming);
        }
    }
}

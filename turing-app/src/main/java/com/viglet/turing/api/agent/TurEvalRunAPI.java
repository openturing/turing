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

import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.eval.TurAgentEvalRunnerService;
import com.viglet.turing.persistence.dto.agent.TurAgentEvalReportDto;
import com.viglet.turing.persistence.dto.agent.TurEvalRunRequest;
import com.viglet.turing.persistence.dto.agent.TurEvalRunResultDto;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.agent.TurEvalGraderStack;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalGraderStackRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T602 / §XXXIII.17 — the public eval-run API: runs a named {@code dataset ×
 * grader stack} against an agent runtime and returns a CI-gate verdict. This is
 * the server side of {@code turing eval --dataset} (T125–T127) and the SDK
 * {@code runEval}, and turns the two hard-wired eval islands into one reusable
 * CI gate over <b>any</b> dataset (extends the Agent-CI gate of T428/T430).
 *
 * <p>Both {@code dataset} and {@code graderStack} accept an id <b>or</b> a human
 * name (resolved id-first, then by name). The run reuses the exact per-case
 * replay + grader-stack scoring of {@link TurAgentEvalRunnerService}; the report
 * is persisted for the Eval Studio timeline but never touches the agent's golden
 * baseline.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/eval/run")
@Tag(name = "Eval Run", description = "Public dataset × grader-stack CI gate")
public class TurEvalRunAPI {

    private final TurAgentEvalRunnerService runnerService;
    private final TurEvalDatasetRepository datasetRepository;
    private final TurEvalGraderStackRepository graderStackRepository;

    public TurEvalRunAPI(TurAgentEvalRunnerService runnerService,
            TurEvalDatasetRepository datasetRepository,
            TurEvalGraderStackRepository graderStackRepository) {
        this.runnerService = runnerService;
        this.datasetRepository = datasetRepository;
        this.graderStackRepository = graderStackRepository;
    }

    @Operation(summary = "Run a named dataset × grader stack against an agent (CI gate)")
    @PostMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurEvalRunResultDto run(@RequestBody TurEvalRunRequest request) {
        if (request.agentId() == null || request.agentId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "agentId is required");
        }
        if (request.dataset() == null || request.dataset().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dataset (id or name) is required");
        }
        TurEvalDataset dataset = resolveDataset(request.dataset());
        String graderStackId = resolveGraderStackId(request.graderStack());

        TurAgentEvalReportDto report =
                runnerService.runDataset(request.agentId(), dataset.getId(), graderStackId);
        if (report.error() != null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, report.error());
        }

        boolean gatePassed = report.passed()
                && (request.minScore() == null || report.score() >= request.minScore());
        return new TurEvalRunResultDto(gatePassed, request.minScore(), dataset.getId(),
                dataset.getName(), graderStackId, report);
    }

    /** Resolves a dataset by id first, then by human name. 404 when neither hits. */
    private TurEvalDataset resolveDataset(String idOrName) {
        return datasetRepository.findById(idOrName)
                .or(() -> datasetRepository.findFirstByNameOrderByCreatedAtDesc(idOrName))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dataset not found (id or name): " + idOrName));
    }

    /**
     * Resolves an optional grader stack by id first, then by name. A blank /
     * null value means "use the default stack" and resolves to {@code null}.
     */
    private String resolveGraderStackId(String idOrName) {
        if (idOrName == null || idOrName.isBlank()) {
            return null;
        }
        return graderStackRepository.findById(idOrName)
                .or(() -> graderStackRepository.findFirstByName(idOrName))
                .map(TurEvalGraderStack::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Grader stack not found (id or name): " + idOrName));
    }
}

/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.dto.agent.TurAgentEvalCaseResultDto;
import com.viglet.turing.persistence.dto.agent.TurAgentEvalGateDto;
import com.viglet.turing.persistence.dto.agent.TurAgentEvalGateDto.Finding;
import com.viglet.turing.persistence.dto.agent.TurAgentEvalReportDto;
import com.viglet.turing.persistence.model.agent.TurAgentEvalReport;
import com.viglet.turing.persistence.model.agent.TurAgentEvalSet;
import com.viglet.turing.persistence.repository.agent.TurAgentEvalReportRepository;
import com.viglet.turing.persistence.repository.agent.TurAgentEvalSetRepository;

/**
 * T287 / §XV.3 — computes the pre-publish gate status for an agent from its
 * most recent eval {@link TurAgentEvalReport}, comparing against the green
 * baseline. The result is rendered in the flow editor's Lint UI panel (same
 * affordance as the T94 chat-flow linter), and consulted by the chat-flow
 * publish endpoint to optionally hard-block a regressing change.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurAgentEvalGateService {

    private final TurAgentEvalSetRepository evalSetRepository;
    private final TurAgentEvalReportRepository reportRepository;

    public TurAgentEvalGateService(TurAgentEvalSetRepository evalSetRepository,
            TurAgentEvalReportRepository reportRepository) {
        this.evalSetRepository = evalSetRepository;
        this.reportRepository = reportRepository;
    }

    /** Builds the gate status surfaced in the editor. */
    public TurAgentEvalGateDto gate(String agentId) {
        List<TurAgentEvalSet> enabledSets = evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(agentId).stream()
                .filter(s -> s.getEnabled() == 1 && !s.getCases().isEmpty())
                .toList();
        if (enabledSets.isEmpty()) {
            return TurAgentEvalGateDto.notConfigured();
        }
        boolean blocking = enabledSets.stream().anyMatch(s -> s.getBlocking() == 1);

        Optional<TurAgentEvalReport> latestOpt =
                reportRepository.findFirstByTurAIAgent_IdOrderByCreatedAtDesc(agentId);
        if (latestOpt.isEmpty()) {
            return new TurAgentEvalGateDto("NEVER_RUN", blocking, null, List.of(new Finding(
                    "INFO", "eval_never_run",
                    "This agent has an eval set but the gate has never been run.",
                    "Run the eval gate to establish a green baseline before publishing.")));
        }
        TurAgentEvalReport latest = latestOpt.get();
        TurAgentEvalReportDto reportDto = toDto(latest);

        if (latest.isPassed()) {
            return new TurAgentEvalGateDto("GREEN", blocking, reportDto, List.of());
        }

        List<Finding> findings = new ArrayList<>();
        List<TurAgentEvalCaseResultDto> results = TurAgentEvalRunnerService.parseResults(latest.getResultsJson());
        String severity = latest.isRegressed() ? "ERROR" : "WARNING";
        String status = latest.isRegressed() ? "REGRESSED" : "RED";
        if (latest.isRegressed()) {
            findings.add(new Finding("ERROR", "eval_regression",
                    "The eval gate regressed against the last green baseline.",
                    "A case that used to pass now fails — review the changes before publishing."));
        }
        for (TurAgentEvalCaseResultDto r : results) {
            if (!r.passed()) {
                findings.add(new Finding(severity, "eval_case_failed",
                        "Eval case failed: " + r.caseName()
                                + (r.error() != null ? " (" + r.error() + ")" : ""),
                        failureHint(r)));
            }
        }
        return new TurAgentEvalGateDto(status, blocking, reportDto, findings);
    }

    /** The agent's most recent eval report, parsed for the API, if any. */
    public Optional<TurAgentEvalReportDto> latestReport(String agentId) {
        return reportRepository.findFirstByTurAIAgent_IdOrderByCreatedAtDesc(agentId)
                .map(TurAgentEvalGateService::toDto);
    }

    /**
     * True when a blocking gate should hard-stop a publish: at least one
     * enabled set is marked blocking AND the latest run is red/regressed.
     */
    public boolean shouldBlockPublish(String agentId) {
        TurAgentEvalGateDto gate = gate(agentId);
        return gate.blocking()
                && ("REGRESSED".equals(gate.status()) || "RED".equals(gate.status()));
    }

    private static String failureHint(TurAgentEvalCaseResultDto r) {
        if (r.slotDiffs() != null) {
            for (TurAgentEvalCaseResultDto.SlotDiff d : r.slotDiffs()) {
                if (!d.match()) {
                    return "Slot '" + d.slot() + "' expected '" + d.expected()
                            + "' but got '" + (d.actual() == null ? "(none)" : d.actual()) + "'.";
                }
            }
        }
        if ("fail".equals(r.rubricVerdict()) && r.rubricRationale() != null) {
            return "Rubric failed: " + r.rubricRationale();
        }
        if (r.expectedOutcome() != null && !r.expectedOutcome().equals(r.actualOutcome())) {
            return "Expected outcome " + r.expectedOutcome() + " but got " + r.actualOutcome() + ".";
        }
        return "Open the eval report for the full diff.";
    }

    private static TurAgentEvalReportDto toDto(TurAgentEvalReport report) {
        return new TurAgentEvalReportDto(report.getId(), report.getCreatedAt(), report.isPassed(),
                report.getScore(), report.getCaseCount(), report.getPassedCount(),
                report.isBaseline(), report.isRegressed(),
                TurAgentEvalRunnerService.parseResults(report.getResultsJson()), null);
    }
}

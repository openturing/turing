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

import java.security.Principal;
import java.util.List;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.eval.TurEvalReviewTaskService;
import com.viglet.turing.persistence.dto.agent.TurEvalAgreementDto;
import com.viglet.turing.persistence.dto.agent.TurEvalCalibrationDto;
import com.viglet.turing.persistence.dto.agent.TurEvalDatasetDto;
import com.viglet.turing.persistence.dto.agent.TurEvalReviewRequest;
import com.viglet.turing.persistence.dto.agent.TurEvalReviewTaskDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T593 / §XXXIII.8 — the human-review inbox: list the open review tasks an
 * agent's HUMAN grader (T592) parked, open one to read its transcript, and
 * submit a verdict that merges back into the latest report. T594 adds the
 * calibration surface: N-reviewer agreement (Fleiss' kappa), sampling a
 * MODEL-graded run into audit tasks, and the MODEL-vs-human calibration report.
 * Role-gated like the rest of the Agent-CI surface.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/ai-agent/{agentId}/eval/review")
@Tag(name = "Agent Eval Review", description = "AI Agent CI — human-in-the-loop review inbox")
public class TurAgentEvalReviewAPI {

    private final TurEvalReviewTaskService reviewTaskService;

    public TurAgentEvalReviewAPI(TurEvalReviewTaskService reviewTaskService) {
        this.reviewTaskService = reviewTaskService;
    }

    @Operation(summary = "Open (pending) human-review tasks for the agent")
    @GetMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<TurEvalReviewTaskDto> inbox(@PathVariable String agentId) {
        return reviewTaskService.openTaskViews(agentId);
    }

    @Operation(summary = "One review task (transcript + expected summary + verdict)")
    @GetMapping("/{taskId}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurEvalReviewTaskDto task(@PathVariable String agentId, @PathVariable String taskId) {
        return reviewTaskService.getTask(taskId);
    }

    @Operation(summary = "Submit a reviewer verdict; merges back into the latest report")
    @PostMapping("/{taskId}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurEvalReviewTaskDto submit(@PathVariable String agentId, @PathVariable String taskId,
            @RequestBody TurEvalReviewRequest request, Principal principal) {
        String reviewer = principal == null ? null : principal.getName();
        return reviewTaskService.submitReview(taskId, request, reviewer);
    }

    @Operation(summary = "Promote a reviewed task into a golden dataset row (closes the loop)")
    @PostMapping("/{taskId}/promote")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurEvalDatasetDto promote(@PathVariable String agentId, @PathVariable String taskId,
            @RequestParam("datasetId") String datasetId) {
        return reviewTaskService.promoteToDataset(taskId, datasetId);
    }

    // ─────────────────────────── T594 calibration & agreement ───────────────────────────

    @Operation(summary = "Set how many reviewers a still-open task needs before consensus (T594)")
    @PostMapping("/{taskId}/reviewers")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurEvalReviewTaskDto setReviewers(@PathVariable String agentId,
            @PathVariable String taskId, @RequestParam("count") int count) {
        return reviewTaskService.setRequiredReviewers(taskId, count);
    }

    @Operation(summary = "Sample MODEL-graded cases from the latest run into audit tasks (T594)")
    @PostMapping("/audit")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public List<TurEvalReviewTaskDto> audit(@PathVariable String agentId,
            @RequestParam(name = "sampleSize", defaultValue = "10") int sampleSize,
            @RequestParam(name = "requiredReviewers", defaultValue = "1") int requiredReviewers) {
        return reviewTaskService.sampleModelGradedAudit(agentId, sampleSize, requiredReviewers);
    }

    @Operation(summary = "Inter-annotator agreement (Fleiss' kappa) over multi-reviewer tasks (T594)")
    @GetMapping("/agreement")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurEvalAgreementDto agreement(@PathVariable String agentId) {
        return reviewTaskService.agreement(agentId);
    }

    @Operation(summary = "MODEL-vs-human calibration (Cohen's kappa) over reviewed audit tasks (T594)")
    @GetMapping("/calibration")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurEvalCalibrationDto calibration(@PathVariable String agentId) {
        return reviewTaskService.calibration(agentId);
    }
}

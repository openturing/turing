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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.distillation.TurOpenAiDistillationService;
import com.viglet.turing.genai.distillation.TurOpenAiDistillationService.DistillResult;
import com.viglet.turing.genai.eval.TurAgentEvalGateService;
import com.viglet.turing.genai.eval.TurAgentEvalRunnerService;
import com.viglet.turing.persistence.dto.agent.TurAgentEvalGateDto;
import com.viglet.turing.persistence.dto.agent.TurAgentEvalReportDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T286–T288 / §XV — Agent-CI eval endpoints: run the golden-set gate, read
 * the latest report, and query the pre-publish gate status the flow editor
 * surfaces in its Lint panel.
 *
 * <p>{@code POST /run} mirrors the nightly/PR pattern of {@code -Pllm-it}
 * (T242): the {@code -Pagent-eval} Maven profile drives the same endpoint
 * through {@code TurAgentEvalRunnerIT}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/ai-agent/{agentId}/eval")
@Tag(name = "Agent Eval", description = "AI Agent CI — golden-set eval gate")
public class TurAgentEvalAPI {

    private final TurAgentEvalRunnerService runnerService;
    private final TurAgentEvalGateService gateService;
    private final TurOpenAiDistillationService distillationService;

    public TurAgentEvalAPI(TurAgentEvalRunnerService runnerService,
            TurAgentEvalGateService gateService,
            TurOpenAiDistillationService distillationService) {
        this.runnerService = runnerService;
        this.gateService = gateService;
        this.distillationService = distillationService;
    }

    @Operation(summary = "Run the agent's eval gate (replays every enabled golden set)")
    @PostMapping("/run")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurAgentEvalReportDto run(@PathVariable String agentId) {
        return runnerService.runAgent(agentId);
    }

    @Operation(summary = "Submit the agent's rubric judgements as a Batch (50% off, ~24h)")
    @PostMapping("/run-batch")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public java.util.Map<String, Object> runBatch(@PathVariable String agentId) {
        int scheduled = runnerService.submitJudgeBatch(agentId);
        if (scheduled < 0) {
            return java.util.Map.of("scheduled", 0,
                    "reason", "Batch eval tier disabled or no usable batch-capable LLM");
        }
        return java.util.Map.of("scheduled", scheduled);
    }

    @Operation(summary = "Push the agent's rubric fixtures to the OpenAI Evals API (server-side graders)")
    @PostMapping("/run-openai-evals")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public java.util.Map<String, Object> runOpenAiEvals(@PathVariable String agentId) {
        return runnerService.submitToOpenAiEvals(agentId)
                .<java.util.Map<String, Object>>map(s -> java.util.Map.of(
                        "submitted", true,
                        "evalId", s.evalId(),
                        "runId", s.runId(),
                        "reportUrl", s.reportUrl() == null ? "" : s.reportUrl(),
                        "fixtureCount", s.fixtureCount()))
                .orElseGet(() -> java.util.Map.of("submitted", false,
                        "reason", "OpenAI Evals tier disabled, eval LLM not OpenAI-backed, "
                                + "or no rubric cases"));
    }

    @Operation(summary = "Distill the agent: export Stored Completions, fine-tune, swap model if eval passes")
    @PostMapping("/distill")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public java.util.Map<String, Object> distill(@PathVariable String agentId) {
        DistillResult result = distillationService.distill(agentId);
        if (!result.started()) {
            return java.util.Map.of("started", false, "reason", result.reason());
        }
        return java.util.Map.of(
                "started", true,
                "jobId", result.job().getId(),
                "fineTuneJobId", result.job().getFineTuneJobId(),
                "exampleCount", result.job().getExampleCount(),
                "baseModel", result.job().getBaseModel());
    }

    @Operation(summary = "Latest eval report for the agent")
    @GetMapping("/report")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurAgentEvalReportDto latestReport(@PathVariable String agentId) {
        return gateService.latestReport(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No eval report yet for agent: " + agentId));
    }

    @Operation(summary = "Agent eval run history (newest first, with the per-case breakdown)")
    @GetMapping("/history")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public java.util.List<TurAgentEvalReportDto> history(@PathVariable String agentId) {
        return gateService.history(agentId);
    }

    @Operation(summary = "Pre-publish gate status (rendered in the flow editor Lint panel)")
    @GetMapping("/gate")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurAgentEvalGateDto gate(@PathVariable String agentId) {
        return gateService.gate(agentId);
    }

    @Operation(summary = "Whether the gate can run (has an enabled set + a usable LLM)")
    @GetMapping("/available")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public boolean available(@PathVariable String agentId) {
        return runnerService.isAvailable(agentId);
    }
}

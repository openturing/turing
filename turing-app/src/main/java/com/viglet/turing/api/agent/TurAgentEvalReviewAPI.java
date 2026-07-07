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
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.eval.TurEvalReviewTaskService;
import com.viglet.turing.persistence.dto.agent.TurEvalReviewRequest;
import com.viglet.turing.persistence.dto.agent.TurEvalReviewTaskDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T593 / §XXXIII.8 — the human-review inbox: list the open review tasks an
 * agent's HUMAN grader (T592) parked, open one to read its transcript, and
 * submit a verdict that merges back into the latest report. Role-gated like the
 * rest of the Agent-CI surface.
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
}

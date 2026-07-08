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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.eval.online.TurOnlineEvalService;
import com.viglet.turing.persistence.dto.agent.TurOnlineEvalSnapshotDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T603 / §XXXIII.18 — the continuous / online-eval surface: read an agent's
 * rolling quality snapshots (drift timeline), trigger a sample-and-grade run on
 * demand, and re-baseline after a fix. Role-gated like the rest of the Agent-CI
 * eval surface.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/ai-agent/{agentId}/eval/online")
@Tag(name = "Agent Eval Online", description = "AI Agent CI — continuous / online eval")
public class TurAgentOnlineEvalAPI {

    private final TurOnlineEvalService onlineEvalService;

    public TurAgentOnlineEvalAPI(TurOnlineEvalService onlineEvalService) {
        this.onlineEvalService = onlineEvalService;
    }

    @Operation(summary = "Recent online-eval snapshots for the agent (newest first)")
    @GetMapping("/history")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<TurOnlineEvalSnapshotDto> history(@PathVariable String agentId,
            @RequestParam(defaultValue = "50") int limit) {
        return onlineEvalService.history(agentId, limit);
    }

    @Operation(summary = "The single most recent online-eval snapshot")
    @GetMapping("/latest")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public ResponseEntity<TurOnlineEvalSnapshotDto> latest(@PathVariable String agentId) {
        return onlineEvalService.latest(agentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Sample live traffic and grade it now (records a snapshot)")
    @PostMapping("/run")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurOnlineEvalSnapshotDto run(@PathVariable String agentId) {
        return onlineEvalService.sampleAndGrade(agentId);
    }

    @Operation(summary = "Promote a snapshot to the agent's healthy drift baseline")
    @PostMapping("/baseline/{snapshotId}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public ResponseEntity<TurOnlineEvalSnapshotDto> baseline(@PathVariable String agentId,
            @PathVariable String snapshotId) {
        return onlineEvalService.promoteBaseline(agentId, snapshotId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}

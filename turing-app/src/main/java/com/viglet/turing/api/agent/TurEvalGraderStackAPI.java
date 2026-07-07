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

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.eval.TurEvalGraderStackService;
import com.viglet.turing.persistence.dto.agent.TurEvalGraderStackDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T600 / §XXXIII.15 — named, reusable grader stacks shareable across agents.
 * An eval set binds one via {@code TurAgentEvalSet.graderStackId}. Role-gated
 * like the Agent-CI surface.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/eval/grader-stack")
@Tag(name = "Eval Grader Stack", description = "Reusable, agent-decoupled grader stacks")
public class TurEvalGraderStackAPI {

    private final TurEvalGraderStackService stackService;

    public TurEvalGraderStackAPI(TurEvalGraderStackService stackService) {
        this.stackService = stackService;
    }

    @Operation(summary = "List grader stacks")
    @GetMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<TurEvalGraderStackDto> list() {
        return stackService.list();
    }

    @Operation(summary = "One grader stack with its entries")
    @GetMapping("/{stackId}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurEvalGraderStackDto get(@PathVariable String stackId) {
        return stackService.get(stackId);
    }

    @Operation(summary = "Create a reusable grader stack with its ordered entries")
    @PostMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurEvalGraderStackDto create(@RequestBody TurEvalGraderStackDto request) {
        return stackService.create(request);
    }

    @Operation(summary = "Delete a grader stack")
    @DeleteMapping("/{stackId}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public void delete(@PathVariable String stackId) {
        stackService.delete(stackId);
    }
}

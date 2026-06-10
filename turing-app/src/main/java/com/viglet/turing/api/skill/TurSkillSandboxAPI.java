/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.skill;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxResult;
import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxService;
import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxSession;
import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxSessionManager;
import com.viglet.turing.system.TurGlobalSettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T321 / §IX.4.d — admin surface to exercise the skill sandbox <em>engine</em>:
 * check availability and run an ad-hoc {@code bash} command in a skill's
 * persistent session. The activation harness (T322) and chat/SN wiring
 * (T323/T324) are the real consumers; this endpoint exists so the engine is
 * verifiable on its own (analogous to the Code Interpreter "Check Docker"
 * probe) and so the folder editor can offer a "run in sandbox" pane.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/skill/{id}/sandbox")
@Tag(name = "Skill Sandbox", description = "Anthropic-compatible Docker session engine (T321)")
public class TurSkillSandboxAPI {

    private final TurSkillSandboxService sandboxService;
    private final TurSkillSandboxSessionManager sessionManager;
    private final TurGlobalSettingsService globalSettingsService;

    public TurSkillSandboxAPI(TurSkillSandboxService sandboxService,
            TurSkillSandboxSessionManager sessionManager,
            TurGlobalSettingsService globalSettingsService) {
        this.sandboxService = sandboxService;
        this.sessionManager = sessionManager;
        this.globalSettingsService = globalSettingsService;
    }

    /** Whether the sandbox can run for this deployment, plus the resolved image/mode. */
    public record SandboxStatus(boolean available, boolean storageEnabled, String executionMode, String image) {
    }

    /** Ad-hoc command request. {@code conversationId}/{@code agentId} scope the persistent session. */
    public record ExecRequest(String command, String conversationId, String agentId) {
    }

    @Operation(summary = "Skill sandbox availability and resolved container image")
    @GetMapping("/status")
    public SandboxStatus status(@PathVariable String id) {
        return new SandboxStatus(
                sandboxService.isAvailable(),
                sessionManager.isStorageEnabled(),
                globalSettingsService.getCodeInterpreterExecutionMode().name(),
                globalSettingsService.getCodeInterpreterSkillImage());
    }

    @Operation(summary = "Open the skill's session and run a bash command in it")
    @PostMapping("/exec")
    public TurSkillSandboxResult exec(@PathVariable String id, @RequestBody ExecRequest request) {
        TurSkillSandboxSession session = sessionManager.openSession(
                request.agentId(), request.conversationId(), id);
        return sandboxService.runBash(session, request.command());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String onBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String onConflict(IllegalStateException e) {
        return e.getMessage();
    }
}

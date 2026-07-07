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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.TurSystemPromptPreviewService;
import com.viglet.turing.genai.TurSystemPromptValidatorService;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptIssueDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptPreviewDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * System-prompt insight endpoints for the AI Agent "System Prompt" admin page:
 * a Live Preview that breaks the assembled prompt into labeled segments, fast
 * heuristic conflict validation, and an on-demand LLM-powered deep conflict
 * audit.
 *
 * <p>Each method is {@code @Transactional(readOnly)} so the agent loaded here
 * stays managed while the services walk its lazy associations (persona, MCP
 * servers, custom tools).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/ai-agent/{id}/system-prompt")
@Tag(name = "AI Agent System Prompt", description = "System prompt preview & validation")
public class TurAgentSystemPromptAPI {

    private final TurAIAgentRepository turAIAgentRepository;
    private final TurSystemPromptPreviewService previewService;
    private final TurSystemPromptValidatorService validatorService;

    public TurAgentSystemPromptAPI(TurAIAgentRepository turAIAgentRepository,
            TurSystemPromptPreviewService previewService,
            TurSystemPromptValidatorService validatorService) {
        this.turAIAgentRepository = turAIAgentRepository;
        this.previewService = previewService;
        this.validatorService = validatorService;
    }

    @Operation(summary = "Live preview of the assembled system prompt, broken into labeled segments")
    @GetMapping("/preview")
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    @Transactional(readOnly = true)
    public TurSystemPromptPreviewDto preview(@PathVariable String id,
            @RequestParam(required = false) String flowId,
            @RequestParam(required = false) String nodeId,
            @RequestParam(required = false) String vars) {
        return previewService.buildPreview(requireAgent(id), flowId, nodeId, vars);
    }

    @Operation(summary = "T612/T618 — replay the assembled prompt from a real conversation. "
            + "Without turnIndex (T612) it rebuilds the system message from the conversation's "
            + "current persisted state (governing flow, cursor node, collected slots, resolved "
            + "persona) through the same runtime assembler. With turnIndex (T618) it loads that "
            + "captured past turn verbatim from the prompt-capture store — the exact system "
            + "message + whole-message list the model received, frozen at send time. Either way "
            + "it rides the tool-call trace along and advertises the available captured turns. "
            + "Degrades to a plain preview when the conversation has no state / capture.")
    @GetMapping("/preview/replay")
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    @Transactional(readOnly = true)
    public TurSystemPromptPreviewDto replay(@PathVariable String id,
            @RequestParam String conversationId,
            @RequestParam(required = false) Integer turnIndex) {
        return previewService.buildReplayPreview(requireAgent(id), conversationId, turnIndex);
    }

    @Operation(summary = "Fast heuristic validation of the system prompt for conflicts")
    @GetMapping("/validate")
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    @Transactional(readOnly = true)
    public List<TurSystemPromptIssueDto> validate(@PathVariable String id) {
        return validatorService.validate(requireAgent(id));
    }

    @Operation(summary = "Deep LLM-powered conflict audit of the system prompt")
    @PostMapping("/validate/deep")
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    @Transactional(readOnly = true)
    public List<TurSystemPromptIssueDto> validateDeep(@PathVariable String id) {
        return validatorService.deepCheck(requireAgent(id));
    }

    private TurAIAgent requireAgent(String id) {
        return turAIAgentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "AI Agent not found: " + id));
    }
}

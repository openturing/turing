/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.distillation.TurChatFeedbackRating;
import com.viglet.turing.genai.distillation.TurChatFeedbackService;
import com.viglet.turing.genai.distillation.TurChatFeedbackService.DpoDataset;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * F.9 / §X.10.d — T170. Operator preference feedback (thumb-up/down) on
 * assistant turns + the incrementally-built DPO dataset export.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/ai-agent/{agentId}")
@Tag(name = "Chat Feedback", description = "Operator preference feedback + DPO dataset (F.9)")
public class TurChatFeedbackAPI {

    private final TurChatFeedbackService feedbackService;

    public TurChatFeedbackAPI(TurChatFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /** Request to record one thumb-up/down on an assistant turn. */
    public record TurChatFeedbackRequest(String conversationId, String prompt, String answer,
            TurChatFeedbackRating rating) {
    }

    @Operation(summary = "Record a thumb-up/down on an assistant turn (DPO preference signal)")
    @PostMapping("/chat-feedback")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT", "AI_AGENT_VIEW" })
    public java.util.Map<String, Object> record(@PathVariable String agentId,
            @RequestBody TurChatFeedbackRequest request) {
        try {
            String id = feedbackService.record(agentId, request.conversationId(), request.prompt(),
                    request.answer(), request.rating()).getId();
            return java.util.Map.of("recorded", true, "id", id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Operation(summary = "Build the agent's incremental DPO dataset from operator feedback")
    @GetMapping("/dpo-dataset")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public DpoDataset dpoDataset(@PathVariable String agentId) {
        return feedbackService.buildDpoDataset(agentId);
    }
}

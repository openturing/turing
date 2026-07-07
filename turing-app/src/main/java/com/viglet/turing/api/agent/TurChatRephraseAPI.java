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

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.TurChatRephraseService;
import com.viglet.turing.genai.TurChatRephraseService.RephraseResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * F.10 / §X.11.b — T172. "Rephrase" an assistant answer (shorter/simpler/…),
 * accelerated by OpenAI Predicted Outputs (T171) when the agent opted in.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/ai-agent/{agentId}")
@Tag(name = "Chat Rephrase", description = "Rewrite an assistant answer, fast-pathed via Predicted Outputs (F.10)")
public class TurChatRephraseAPI {

    private final TurChatRephraseService rephraseService;

    public TurChatRephraseAPI(TurChatRephraseService rephraseService) {
        this.rephraseService = rephraseService;
    }

    /** Request to rewrite a prior assistant answer. */
    public record TurChatRephraseRequest(String conversationId, String llmInstanceId, String prompt,
            String answer, String style) {
    }

    @Operation(summary = "Rewrite an assistant answer (shorter/simpler/…); uses Predicted Outputs when enabled")
    @PostMapping("/chat-rephrase")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT", "AI_AGENT_VIEW" })
    public RephraseResult rephrase(@PathVariable String agentId,
            @RequestBody TurChatRephraseRequest request) {
        return rephraseService.rephrase(agentId, request.llmInstanceId(), request.prompt(),
                request.answer(), request.style());
    }
}

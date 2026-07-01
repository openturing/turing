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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.callcenter.TurCallCenterAssistService;
import com.viglet.turing.genai.callcenter.TurCallCenterAssistService.CallAssistResult;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * T189 / §X.15.c — real-time multilingual call-center augmentation for an AI
 * agent. The operator console first checks {@code GET …/call-center/available}
 * (to decide whether to render the assist panel), then posts each transcribed
 * customer utterance to {@code POST …/call-center/assist} to get back a cited
 * answer in the operator's language plus its back-translation for the customer.
 *
 * <p>Strictly opt-in: both endpoints require {@link TurAIAgent}'s {@code isCallCenterEnabled()}
 * (403 otherwise), so existing agents are unaffected. The actual translate →
 * cited-answer → translate-back composition lives in
 * {@link TurCallCenterAssistService}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/ai-agent/{agentId}/call-center")
@Tag(name = "AI Agent Call Center",
        description = "Real-time multilingual call-center augmentation for an AI Agent")
public class TurAIAgentCallCenterAPI {

    private final TurAIAgentRepository turAIAgentRepository;
    private final TurCallCenterAssistService assistService;

    public TurAIAgentCallCenterAPI(TurAIAgentRepository turAIAgentRepository,
            TurCallCenterAssistService assistService) {
        this.turAIAgentRepository = turAIAgentRepository;
        this.assistService = assistService;
    }

    /** One operator-assist turn: a transcribed customer utterance to answer. */
    public record CallAssistApiRequest(String site, String siteLocale, String customerUtterance,
            String customerLang, String operatorLang, Integer maxSources) {
    }

    /** Whether call-center augmentation is on for this agent (drives the UI). */
    public record CallCenterAvailability(boolean available, String reason) {
    }

    @GetMapping("/available")
    public CallCenterAvailability available(@PathVariable String agentId) {
        TurAIAgent agent = turAIAgentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return new CallCenterAvailability(false, "Agent not found");
        }
        if (agent.getEnabled() != 1) {
            return new CallCenterAvailability(false, "Agent is disabled");
        }
        if (!agent.isCallCenterEnabled()) {
            return new CallCenterAvailability(false, "Call-center augmentation is not enabled for this agent");
        }
        return new CallCenterAvailability(true, null);
    }

    @PostMapping("/assist")
    public CallAssistResult assist(@PathVariable String agentId,
            @RequestBody CallAssistApiRequest request) {
        TurAIAgent agent = turAIAgentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
        if (agent.getEnabled() != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent is disabled");
        }
        if (!agent.isCallCenterEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Call-center augmentation is not enabled for this agent");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        return assistService.assist(request.site(), request.siteLocale(), request.customerUtterance(),
                request.customerLang(), request.operatorLang(), request.maxSources());
    }
}

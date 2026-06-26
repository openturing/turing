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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.nativeapi.voice.TurRealtimeVoiceSession;
import com.viglet.turing.genai.nativeapi.voice.TurRealtimeVoiceSessionService;
import com.viglet.turing.genai.nativeapi.voice.TurRealtimeVoiceSessionService.VoiceAvailability;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * T148 / §X.6.b — real-time voice session endpoint for an AI agent. The browser
 * first checks {@code GET …/voice/available} (to decide whether to render the
 * voice button), then {@code POST …/voice/session} to mint a short-lived
 * ephemeral session it uses to open the WebSocket transport to the vendor's
 * realtime endpoint directly. The Turing server never proxies the audio — it
 * only mints the credential.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/ai-agent/{agentId}/voice")
@Tag(name = "AI Agent Voice", description = "Real-time voice sessions for an AI Agent (Realtime API)")
public class TurAIAgentVoiceAPI {

    private final TurRealtimeVoiceSessionService voiceSessionService;

    public TurAIAgentVoiceAPI(TurRealtimeVoiceSessionService voiceSessionService) {
        this.voiceSessionService = voiceSessionService;
    }

    /** Optional hints from the browser; all fields may be omitted (the server fills defaults). */
    public record VoiceSessionRequest(String llmInstanceId, String model, String voice, String locale) {
    }

    /** Whether voice is available for this agent (drives the UI), plus the resolved model/voice. */
    @GetMapping("/available")
    public VoiceAvailability available(@PathVariable String agentId,
            @RequestParam(required = false) String llmInstanceId) {
        return voiceSessionService.availability(agentId, llmInstanceId);
    }

    /** Mint an ephemeral real-time voice session for the agent. */
    @PostMapping("/session")
    public TurRealtimeVoiceSession session(@PathVariable String agentId,
            @RequestBody(required = false) VoiceSessionRequest request) {
        VoiceSessionRequest req = request == null
                ? new VoiceSessionRequest(null, null, null, null) : request;
        return voiceSessionService.createForAgent(agentId, req.llmInstanceId(), req.model(),
                req.voice(), req.locale());
    }
}

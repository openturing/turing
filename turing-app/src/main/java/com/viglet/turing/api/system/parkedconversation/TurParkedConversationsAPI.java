/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.system.parkedconversation;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.flow.TurParkedConversationService;
import com.viglet.turing.persistence.dto.agent.TurParkedConversationDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T122 / §IX.6.b — admin dashboard API for conversations parked on a
 * {@code suspend} node (T121). Lists every suspended conversation with its
 * reason and waiting time, and exposes an "unblock" action that resumes the
 * flow server-side (the operator-driven counterpart to the visitor-facing
 * {@code POST /api/sn/{site}/chat/resume}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/system/parked-conversations")
@Tag(name = "Parked Conversations",
        description = "Admin view of conversations suspended on a chat-flow suspend node, with an unblock action.")
public class TurParkedConversationsAPI {

    private final TurParkedConversationService parkedConversationService;

    public TurParkedConversationsAPI(TurParkedConversationService parkedConversationService) {
        this.parkedConversationService = parkedConversationService;
    }

    /**
     * Every currently-parked conversation, longest-waiting first.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    @Operation(summary = "List all conversations parked on a suspend node.")
    public List<TurParkedConversationDto> list() {
        return parkedConversationService.listParked();
    }

    /**
     * Unblock one conversation — walk its parked state(s) past the suspend
     * node without any slot payload. Idempotent.
     */
    @PostMapping(value = "/{conversationId}/resume", produces = MediaType.APPLICATION_JSON_VALUE)
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    @Operation(summary = "Unblock a parked conversation (admin resume).")
    public UnblockResponse resume(@PathVariable String conversationId) {
        return parkedConversationService.unblockIfPresent(conversationId)
                .map(r -> new UnblockResponse(r.resumed(), !r.nothingToResume(), null))
                .orElseGet(() -> new UnblockResponse(0, false, "conversationId is required"));
    }

    /**
     * @param resumed   number of state rows advanced past their suspend node
     * @param wasParked whether the conversation actually had a parked state
     * @param error     non-null when the request was rejected (blank id)
     */
    public record UnblockResponse(int resumed, boolean wasParked, String error) {
    }
}

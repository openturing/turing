/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.chat;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.TurChatStreamingDispatcher;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/**
 * T438 / §XXII.3 — resume endpoint for the client-tool protocol.
 *
 * <p>When an agent's turn parks on a frontend ("client") tool, the chat SSE
 * carries a {@code client_tool_call} event {@code {callId, name, args}} and the
 * turn suspends (see {@link TurChatStreamingDispatcher}). The browser runs the
 * tool and POSTs the result here; the parked turn resumes and the continuation
 * streams back as the same SSE {@code ChatResponse} shape the chat endpoint
 * uses (so the SDK consumes it identically).
 *
 * <p>An unknown / already-resumed / expired {@code (conversationId, callId)}
 * yields 404. The park store is in-memory (single-node MVP).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/chat")
@Tag(name = "Client Tools", description = "Resume a chat turn parked on a frontend (client) tool")
public class TurClientToolResultAPI {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurChatStreamingDispatcher streamingDispatcher;

    public TurClientToolResultAPI(TurChatStreamingDispatcher streamingDispatcher) {
        this.streamingDispatcher = streamingDispatcher;
    }

    /**
     * Request body. {@code result} is the tool's return value (any JSON — object,
     * array, scalar — or a string); {@code error} is a client-side failure
     * message, fed back to the model as the tool result instead of {@code result}.
     */
    public record ClientToolResultRequest(String conversationId, String callId,
            Object result, String error) {
    }

    @PostMapping(value = "/client-tool-result", produces = MediaType.TEXT_EVENT_STREAM_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ChatResponse> clientToolResult(@RequestBody ClientToolResultRequest request) {
        if (request == null || request.conversationId() == null || request.callId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "conversationId and callId are required");
        }
        String resultJson = serializeResult(request.result());
        try {
            return streamingDispatcher.resumeClientTool(
                    request.conversationId(), request.callId(), resultJson, request.error());
        } catch (IllegalArgumentException e) {
            // unknown / already-resumed / expired park
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /** Serializes the result payload to a JSON string the model receives as the tool output. */
    private static String serializeResult(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof String s) {
            return s;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (RuntimeException e) {
            log.warn("[ClientTool] could not serialize client-tool result: {}", e.getMessage());
            return String.valueOf(result);
        }
    }
}

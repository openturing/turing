/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.time.Duration;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.codec.ServerSentEvent;

import com.viglet.turing.genai.gateway.TurOpenAiWire.ErrorEnvelope;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/**
 * T1100 — how a streamed gateway call fails the way an OpenAI streaming client
 * expects: with the status the blocking path would answer when the provider
 * refuses before the first token, and with OpenAI's streamed error shape when
 * it fails after tokens were already sent.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
final class TurGatewayStreams {

    /** How long a streamed call waits for the provider's first signal. */
    static final Duration FIRST_SIGNAL_TIMEOUT = Duration.ofMinutes(2);

    private TurGatewayStreams() {
    }

    /**
     * The provider stream, already connected and past its first signal. A quota
     * or rate limit always lands before the first token, and the SSE status is
     * committed once the stream is handed to Spring, so the first signal is
     * awaited here: an error then throws to the controller, which answers it as
     * the blocking path does (429 for a provider limit). The stream is replayed,
     * so the first token still reaches the client.
     */
    static Flux<ChatResponse> started(ChatModel chatModel, Prompt prompt) {
        ConnectableFlux<ChatResponse> replay = chatModel.stream(prompt).replay();
        replay.connect();
        replay.next().block(FIRST_SIGNAL_TIMEOUT);
        return replay;
    }

    /**
     * A failure after tokens were sent, in OpenAI's streamed error shape
     * ({@code data: {"error": {...}}}) so a client raises it instead of showing
     * it as assistant text.
     */
    static ServerSentEvent<String> errorFrame(Throwable err, ObjectMapper objectMapper) {
        ErrorEnvelope envelope = TurGatewayProviderLimit.of(err)
                .map(limit -> ErrorEnvelope.of(limit.message(), limit.type(), limit.code()))
                .orElseGet(() -> ErrorEnvelope.of(err.getMessage(), "api_error", null));
        try {
            return ServerSentEvent.<String>builder(objectMapper.writeValueAsString(envelope)).build();
        } catch (RuntimeException e) {
            log.warn("[Gateway] Failed to serialise stream error: {}", e.getMessage());
            return ServerSentEvent.<String>builder("{\"error\":{\"type\":\"api_error\"}}").build();
        }
    }
}

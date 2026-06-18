/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.spectator;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-process pub/sub for chat messages, keyed by {@code conversationId} (T120).
 * The exact analogue of {@link com.viglet.turing.service.chatslots.TurChatSlotEventBus}
 * and {@link com.viglet.turing.genai.workspace.TurWorkspaceEventBus} for the
 * conversation transcript: subscribers (the spectator stream
 * {@code GET /api/chat/sessions/{id}/spectate/stream}) receive a server-sent
 * event every time a turn completes — one {@link TurChatMessageEvent} for the
 * visitor message and one for the assistant reply, whether that reply came from
 * the LLM or from an operator who took the wheel.
 *
 * <p>Unlike the chat SSE that streams the visitor's own request back to that
 * one client, this bus lets a <em>second</em> observer (a spectating operator)
 * watch a conversation they did not initiate. The visitor's chat keeps working
 * unchanged; the bus is a passive tee.
 *
 * <p>Single-node only — the sink lives in-process, same caveat as the slot and
 * workspace buses. Backpressure uses {@code tryEmitNext} with the
 * {@code FAIL_FAST} contract so a slow spectator never blocks a chat turn;
 * dropped events are logged at debug, and a fresh spectator recovers the
 * authoritative transcript from the chat-memory snapshot
 * ({@code GET /api/chat/sessions/{id}/messages}) on subscribe.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurChatMessageEventBus {

    /**
     * Multicast sink — one subscription per consumer, broadcasts every
     * {@code publish} to all live ones. The {@code conversationId} filter is
     * applied downstream in {@link #subscribe(String)} rather than maintaining
     * one sink per conversation, so a new conversation costs nothing.
     */
    private final Sinks.Many<TurChatMessageEvent> sink =
            Sinks.many().multicast().directBestEffort();

    /**
     * Publishes one completed message. Called by the post-turn telemetry seam
     * for normal turns and by {@link TurCopilotService} for operator turns. A
     * null event or one with a blank conversation id / content is ignored.
     */
    public void publish(TurChatMessageEvent event) {
        if (event == null || event.conversationId() == null || event.conversationId().isBlank()) {
            return;
        }
        if (event.content() == null || event.content().isBlank()) {
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            // FAIL_FAST result — usually FAIL_NON_SERIALIZED under contention.
            // A spectator recovers the missed turn from the chat-memory
            // snapshot on resubscribe, so we only log so the operator can spot
            // persistent issues.
            log.debug("[ChatMessageBus] dropped event for conv={} role={}: {}",
                    event.conversationId(), event.role(), result);
        }
    }

    /**
     * A filtered view of the bus restricted to one conversation. The returned
     * flux is hot — subscribers receive only events emitted after subscription.
     * The SSE endpoint prepends a transcript snapshot read from chat memory so
     * the spectator always starts with the conversation so far.
     */
    public Flux<TurChatMessageEvent> subscribe(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Flux.empty();
        }
        return sink.asFlux().filter(event -> conversationId.equals(event.conversationId()));
    }

    /**
     * Unfiltered hot view of every message event on the bus, regardless of
     * conversation. Provided for parity with the slot and workspace buses and
     * for single-process listeners that need to react to chat globally.
     */
    public Flux<TurChatMessageEvent> subscribeAll() {
        return sink.asFlux();
    }
}

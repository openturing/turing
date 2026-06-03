/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.workspace;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-process pub/sub for workspace mutations, keyed by {@code conversationId}
 * (T113). The exact analogue of {@link com.viglet.turing.service.chatslots.TurChatSlotEventBus}
 * for the per-conversation blob store: subscribers
 * ({@code GET /chat/workspace/stream}) receive a server-sent event every time a
 * blob is written or deleted through {@link TurAgentWorkspaceService}, which is
 * the single mutation seam every path funnels through:
 *
 * <ul>
 *   <li>{@code workspace.put(...)} / {@code workspace.delete(...)} inside a
 *   Custom Tool Groovy script (T112).</li>
 *   <li>The auto-offload of large tool results (T114).</li>
 *   <li>Any future server-side writer (image generation, code-interpreter
 *   artifacts).</li>
 * </ul>
 *
 * <p><b>Metadata only.</b> Unlike the slot bus — which re-broadcasts the whole
 * value map — a workspace event carries {@link TurWorkspaceEvent only
 * metadata} ({@code key, contentType, size, signedUrl}). A 50&nbsp;MB blob
 * therefore costs a few hundred bytes on the wire per change, not 50&nbsp;MB to
 * every connected client; consumers fetch the bytes through the signed URL on
 * demand.
 *
 * <p>Single-node only — the sink lives in-process, same caveat as the slot bus.
 * Backpressure uses {@code tryEmitNext} with the {@code FAIL_FAST} contract so a
 * slow SSE subscriber never blocks a workspace write; dropped events are logged
 * at debug, and a client recovers the authoritative state from the initial
 * snapshot on resubscribe.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurWorkspaceEventBus {

    /**
     * Multicast sink — one subscription per consumer, broadcasts every
     * {@code publish} to all live ones. The {@code conversationId} filter is
     * applied downstream in {@link #subscribe(String)} rather than maintaining
     * one sink per conversation, so a new conversation costs nothing.
     */
    private final Sinks.Many<TurWorkspaceEvent> sink =
            Sinks.many().multicast().directBestEffort();

    /**
     * Publishes a workspace mutation. Called by {@link TurAgentWorkspaceService}
     * after a successful {@code put}/{@code delete}. A null event or one with a
     * blank conversation id is ignored.
     */
    public void publish(TurWorkspaceEvent event) {
        if (event == null || event.conversationId() == null || event.conversationId().isBlank()) {
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            // FAIL_FAST result — usually FAIL_NON_SERIALIZED under contention.
            // The next write republishes the artifact's state, and a fresh
            // subscriber reads the snapshot, so a dropped event self-heals; we
            // only log so the operator can spot persistent issues.
            log.debug("[WorkspaceEventBus] dropped event for conv={} key={}: {}",
                    event.conversationId(), event.key(), result);
        }
    }

    /**
     * A filtered view of the bus restricted to one conversation. The returned
     * flux is hot — subscribers receive only events emitted after subscription.
     * The SSE endpoint prepends an initial snapshot (one {@code put} per
     * existing artifact) so consumers always start with a known artifact list.
     */
    public Flux<TurWorkspaceEvent> subscribe(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Flux.empty();
        }
        return sink.asFlux().filter(event -> conversationId.equals(event.conversationId()));
    }

    /**
     * Unfiltered hot view of every workspace event on the bus, regardless of
     * conversation. Provided for parity with {@code TurChatSlotEventBus} and for
     * single-process listeners that need to react to artifact writes globally.
     */
    public Flux<TurWorkspaceEvent> subscribeAll() {
        return sink.asFlux();
    }
}

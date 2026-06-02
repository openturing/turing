/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatslots;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDeltaDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-process pub/sub for slot updates, keyed by {@code conversationId}.
 * Subscribers ({@code GET /chat/slots/stream}) receive a server-sent event
 * every time a slot is written through any of the three production paths:
 *
 * <ul>
 *   <li>{@code slot} chat-flow node (graph executor saves a state).</li>
 *   <li>{@code slots.set(...)} inside a Custom Tool Groovy script.</li>
 *   <li>{@code POST /api/sn/{site}/chat/slots} — the React {@code
 *   useTuringSlotWriter} hook.</li>
 * </ul>
 *
 * <p>Single-node only — the sink lives in-process. Cluster deployments that
 * route a slot write to node A but a slot stream to node B will miss the
 * update on B. Future work can swap the sink for a Redis pub/sub channel
 * keyed by {@code conversationId} without changing the public API of this
 * service.
 *
 * <p>Backpressure: each emit uses {@code tryEmitNext} with the
 * {@code FAIL_FAST} contract — we never block a write path on a slow
 * subscriber. Dropped events are logged at debug; clients fetch the
 * authoritative snapshot via {@code GET /chat/slots} on resubscribe.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@Component
public class TurChatSlotEventBus {

    /**
     * Multicast sink — one subscription per consumer, broadcasts every
     * {@code emit} to all live ones. We attach an in-line filter on
     * {@code conversationId} downstream rather than maintaining one sink
     * per conversation, so creating a new conversation costs nothing.
     */
    private final Sinks.Many<TurChatSessionSlotsDto> sink =
            Sinks.many().multicast().directBestEffort();

    /**
     * Publishes the post-write slot snapshot. Called by every code path
     * that mutates a {@code TurChatFlowState}'s variable map. The DTO
     * is the full merged slot map for the conversation — subscribers
     * never have to diff.
     */
    public void publish(String conversationId, Map<String, String> slots) {
        if (conversationId == null || conversationId.isBlank() || slots == null) return;
        TurChatSessionSlotsDto event = new TurChatSessionSlotsDto(conversationId, slots);
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            // FAIL_FAST result — usually FAIL_NON_SERIALIZED under heavy
            // contention. Slot writes are intrinsically idempotent so the
            // next mutation will republish; we only log so the operator
            // can spot persistent issues.
            log.debug("[SlotEventBus] dropped event for conv={}: {}", conversationId, result);
        }
    }

    /**
     * A filtered view of the bus restricted to one conversation. The
     * returned flux is hot — subscribers receive only events emitted
     * after subscription. The SSE endpoint prepends an initial snapshot
     * read from the persistence layer so consumers always start with a
     * known state.
     */
    public Flux<TurChatSessionSlotsDto> subscribe(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Flux.empty();
        }
        return sink.asFlux().filter(event -> conversationId.equals(event.conversationId()));
    }

    /**
     * Unfiltered hot view of every slot event on the bus, regardless of
     * conversation. Used by single-process listeners that need to react
     * to slot writes globally — e.g. {@code TurChatFlowAutoResumeService}
     * nudging the engine to advance any flow parked on a
     * {@code scheduleAgent} node when its routine writes the output slot.
     * Per-conversation SSE clients keep using {@link #subscribe(String)}.
     *
     * @since 2026.3.1
     */
    public Flux<TurChatSessionSlotsDto> subscribeAll() {
        return sink.asFlux();
    }

    /**
     * T63 — a delta view for one conversation. Emits an initial
     * {@link TurChatSessionSlotsDeltaDto#snapshot snapshot} event carrying
     * {@code initialSnapshot} verbatim, then one incremental delta per slot
     * write (computed against the previous full snapshot). Empty deltas are
     * filtered out so an idle write never reaches the wire.
     *
     * <p>The {@code prev} reference is per-subscription (each SSE client gets
     * its own), so two clients connecting at different times each get a
     * correct snapshot + deltas relative to their own connect point. Same
     * single-node caveat as {@link #subscribe(String)}.
     *
     * @param conversationId conversation to scope the stream to
     * @param initialSnapshot the current full slot map (read from persistence
     *                        by the caller) — becomes the first event
     * @since 2026.3.1
     */
    public Flux<TurChatSessionSlotsDeltaDto> subscribeDelta(String conversationId,
            Map<String, String> initialSnapshot) {
        if (conversationId == null || conversationId.isBlank()) {
            return Flux.empty();
        }
        Map<String, String> safeInitial = initialSnapshot == null
                ? Map.of() : new LinkedHashMap<>(initialSnapshot);
        AtomicReference<Map<String, String>> previous = new AtomicReference<>(safeInitial);
        TurChatSessionSlotsDeltaDto initialEvent =
                TurChatSessionSlotsDeltaDto.snapshot(conversationId, safeInitial);
        Flux<TurChatSessionSlotsDeltaDto> deltas = subscribe(conversationId)
                .map(event -> {
                    Map<String, String> after = event.slots() == null
                            ? Map.of() : event.slots();
                    Map<String, String> before = previous.getAndSet(new LinkedHashMap<>(after));
                    return TurChatSessionSlotsDeltaDto.diff(conversationId, before, after);
                })
                .filter(delta -> !delta.isEmpty());
        return Flux.concat(Flux.just(initialEvent), deltas);
    }
}

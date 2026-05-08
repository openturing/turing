/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.service.chatanalytics;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.turing.observability.TurMeterNames;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Facade over {@link TurChatAnalyticsStore}. Two responsibilities:
 *
 * <ol>
 *   <li>Maintain a small in-memory map of {@code conversationId → in-flight
 *       counters} (turn count, token totals, started-at) so callers don't have
 *       to round-trip the store on every turn just to increment.</li>
 *   <li>Expose a small, intention-named API ({@code recordSessionStart},
 *       {@code recordTurn}, {@code recordSessionEnd}) so the chat executor
 *       and flow engine don't have to construct event records by hand.</li>
 * </ol>
 *
 * <p>All persistence is best-effort: failures inside the underlying store are
 * already logged and swallowed, and the cache is bounded by natural session
 * turnover (entries are removed at session end).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@Service
public class TurChatAnalyticsService {

    private final TurChatAnalyticsStore store;

    /**
     * Per-conversation in-flight aggregates. Only kept while the session is
     * open; removed by {@link #recordSessionEnd}. A periodic sweep
     * ({@link #sweepStaleInFlight}) trims entries whose owning worker
     * died before recording an end event so the map can't leak indefinitely.
     */
    private final Map<String, InFlight> inFlight = new ConcurrentHashMap<>();

    private final long inFlightMaxAgeMillis;

    public TurChatAnalyticsService(TurChatAnalyticsStore store,
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Value("${turing.chat.analytics.inflight.max-age-hours:4}") int inFlightMaxAgeHours) {
        this.store = store;
        this.inFlightMaxAgeMillis = Duration.ofHours(Math.max(1, inFlightMaxAgeHours)).toMillis();
        // Idempotent — safe to call on every boot. NoOp implementations
        // override to nothing; Mongo creates secondary indexes here so the
        // first dashboard load doesn't pay the index-build cost.
        try {
            store.ensureIndexes();
        } catch (RuntimeException e) {
            log.warn("Chat analytics ensureIndexes failed: {}", e.getMessage());
        }
        // Surface the in-flight map size as a Prometheus gauge so a slow leak
        // (sessions started but never ended) is visible before it becomes a
        // memory pressure issue. Tests that don't wire MeterRegistry skip this.
        if (meterRegistry != null) {
            Gauge.builder(TurMeterNames.CHAT_ANALYTICS_INFLIGHT_SIZE, inFlight, Map::size)
                    .description("Active chat sessions tracked in memory by the analytics service.")
                    .register(meterRegistry);
        }
    }

    /** Returns true if the configured store is wired (not the no-op). */
    public boolean isEnabled() {
        return store.isEnabled();
    }

    /**
     * Records the start of a chat session and seeds the in-flight cache.
     * Idempotent — calling twice with the same {@code conversationId} only
     * upserts the start timestamp the first time (the store uses
     * {@code $setOnInsert}-style semantics).
     */
    public void recordSessionStart(String conversationId, String agentId, String personaId,
            String llmInstanceId, String embeddingModelId, String storeInstanceId,
            String userId, String locale, String firstUserMessage) {
        if (!store.isEnabled()) return;
        Instant now = Instant.now();
        inFlight.computeIfAbsent(conversationId,
                id -> new InFlight(now, agentId, personaId, llmInstanceId,
                        embeddingModelId, storeInstanceId, userId, locale));
        try {
            store.upsertSession(TurChatSessionEvent.start(conversationId, agentId, personaId,
                    llmInstanceId, embeddingModelId, storeInstanceId, userId, locale,
                    firstUserMessage, now));
        } catch (RuntimeException e) {
            log.warn("Chat analytics recordSessionStart failed for {}: {}",
                    conversationId, e.getMessage());
        }
    }

    /**
     * Increments the turn counter and accumulates token usage for the
     * conversation. Cheap — touches only the in-memory cache, no I/O.
     */
    public void recordTurn(String conversationId, long tokensIn, long tokensOut) {
        InFlight inflight = inFlight.get(conversationId);
        if (inflight != null) inflight.recordTurn(tokensIn, tokensOut);
    }

    /**
     * Persists the final session record with all aggregates and removes the
     * in-flight entry. Safe to call when no start was recorded — the store
     * upserts and the timestamps degrade gracefully (startedAt absent ↦ stays
     * absent, durationMs ↦ 0).
     */
    public void recordSessionEnd(String conversationId, TurChatSessionOutcome outcome) {
        if (!store.isEnabled()) return;
        InFlight inflight = inFlight.remove(conversationId);
        Instant now = Instant.now();
        Instant startedAt = inflight != null ? inflight.startedAt : null;
        long durationMs = startedAt != null
                ? Math.max(0, Duration.between(startedAt, now).toMillis())
                : 0L;
        try {
            store.upsertSession(new TurChatSessionEvent(
                    conversationId,
                    inflight == null ? null : inflight.agentId,
                    inflight == null ? null : inflight.personaId,
                    inflight == null ? null : inflight.llmInstanceId,
                    inflight == null ? null : inflight.embeddingModelId,
                    inflight == null ? null : inflight.storeInstanceId,
                    inflight == null ? null : inflight.userId,
                    inflight == null ? null : inflight.locale,
                    null,           // firstUserMessage is start-only and preserved by store
                    startedAt,
                    now,
                    outcome,
                    inflight == null ? 0  : inflight.turns,
                    inflight == null ? 0L : inflight.tokensIn,
                    inflight == null ? 0L : inflight.tokensOut,
                    durationMs));
        } catch (RuntimeException e) {
            log.warn("Chat analytics recordSessionEnd failed for {}: {}",
                    conversationId, e.getMessage());
        }
    }

    /** Pass-through to the store — used by the REST analytics endpoints. */
    public TurChatAnalyticsStore getStore() {
        return store;
    }

    /**
     * Hourly sweep that removes in-flight entries whose worker died before
     * calling {@link #recordSessionEnd}. Without this, the map grows
     * monotonically: every crashed/aborted worker leaves an entry behind that
     * is never picked up by the normal end-of-session path.
     *
     * <p>Threshold defaults to 4 hours
     * ({@code turing.chat.analytics.inflight.max-age-hours}) — comfortably
     * longer than any realistic conversation, well below the kind of timeline
     * that would be confused with active traffic.
     *
     * <p>Not cluster-locked: the in-flight map is per-process, so each node
     * trims its own state.
     */
    @Scheduled(fixedDelayString = "${turing.chat.analytics.inflight.sweep-ms:3600000}")
    public void sweepStaleInFlight() {
        if (inFlight.isEmpty()) return;
        long cutoffMillis = System.currentTimeMillis() - inFlightMaxAgeMillis;
        int before = inFlight.size();
        inFlight.entrySet().removeIf(entry -> {
            Instant startedAt = entry.getValue().startedAt;
            return startedAt != null && startedAt.toEpochMilli() < cutoffMillis;
        });
        int removed = before - inFlight.size();
        if (removed > 0) {
            log.info("[ChatAnalytics] swept {} stale in-flight session(s); {} remaining",
                    removed, inFlight.size());
        }
    }

    /**
     * Mutable in-flight bag held only while the session is open. Synchronized
     * on increments so concurrent turns from the same conversation (rare —
     * usually serialized by the chat executor) don't corrupt counters.
     */
    private static final class InFlight {
        final Instant startedAt;
        final String agentId;
        final String personaId;
        final String llmInstanceId;
        final String embeddingModelId;
        final String storeInstanceId;
        final String userId;
        final String locale;
        int turns;
        long tokensIn;
        long tokensOut;

        InFlight(Instant startedAt, String agentId, String personaId, String llmInstanceId,
                String embeddingModelId, String storeInstanceId, String userId, String locale) {
            this.startedAt = startedAt;
            this.agentId = agentId;
            this.personaId = personaId;
            this.llmInstanceId = llmInstanceId;
            this.embeddingModelId = embeddingModelId;
            this.storeInstanceId = storeInstanceId;
            this.userId = userId;
            this.locale = locale;
        }

        synchronized void recordTurn(long in, long out) {
            this.turns += 1;
            this.tokensIn += Math.max(0, in);
            this.tokensOut += Math.max(0, out);
        }
    }
}

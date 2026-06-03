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
import java.util.List;
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
    private final long inFlightIdleTimeoutMillis;

    public TurChatAnalyticsService(TurChatAnalyticsStore store,
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Value("${turing.chat.analytics.inflight.max-age-hours:4}") int inFlightMaxAgeHours,
            @Value("${turing.chat.analytics.inflight.idle-timeout-min:30}") int inFlightIdleTimeoutMin) {
        this.store = store;
        this.inFlightMaxAgeMillis = Duration.ofHours(Math.max(1, inFlightMaxAgeHours)).toMillis();
        this.inFlightIdleTimeoutMillis = Duration.ofMinutes(Math.max(1, inFlightIdleTimeoutMin)).toMillis();
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
        recordSessionStart(conversationId, agentId, personaId, llmInstanceId, embeddingModelId,
                storeInstanceId, userId, locale, firstUserMessage, null, null);
    }

    /**
     * T74 overload — also seeds the visitor cohort fields ({@code timezone}
     * from the {@code X-Timezone} header, {@code deviceType} classified from
     * the {@code User-Agent}). The chat executor resolves these via
     * {@link TurChatCohortResolver} on the HTTP thread and passes them in so
     * the scorecard can slice variants by sub-population. The legacy overload
     * delegates here with {@code null} cohort fields.
     *
     * @since 2026.3.1
     */
    public void recordSessionStart(String conversationId, String agentId, String personaId,
            String llmInstanceId, String embeddingModelId, String storeInstanceId,
            String userId, String locale, String firstUserMessage,
            String timezone, String deviceType) {
        if (!store.isEnabled()) return;
        Instant now = Instant.now();
        // T110 — when a Custom Tool's agent.invoke(...) spawns this session,
        // the helper carries the parent conversation id inline on the first
        // user message via the `[__parentConversationId=...]` prefix. Parse
        // + strip here so the analytics row gets the structured field and
        // the visible firstUserMessage stays clean of the audit marker.
        String parentConversationId = extractParentConversationId(firstUserMessage);
        String visibleFirstUserMessage = stripParentConversationIdPrefix(firstUserMessage);
        inFlight.computeIfAbsent(conversationId,
                id -> new InFlight(now, agentId, personaId, llmInstanceId,
                        embeddingModelId, storeInstanceId, userId, locale,
                        parentConversationId, timezone, deviceType));
        try {
            store.upsertSession(TurChatSessionEvent.start(conversationId, agentId, personaId,
                    llmInstanceId, embeddingModelId, storeInstanceId, userId, locale,
                    visibleFirstUserMessage, now, parentConversationId, timezone, deviceType));
        } catch (RuntimeException e) {
            log.warn("Chat analytics recordSessionStart failed for {}: {}",
                    conversationId, e.getMessage());
        }
    }

    /**
     * T110 — parses the {@code [__parentConversationId=...]} prefix the
     * Custom-Tool {@code agent.invoke(...)} helper writes on the child's
     * first user message. Returns {@code null} when the input is null/blank
     * or the prefix is absent.
     *
     * <p>Visible for testing — package-private + static so the parsing
     * contract can be pinned independently of Spring wiring.
     */
    static String extractParentConversationId(String firstUserMessage) {
        if (firstUserMessage == null) return null;
        String s = firstUserMessage;
        if (!s.startsWith("[__parentConversationId=")) return null;
        int end = s.indexOf(']');
        if (end < 0) return null;
        String value = s.substring("[__parentConversationId=".length(), end);
        return value.isBlank() ? null : value;
    }

    /**
     * T110 — returns {@code firstUserMessage} with the
     * {@code [__parentConversationId=...]\n} prefix stripped. Passes the
     * input through unchanged when the prefix isn't present, so non-child
     * sessions are unaffected.
     */
    static String stripParentConversationIdPrefix(String firstUserMessage) {
        if (firstUserMessage == null) return null;
        if (!firstUserMessage.startsWith("[__parentConversationId=")) return firstUserMessage;
        int end = firstUserMessage.indexOf(']');
        if (end < 0) return firstUserMessage;
        // Skip the closing bracket; also skip a single trailing newline
        // (the helper writes "[...]\n<message>") so the visible message
        // starts at column 0 instead of with a stray blank line.
        int after = end + 1;
        if (after < firstUserMessage.length() && firstUserMessage.charAt(after) == '\n') {
            after += 1;
        }
        return firstUserMessage.substring(after);
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
     * Tags an in-flight conversation with the A/B experiment variant it
     * landed on. Called once per session by the flow router right after
     * {@code assignVariant} picks an arm — sticky thereafter (subsequent
     * calls on the same conversation are no-ops to preserve the original
     * assignment for the entire session's analytics).
     *
     * @since 2026.2.7
     */
    public void recordExperimentAssignment(String conversationId, String experimentKey,
            String variantLabel) {
        if (conversationId == null || conversationId.isBlank()) return;
        if (experimentKey == null || experimentKey.isBlank()) return;
        InFlight inflight = inFlight.get(conversationId);
        if (inflight == null) return;
        // First-write-wins — preserves the original arm even if the router
        // re-runs (defensive: shouldn't happen, but pinning here means no
        // amount of mid-session retry can flip the assignment).
        if (inflight.experimentKey == null) {
            inflight.experimentKey = experimentKey;
            inflight.variantLabel = variantLabel;
            log.info("[ChatAnalytics] conv={} tagged experiment '{}' variant '{}'",
                    conversationId, experimentKey, variantLabel);
        }
    }

    /**
     * Records a single tool invocation for the conversation: tool name,
     * wall-clock latency, success/failure. Aggregates accumulate on the
     * in-flight record (no I/O), so logging tool calls is cheap enough
     * to keep on the hot path. The {@code recordSessionEnd} flush carries
     * the totals into the analytics store row.
     *
     * <p>No-op when the conversation has no in-flight entry (e.g. the
     * Custom Tool preview endpoint runs outside an active session).
     *
     * @param conversationId active conversation id, or null/blank to skip
     * @param toolName       sanitized tool name (matches Spring AI's
     *                       {@code ToolDefinition.name()})
     * @param latencyMs      milliseconds the {@code call(...)} took, end-to-end
     * @param success        false when the callback threw or returned an
     *                       error envelope
     * @since 2026.2.7
     */
    public void recordToolCall(String conversationId, String toolName, long latencyMs, boolean success) {
        if (conversationId == null || conversationId.isBlank()) return;
        InFlight inflight = inFlight.get(conversationId);
        if (inflight == null) return;
        inflight.recordToolCall(toolName, latencyMs, success);
        log.debug("[ChatAnalytics] tool '{}' conv={} latencyMs={} success={}",
                toolName, conversationId, latencyMs, success);
    }

    /**
     * Idle-aware sweep that converts silent in-flight sessions into
     * {@code ABANDONED} rows in the store before evicting them. Two thresholds:
     *
     * <ul>
     *   <li><b>idle-timeout-min</b> (default 30) — entries whose
     *       {@code lastTouchedAt} hasn't moved within this window are flushed
     *       as {@link TurChatSessionOutcome#ABANDONED} via the normal
     *       {@link #recordSessionEnd} path. Their accumulated turns + tokens
     *       reach the store; the row's outcome stops being {@code IN_PROGRESS}.
     *       This is the threshold that turns silent conversations into
     *       analyzable rows.</li>
     *   <li><b>max-age-hours</b> (default 4) — absolute hard ceiling. Anything
     *       still in-flight past this is force-removed without a final flush
     *       (defends against bugs in idle tracking — should never trigger in
     *       normal operation).</li>
     * </ul>
     *
     * <p>Not cluster-locked: the in-flight map is per-process, so each node
     * trims its own state.
     */
    @Scheduled(fixedDelayString = "${turing.chat.analytics.inflight.sweep-ms:300000}")
    public void sweepStaleInFlight() {
        if (inFlight.isEmpty()) return;
        long now = System.currentTimeMillis();
        long idleCutoff   = now - inFlightIdleTimeoutMillis;
        long maxAgeCutoff = now - inFlightMaxAgeMillis;

        int flushed = 0;
        int forced  = 0;
        // Snapshot ids before iterating — recordSessionEnd mutates the map.
        var ids = new java.util.ArrayList<>(inFlight.keySet());
        for (String conversationId : ids) {
            InFlight entry = inFlight.get(conversationId);
            if (entry == null) continue;
            long lastTouched = entry.lastTouchedAt.toEpochMilli();
            long startedAt   = entry.startedAt.toEpochMilli();

            if (startedAt < maxAgeCutoff) {
                // Hard ceiling — drop without flushing. Should be rare.
                inFlight.remove(conversationId);
                forced++;
                continue;
            }
            if (lastTouched < idleCutoff) {
                try {
                    recordSessionEnd(conversationId, TurChatSessionOutcome.ABANDONED);
                    flushed++;
                } catch (RuntimeException e) {
                    log.warn("[ChatAnalytics] failed to flush idle session {}: {}",
                            conversationId, e.getMessage());
                    inFlight.remove(conversationId); // remove anyway so we don't retry forever
                }
            }
        }
        if (flushed > 0 || forced > 0) {
            log.info("[ChatAnalytics] sweep: flushed={} (ABANDONED), forced={} (max-age), remaining={}",
                    flushed, forced, inFlight.size());
        }
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
                    durationMs,
                    inflight == null ? 0  : inflight.toolCalls,
                    inflight == null ? 0  : inflight.toolErrors,
                    inflight == null ? 0L : inflight.totalToolLatencyMs,
                    inflight == null ? null : inflight.experimentKey,
                    inflight == null ? null : inflight.variantLabel,
                    inflight == null ? null : inflight.parentConversationId,
                    inflight == null ? null : inflight.timezone,
                    inflight == null ? null : inflight.deviceType,
                    inflight == null ? List.of() : List.copyOf(inflight.toolSamples)));
            if (inflight != null && inflight.toolCalls > 0) {
                log.info("[ChatAnalytics] session {} tool aggregates: calls={} errors={} totalLatencyMs={}",
                        conversationId, inflight.toolCalls, inflight.toolErrors,
                        inflight.totalToolLatencyMs);
            }
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
     * Mutable in-flight bag held only while the session is open. Synchronized
     * on increments so concurrent turns from the same conversation (rare —
     * usually serialized by the chat executor) don't corrupt counters.
     *
     * <p>{@code lastTouchedAt} drives the idle-timeout in
     * {@link #sweepStaleInFlight}: a session that hasn't recorded a turn
     * within the configured idle window is considered abandoned by the user
     * and gets flushed to the store as {@code ABANDONED}.
     */
    private static final class InFlight {
        final Instant startedAt;
        volatile Instant lastTouchedAt;
        final String agentId;
        final String personaId;
        final String llmInstanceId;
        final String embeddingModelId;
        final String storeInstanceId;
        final String userId;
        final String locale;
        /** T110 — parent conversation id when spawned by {@code agent.invoke(...)}. */
        final String parentConversationId;
        /** T74 — visitor cohort: IANA timezone (X-Timezone header), or null. */
        final String timezone;
        /** T74 — visitor cohort: device class from User-Agent, or null. */
        final String deviceType;
        int turns;
        long tokensIn;
        long tokensOut;
        int toolCalls;
        int toolErrors;
        long totalToolLatencyMs;
        /**
         * T88 — raw per-tool latency samples for the session, used to compute
         * disaggregated p95 across sessions at query time. Bounded by
         * {@link #MAX_TOOL_SAMPLES}: the aggregate counters above stay exact
         * past the cap (so {@code avg_tool_latency_ms} is unaffected), only the
         * percentile sample reservoir stops growing. Chat sessions invoke a
         * handful of tools, so the cap is effectively never hit in practice.
         */
        final List<TurToolLatencySample> toolSamples = new java.util.ArrayList<>();
        // Set lazily after the flow router assigns an experiment variant —
        // happens on the FIRST turn that lands on an experimentKey'd flow.
        // Sticky thereafter (a conversation never reflows experiments mid-life).
        volatile String experimentKey;
        volatile String variantLabel;

        InFlight(Instant startedAt, String agentId, String personaId, String llmInstanceId,
                String embeddingModelId, String storeInstanceId, String userId, String locale,
                String parentConversationId, String timezone, String deviceType) {
            this.startedAt = startedAt;
            this.lastTouchedAt = startedAt;
            this.agentId = agentId;
            this.personaId = personaId;
            this.llmInstanceId = llmInstanceId;
            this.embeddingModelId = embeddingModelId;
            this.storeInstanceId = storeInstanceId;
            this.userId = userId;
            this.locale = locale;
            this.parentConversationId = parentConversationId;
            this.timezone = timezone;
            this.deviceType = deviceType;
        }

        synchronized void recordTurn(long in, long out) {
            this.turns += 1;
            this.tokensIn += Math.max(0, in);
            this.tokensOut += Math.max(0, out);
            this.lastTouchedAt = Instant.now();
        }

        synchronized void recordToolCall(String toolName, long latencyMs, boolean success) {
            this.toolCalls += 1;
            if (!success) this.toolErrors += 1;
            this.totalToolLatencyMs += Math.max(0, latencyMs);
            // T88 — keep the raw sample for per-tool percentiles, bounded so a
            // pathologically chatty session can't grow the map without limit.
            if (toolSamples.size() < MAX_TOOL_SAMPLES) {
                toolSamples.add(new TurToolLatencySample(toolName, latencyMs, success));
            }
            this.lastTouchedAt = Instant.now();
        }
    }

    /**
     * Per-session ceiling on retained per-tool latency samples (T88). The
     * aggregate counters ({@code toolCalls}/{@code toolErrors}/
     * {@code totalToolLatencyMs}) keep counting past it, so only percentile
     * fidelity degrades on a runaway session — never the average or the call
     * count.
     */
    private static final int MAX_TOOL_SAMPLES = 1000;
}

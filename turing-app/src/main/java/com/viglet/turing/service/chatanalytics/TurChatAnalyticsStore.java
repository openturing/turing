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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pluggable persistence for chat usage analytics — companion to
 * {@link com.viglet.turing.service.chatmemory.TurChatMemoryStore} that holds
 * one document per conversation (start + end states), suitable for product
 * dashboards (audience, intent, persona effectiveness, abandonment).
 *
 * <p>Implementations are picked at startup by {@link TurChatAnalyticsConfig}
 * based on {@code turing.logging.engine}. The NoOp implementation is the
 * default whenever the engine is {@code none} or the matching backend's
 * {@code enabled} flag is off — keeps callers unaware of the wiring.
 *
 * <p>The read API ({@link #findRecentSessions}, {@link #aggregate}) is what
 * the REST endpoints under {@code /api/system/chat-analytics/**} expose to
 * Grafana so dashboards stay agnostic of the underlying store.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public interface TurChatAnalyticsStore {

    /** Whether the store is wired up and writes will be persisted. */
    boolean isEnabled();

    /** Storage backend identifier — useful for diagnostics. */
    TurChatAnalyticsEngine getEngine();

    /**
     * Upserts a {@link TurChatSessionEvent} keyed by {@code conversationId}.
     * Implementations preserve start-only fields (e.g. {@code startedAt})
     * when called again with an end event.
     */
    void upsertSession(TurChatSessionEvent event);

    /**
     * Returns up to {@code limit} most recent sessions whose
     * {@code completedAt} or {@code startedAt} is in {@code [from, to]}.
     * Optional filters: agent, persona, outcome, intentLabel, goalAchieved,
     * sentiment. Null/blank filter values are ignored.
     *
     * <p>Result is a list of plain maps (column → value) ready for JSON
     * serialization — keeps the API engine-agnostic and avoids leaking BSON
     * or Redis types to callers.
     */
    List<Map<String, Object>> findRecentSessions(Instant from, Instant to,
            String agentId, String personaId, String outcome,
            String intentLabel, String goalAchieved, String sentiment,
            int limit);

    /**
     * Returns aggregated counts per bucket — e.g. sessions by outcome, or
     * sessions per agent. {@code groupBy} is the document field name.
     * The store implementation may return at most {@code maxGroups} buckets
     * sorted descending by count. Buckets with empty group key are reported
     * as {@code "unknown"}.
     */
    List<Map<String, Object>> aggregate(Instant from, Instant to, String groupBy, int maxGroups);

    /**
     * Returns up to {@code limit} sessions that are eligible for enrichment —
     * the {@link com.viglet.turing.service.chatanalytics.TurChatAnalyticsEnricher}
     * scheduled job uses this to find unprocessed terminal sessions.
     *
     * <p>Eligibility: outcome is set (i.e. {@code != IN_PROGRESS}) and
     * {@code processedAt} is missing.
     */
    List<Map<String, Object>> findUnenrichedSessions(int limit);

    /**
     * Merges the AI-on-AI annotation onto an existing session document. The
     * implementation must preserve every other field (start/end timestamps,
     * aggregates, identity columns) and stamp {@code processedAt = now()} so
     * subsequent calls to {@link #findUnenrichedSessions} skip the row.
     */
    void enrichSession(String conversationId, TurChatSessionEnrichment enrichment);

    /**
     * Time-bucketed values for a single metric over {@code [from, to]}, sorted
     * ascending by bucket. Backs the trend panels in the Grafana dashboard so
     * leaders can see whether goal-achievement / sentiment / duration are
     * drifting up or down without re-running point-in-time aggregates.
     *
     * <p>{@code interval} accepts {@code hour} or {@code day} (others fall back
     * to {@code hour}). {@code metric} is whitelisted by the implementation;
     * unknown values default to {@code sessions}.
     *
     * <p>Each row carries:
     * <ul>
     *   <li>{@code bucket} — ISO-8601 instant marking the start of the bucket;</li>
     *   <li>{@code value} — the metric value for the bucket (rate, average, or count);</li>
     *   <li>{@code total} — sessions in the bucket. For rate metrics this is the
     *       enriched-sessions denominator; consumers can use it as a sample-size
     *       sanity check.</li>
     * </ul>
     *
     * <p>Supported metrics:
     * <ul>
     *   <li>{@code sessions} — count of sessions started in the bucket</li>
     *   <li>{@code goal_achievement_rate} — YES counts as 1, PARTIAL as 0.5, others 0;
     *       divided by enriched sessions in the bucket</li>
     *   <li>{@code negative_sentiment_rate} — fraction of enriched sessions with
     *       sentiment NEGATIVE or FRUSTRATED</li>
     *   <li>{@code avg_duration_ms} — average session duration in ms</li>
     *   <li>{@code avg_tokens_out} — average totalTokensOut per session</li>
     * </ul>
     */
    List<Map<String, Object>> timeseries(Instant from, Instant to, String metric, String interval);

    /**
     * Per-dimension scorecard — one row per agent (or persona) with sessions,
     * goal-achievement rate, negative+frustrated sentiment rate, avg duration,
     * and the most common intent label. Powers the comparison table in the
     * Grafana dashboard so we can spot which agent/persona is dragging the
     * mean down.
     *
     * <p>{@code dimension} accepts {@code agentId} or {@code personaId};
     * unknown values default to {@code agentId}. {@code maxRows} is capped to
     * 100 by the implementation.
     *
     * <p>T74 / §VII.8.e — {@code filters} narrows the population BEFORE
     * grouping, so an operator can ask "does this variant win on
     * <i>mobile</i>?" by grouping on {@code variantLabel} while filtering
     * {@code deviceType=mobile}. Keys are whitelisted by the implementation
     * (cohort: {@code deviceType}/{@code locale}/{@code timezone}; identity:
     * {@code agentId}/{@code personaId}/{@code experimentKey}/
     * {@code variantLabel}/{@code outcome}); unknown keys and blank values are
     * ignored. A {@code null}/empty map applies no filter (deployment-wide).
     */
    List<Map<String, Object>> scorecard(Instant from, Instant to, String dimension,
            Map<String, String> filters, int maxRows);

    /**
     * T88 / §VII.10.e — per-tool latency percentiles over {@code [from, to]},
     * one row per distinct tool, sorted by p95 descending so the outlier floats
     * to the top. Answers "<i>which</i> tool is the p95 outlier?" — the
     * session-level {@code avg_tool_latency_ms} only exposes an average across
     * every tool, hiding a single slow tool behind a sea of fast ones.
     *
     * <p>Computed by pooling the raw {@link TurToolLatencySample}s the store
     * persisted per session ({@link TurChatSessionEvent#toolLatencies()}) and
     * running {@link TurToolLatencyAccumulator} over them — both backends share
     * the same percentile math so Mongo and Redis agree to the millisecond.
     * Percentiles aren't composable from per-session sums, hence the raw
     * samples rather than a re-aggregation of {@code totalToolLatencyMs}.
     *
     * <p>Each row carries: {@code tool}, {@code count}, {@code errors},
     * {@code errorRatePct} (0-100), {@code avgMs}, {@code minMs}, {@code p50Ms},
     * {@code p95Ms}, {@code p99Ms}, {@code maxMs}.
     *
     * @param from     window start (inclusive), matched on {@code startedAt}
     * @param to       window end (inclusive)
     * @param agentId  optional agent filter; null/blank means deployment-wide
     * @param maxTools cap on returned rows (the top-N by p95), 1-200
     */
    List<Map<String, Object>> toolLatency(Instant from, Instant to, String agentId, int maxTools);

    /**
     * Returns a single session document keyed by {@code conversationId}, or
     * {@code null} if not found. Same column shape as
     * {@link #findRecentSessions} so callers can render with the same mapper.
     * Backs the drill-down endpoint at {@code /sessions/{id}}.
     */
    Map<String, Object> findSessionById(String conversationId);

    /**
     * Deletes sessions whose {@code startedAt} is strictly older than
     * {@code threshold}. Returns the number of rows removed for logging.
     * Implementations should be best-effort — failures are logged and reported
     * as {@code 0}.
     */
    long purgeOlderThan(Instant threshold);

    /**
     * Creates whatever secondary indexes / sorted-set scaffolding the engine
     * needs for the read API to stay fast. Idempotent; safe to call on every
     * startup. The default no-op is correct for engines that don't need it.
     */
    default void ensureIndexes() {
        // no-op by default
    }

    /**
     * T74 — fields a scorecard {@code filters} map may legitimately target.
     * Shared by every store so the Mongo {@code $match} and the Redis
     * in-process filter agree on exactly which columns are filterable, closing
     * the same field-injection vector {@code sanitizeFieldName} guards for
     * {@code groupBy}. Cohort fields ({@code deviceType}/{@code locale}/
     * {@code timezone}) plus the identity/experiment dimensions already used
     * for grouping.
     */
    Set<String> SCORECARD_FILTERABLE_FIELDS = Set.of(
            "agentId", "personaId", "experimentKey", "variantLabel",
            "locale", "timezone", "deviceType", "outcome");

    /**
     * Maps a scorecard {@code dimension} request to the stored document field
     * to group on, whitelisting the accepted values (unknown → {@code agentId}).
     * Shared so Mongo and Redis pivot on identical fields. T74 adds the three
     * cohort dimensions.
     */
    static String scorecardDimensionField(String dimension) {
        return switch (dimension == null ? "" : dimension.trim()) {
            case "personaId"           -> "personaId";
            case "experimentKey"       -> "experimentKey";
            case "variantLabel"        -> "variantLabel";
            case "parentConversationId" -> "parentConversationId";
            // T74 cohort dimensions.
            case "locale"     -> "locale";
            case "timezone"   -> "timezone";
            case "deviceType" -> "deviceType";
            default -> "agentId";
        };
    }
}

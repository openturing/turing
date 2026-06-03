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

/**
 * Snapshot of one chat conversation persisted by {@link TurChatAnalyticsStore}.
 * Recorded twice during the session lifecycle:
 *
 * <ul>
 *   <li>Once at <b>session start</b> ({@link #completedAt} = {@code null},
 *       {@link #outcome} = {@code IN_PROGRESS}) so dashboards can already see
 *       in-flight conversations and detect abandonment by the absence of an
 *       end record.</li>
 *   <li>Once at <b>session end</b> with all aggregates filled, when the flow
 *       engine records a terminal submission (or the explicit
 *       {@code DELETE /api/v2/ai-agent/{id}/chat-flow-state} reset call
 *       fires).</li>
 * </ul>
 *
 * <p>Designed as a single immutable record per persistence call — the store
 * implementations upsert by {@code conversationId} so the end record fully
 * supersedes the start record without losing the start fields.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public record TurChatSessionEvent(
        String conversationId,
        String agentId,
        String personaId,
        String llmInstanceId,
        String embeddingModelId,
        String storeInstanceId,
        String userId,
        String locale,
        String firstUserMessage,
        Instant startedAt,
        Instant completedAt,
        TurChatSessionOutcome outcome,
        int totalTurns,
        long totalTokensIn,
        long totalTokensOut,
        long durationMs,
        /** Total tool callbacks invoked during the session (across all tools). */
        int totalToolCalls,
        /** Subset of {@link #totalToolCalls} that threw or returned an error. */
        int totalToolErrors,
        /** Sum of per-call wall-clock latency for every tool invocation, in ms. */
        long totalToolLatencyMs,
        /**
         * A/B experiment id this conversation participated in, or {@code null}
         * when the active flow was outside any experiment. Joins to all the
         * variants under the same key for cross-variant comparisons.
         */
        String experimentKey,
        /**
         * Variant arm the conversation was assigned to inside
         * {@link #experimentKey} (e.g. {@code "control"}, {@code "treatment_v2"}).
         * {@code null} when {@link #experimentKey} is null.
         */
        String variantLabel,
        /**
         * Parent conversation id when this session was spawned by a
         * {@code agent.invoke(...)} call from a Custom Tool (T109). Links
         * the child session back to its orchestrator so the scorecard can
         * drill down ("orchestrator agent X invoked specialist Y in N
         * sessions, average sub-task duration M ms"). {@code null} for
         * top-level (visitor-initiated) sessions.
         *
         * @since 2026.3.1 (T110)
         */
        String parentConversationId,
        /**
         * IANA timezone id the visitor's browser reported (e.g.
         * {@code "America/Sao_Paulo"}), captured from the {@code X-Timezone}
         * request header the SDK populates via
         * {@code Intl.DateTimeFormat().resolvedOptions().timeZone}. {@code null}
         * when the client didn't send it. One of the three T74 cohort
         * dimensions used to slice the scorecard into sub-populations.
         *
         * @since 2026.3.1 (T74)
         */
        String timezone,
        /**
         * Device class derived from the {@code User-Agent} header —
         * {@code "mobile"} / {@code "tablet"} / {@code "desktop"} /
         * {@code "bot"} / {@code "unknown"}. The headline T74 cohort
         * ("does this variant win on mobile vs desktop?"). Server-side
         * classification via {@link TurChatCohortResolver}, so it's populated
         * for every visitor without client cooperation.
         *
         * @since 2026.3.1 (T74)
         */
        String deviceType,
        /**
         * Raw per-tool-call latency samples captured during the session, one
         * entry per invocation ({@code (tool, latencyMs, success)}). Empty on
         * the start record and for sessions that never called a tool. Backs the
         * T88 per-tool p95 view: the session-level {@link #totalToolLatencyMs}
         * only yields an <em>average</em>, but p95 isn't composable from sums,
         * so the individual samples are kept and the dashboard pools them across
         * sessions at query time. The capture-side cap (see
         * {@code TurChatAnalyticsService}) bounds this list per session.
         *
         * @since 2026.3.1 (T88)
         */
        List<TurToolLatencySample> toolLatencies) {

    /**
     * Compact canonical constructor — normalizes {@code toolLatencies} to an
     * immutable empty list when {@code null} so the field is never null on the
     * wire (mirrors the T87 {@code sentimentTrajectory} idiom) and callers can
     * iterate it without a null guard.
     */
    public TurChatSessionEvent {
        toolLatencies = toolLatencies == null ? List.of() : List.copyOf(toolLatencies);
    }

    /** Builder of the start record (most aggregate fields zeroed/null). */
    public static TurChatSessionEvent start(String conversationId, String agentId,
            String personaId, String llmInstanceId, String embeddingModelId,
            String storeInstanceId, String userId, String locale, String firstUserMessage,
            Instant startedAt) {
        return start(conversationId, agentId, personaId, llmInstanceId,
                embeddingModelId, storeInstanceId, userId, locale, firstUserMessage,
                startedAt, null);
    }

    /**
     * Overload accepting a parent conversation id — used by T110 when a
     * Custom Tool's {@code agent.invoke(...)} call spawns a child session.
     *
     * @since 2026.3.1
     */
    public static TurChatSessionEvent start(String conversationId, String agentId,
            String personaId, String llmInstanceId, String embeddingModelId,
            String storeInstanceId, String userId, String locale, String firstUserMessage,
            Instant startedAt, String parentConversationId) {
        return start(conversationId, agentId, personaId, llmInstanceId,
                embeddingModelId, storeInstanceId, userId, locale, firstUserMessage,
                startedAt, parentConversationId, null, null);
    }

    /**
     * Richest start overload — also carries the T74 visitor cohort fields
     * ({@code timezone} from the {@code X-Timezone} header, {@code deviceType}
     * classified from the {@code User-Agent}). The two legacy overloads above
     * delegate here with {@code null} cohort fields so existing call sites
     * stay source-compatible.
     *
     * @since 2026.3.1 (T74)
     */
    public static TurChatSessionEvent start(String conversationId, String agentId,
            String personaId, String llmInstanceId, String embeddingModelId,
            String storeInstanceId, String userId, String locale, String firstUserMessage,
            Instant startedAt, String parentConversationId, String timezone, String deviceType) {
        return new TurChatSessionEvent(conversationId, agentId, personaId,
                llmInstanceId, embeddingModelId, storeInstanceId, userId, locale,
                firstUserMessage, startedAt, null, TurChatSessionOutcome.IN_PROGRESS,
                0, 0L, 0L, 0L, 0, 0, 0L, null, null, parentConversationId,
                timezone, deviceType, List.of());
    }
}

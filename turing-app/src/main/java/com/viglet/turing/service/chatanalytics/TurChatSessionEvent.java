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
        long durationMs) {

    /** Builder of the start record (most aggregate fields zeroed/null). */
    public static TurChatSessionEvent start(String conversationId, String agentId,
            String personaId, String llmInstanceId, String embeddingModelId,
            String storeInstanceId, String userId, String locale, String firstUserMessage,
            Instant startedAt) {
        return new TurChatSessionEvent(conversationId, agentId, personaId,
                llmInstanceId, embeddingModelId, storeInstanceId, userId, locale,
                firstUserMessage, startedAt, null, TurChatSessionOutcome.IN_PROGRESS,
                0, 0L, 0L, 0L);
    }
}

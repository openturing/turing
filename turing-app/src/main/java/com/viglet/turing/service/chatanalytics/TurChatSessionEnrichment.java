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
 * AI-on-AI annotation produced by {@link TurChatIntentClassifier}, applied
 * post-hoc to a finished session. Persisted onto the same document the
 * {@link TurChatSessionEvent} writes; never modifies the start/end timestamps
 * or aggregate counters.
 *
 * <p>{@code processedAt} is the watermark that lets the scheduled enricher
 * find unprocessed sessions — it's set by the store on success and never
 * cleared.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public record TurChatSessionEnrichment(
        TurChatIntentLabel intentLabel,
        double intentConfidence,
        String goalSummary,
        TurChatGoalAchieved goalAchieved,
        TurChatSentiment sentiment,
        List<String> keyTerms,
        /**
         * T87 / §VII.10.d — per-turn sentiment trajectory, one entry per user
         * turn in chronological order (turn {@code 1..N}). Lets the drill-down
         * render a line chart that pinpoints where a conversation soured rather
         * than collapsing it to the single session-level {@link #sentiment}.
         *
         * <p>Only the LLM classifier populates it; the catalog-driven (Lucene /
         * search-engine MLT) strategies leave it empty. Never {@code null} — the
         * canonical constructor normalizes a {@code null} list to {@link List#of()}.
         *
         * @since 2026.3.1
         */
        List<TurChatSentiment> sentimentTrajectory,
        Instant processedAt) {

    public TurChatSessionEnrichment {
        // Keep the trajectory non-null so store/serialization paths never NPE
        // and the frontend can treat "absent" and "empty" identically.
        sentimentTrajectory = sentimentTrajectory == null ? List.of() : List.copyOf(sentimentTrajectory);
    }

    /**
     * Legacy 7-arg constructor (pre-T87) — kept source-compatible for the
     * catalog-driven classifier strategies and tests that don't compute a
     * per-turn trajectory. Delegates with an empty trajectory.
     */
    public TurChatSessionEnrichment(TurChatIntentLabel intentLabel, double intentConfidence,
            String goalSummary, TurChatGoalAchieved goalAchieved, TurChatSentiment sentiment,
            List<String> keyTerms, Instant processedAt) {
        this(intentLabel, intentConfidence, goalSummary, goalAchieved, sentiment,
                keyTerms, List.of(), processedAt);
    }

    /** Used by the enricher when the LLM is unavailable; marks the session
     *  processed so it isn't retried in a tight loop, but with UNCLASSIFIED. */
    public static TurChatSessionEnrichment unclassified() {
        return new TurChatSessionEnrichment(
                TurChatIntentLabel.UNCLASSIFIED, 0.0, null,
                TurChatGoalAchieved.UNKNOWN, TurChatSentiment.UNKNOWN,
                List.of(), List.of(), Instant.now());
    }
}

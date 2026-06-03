/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatanalytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the T87 per-turn sentiment-trajectory parsing surface of
 * {@link TurLlmIntentClassifier} plus the
 * {@link TurChatSessionEnrichment} record normalization. Both are pure helpers
 * — no LLM call, no Spring context — so they pin the lenient JSON contract the
 * classifier relies on without a Testcontainers backend.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurLlmIntentClassifierTrajectoryTest {

    @Test
    void parsesWellFormedTrajectory() {
        List<TurChatSentiment> trajectory = TurLlmIntentClassifier.parseSentimentTrajectory(
                List.of("POSITIVE", "neutral", "NEGATIVE", "FRUSTRATED"));

        assertThat(trajectory).containsExactly(
                TurChatSentiment.POSITIVE,
                TurChatSentiment.NEUTRAL,
                TurChatSentiment.NEGATIVE,
                TurChatSentiment.FRUSTRATED);
    }

    @Test
    void unknownLabelsCollapseToUnknownNotDropped() {
        // Keep alignment with the turn index — a bad label must not shrink the array.
        List<TurChatSentiment> trajectory = TurLlmIntentClassifier.parseSentimentTrajectory(
                List.of("POSITIVE", "ecstatic", "NEGATIVE"));

        assertThat(trajectory).containsExactly(
                TurChatSentiment.POSITIVE,
                TurChatSentiment.UNKNOWN,
                TurChatSentiment.NEGATIVE);
    }

    @Test
    void nonListInputsYieldEmptyList() {
        assertThat(TurLlmIntentClassifier.parseSentimentTrajectory(null)).isEmpty();
        assertThat(TurLlmIntentClassifier.parseSentimentTrajectory("POSITIVE")).isEmpty();
        assertThat(TurLlmIntentClassifier.parseSentimentTrajectory(List.of())).isEmpty();
    }

    @Test
    void trajectoryIsCappedAtMaxTurns() {
        List<Object> oversize = new ArrayList<>();
        for (int i = 0; i < TurLlmIntentClassifier.MAX_TRAJECTORY_TURNS + 50; i++) {
            oversize.add("NEUTRAL");
        }
        assertThat(TurLlmIntentClassifier.parseSentimentTrajectory(oversize))
                .hasSize(TurLlmIntentClassifier.MAX_TRAJECTORY_TURNS);
    }

    @Test
    void enrichmentRecordNormalizesNullTrajectoryToEmpty() {
        TurChatSessionEnrichment enrichment = new TurChatSessionEnrichment(
                TurChatIntentLabel.OTHER, 0.5, "goal", TurChatGoalAchieved.YES,
                TurChatSentiment.POSITIVE, List.of("term"), null, java.time.Instant.now());

        assertThat(enrichment.sentimentTrajectory()).isNotNull().isEmpty();
    }

    @Test
    void legacySevenArgConstructorDefaultsTrajectoryToEmpty() {
        // Catalog-driven classifiers (Lucene / SE-MLT) still use the pre-T87 shape.
        TurChatSessionEnrichment enrichment = new TurChatSessionEnrichment(
                TurChatIntentLabel.SUPPORT, 0.9, "summary", TurChatGoalAchieved.PARTIAL,
                TurChatSentiment.NEUTRAL, List.of("a", "b"), java.time.Instant.now());

        assertThat(enrichment.sentimentTrajectory()).isEmpty();
        assertThat(enrichment.sentiment()).isEqualTo(TurChatSentiment.NEUTRAL);
    }

    @Test
    void unclassifiedHasEmptyTrajectory() {
        assertThat(TurChatSessionEnrichment.unclassified().sentimentTrajectory()).isEmpty();
    }
}

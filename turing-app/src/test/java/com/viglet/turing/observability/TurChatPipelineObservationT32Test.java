/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Tests for the T32 / §IV.7 observability helpers added to
 * {@link TurChatPipelineObservation}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatPipelineObservationT32Test {

    private MeterRegistry registry;
    private TurChatPipelineObservation obs;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        obs = new TurChatPipelineObservation(registry);
    }

    // ──────────────── memory retrieval (T30) ────────────────

    @Test
    void recordMemoryRetrieval_hitsOutcome_recordsTimerAndBothDistributions() {
        obs.recordMemoryRetrieval(45, 3, 12L, TurMeterNames.OUTCOME_HITS);

        Timer t = registry.find(TurMeterNames.CHAT_MEMORY_RETRIEVAL)
                .tags(Tags.of(TurMeterNames.TAG_OUTCOME, TurMeterNames.OUTCOME_HITS))
                .timer();
        assertThat(t).isNotNull();
        assertThat(t.count()).isEqualTo(1);
        assertThat(t.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(12.0);

        DistributionSummary poolSummary = registry.find(TurMeterNames.CHAT_MEMORY_RETRIEVAL_POOL)
                .summary();
        assertThat(poolSummary.count()).isEqualTo(1);
        assertThat(poolSummary.totalAmount()).isEqualTo(45.0);

        DistributionSummary hitsSummary = registry.find(TurMeterNames.CHAT_MEMORY_RETRIEVAL_HITS)
                .summary();
        assertThat(hitsSummary.count()).isEqualTo(1);
        assertThat(hitsSummary.totalAmount()).isEqualTo(3.0);
    }

    @Test
    void recordMemoryRetrieval_noHitsOutcome_recordsTimerAndZeroHits() {
        obs.recordMemoryRetrieval(8, 0, 7L, TurMeterNames.OUTCOME_NO_HITS);

        Timer t = registry.find(TurMeterNames.CHAT_MEMORY_RETRIEVAL)
                .tags(Tags.of(TurMeterNames.TAG_OUTCOME, TurMeterNames.OUTCOME_NO_HITS))
                .timer();
        assertThat(t.count()).isEqualTo(1);

        // Pool size still recorded (BM25 ran, just didn't match) — the
        // 0 in the hits summary is the meaningful signal for dashboards.
        DistributionSummary hitsSummary = registry.find(TurMeterNames.CHAT_MEMORY_RETRIEVAL_HITS)
                .summary();
        assertThat(hitsSummary.count()).isEqualTo(1);
        assertThat(hitsSummary.totalAmount()).isEqualTo(0.0);
    }

    @Test
    void recordMemoryRetrieval_bypassOutcome_skipsDistributionsButRecordsTimer() {
        obs.recordMemoryRetrieval(-1, -1, 1L, TurMeterNames.OUTCOME_BYPASS);

        Timer t = registry.find(TurMeterNames.CHAT_MEMORY_RETRIEVAL)
                .tags(Tags.of(TurMeterNames.TAG_OUTCOME, TurMeterNames.OUTCOME_BYPASS))
                .timer();
        assertThat(t.count()).isEqualTo(1);

        // Both distributions skipped — caller passed -1 so zero-population
        // samples don't pollute the histograms.
        assertThat(registry.find(TurMeterNames.CHAT_MEMORY_RETRIEVAL_POOL).summary())
                .isNull();
        assertThat(registry.find(TurMeterNames.CHAT_MEMORY_RETRIEVAL_HITS).summary())
                .isNull();
    }

    @Test
    void recordMemoryRetrieval_aggregatesAcrossMultipleCalls() {
        obs.recordMemoryRetrieval(10, 2, 5L, TurMeterNames.OUTCOME_HITS);
        obs.recordMemoryRetrieval(20, 4, 10L, TurMeterNames.OUTCOME_HITS);
        obs.recordMemoryRetrieval(15, 0, 6L, TurMeterNames.OUTCOME_NO_HITS);

        Timer hitsTimer = registry.find(TurMeterNames.CHAT_MEMORY_RETRIEVAL)
                .tags(Tags.of(TurMeterNames.TAG_OUTCOME, TurMeterNames.OUTCOME_HITS))
                .timer();
        assertThat(hitsTimer.count()).isEqualTo(2);

        Timer noHitsTimer = registry.find(TurMeterNames.CHAT_MEMORY_RETRIEVAL)
                .tags(Tags.of(TurMeterNames.TAG_OUTCOME, TurMeterNames.OUTCOME_NO_HITS))
                .timer();
        assertThat(noHitsTimer.count()).isEqualTo(1);

        DistributionSummary hitsSummary = registry.find(TurMeterNames.CHAT_MEMORY_RETRIEVAL_HITS)
                .summary();
        // All 3 calls had non-negative hits (2+4+0) → all sampled
        assertThat(hitsSummary.count()).isEqualTo(3);
        assertThat(hitsSummary.totalAmount()).isEqualTo(6.0);
    }

    // ──────────────── tool prefilter (T29) ────────────────

    @Test
    void recordToolPrefilter_engagedReason_tagsFilteredOutcome() {
        obs.recordToolPrefilter(48, 12, 18L, TurMeterNames.REASON_ENGAGED);

        Timer t = registry.find(TurMeterNames.CHAT_TOOL_PREFILTER)
                .tags(Tags.of(TurMeterNames.TAG_OUTCOME, TurMeterNames.OUTCOME_FILTERED))
                .timer();
        assertThat(t.count()).isEqualTo(1);

        // Bypass counter tagged by reason
        assertThat(registry.find(TurMeterNames.CHAT_TOOL_PREFILTER_BYPASS)
                .tags(Tags.of(TurMeterNames.TAG_REASON, TurMeterNames.REASON_ENGAGED))
                .counter().count()).isEqualTo(1.0);

        // Input/output distributions
        assertThat(registry.find(TurMeterNames.CHAT_TOOL_PREFILTER_INPUT).summary().totalAmount())
                .isEqualTo(48.0);
        assertThat(registry.find(TurMeterNames.CHAT_TOOL_PREFILTER_OUTPUT).summary().totalAmount())
                .isEqualTo(12.0);
    }

    @Test
    void recordToolPrefilter_bypassReasonsTagPassthroughOutcome() {
        obs.recordToolPrefilter(8, 8, 0L, TurMeterNames.REASON_BELOW_THRESHOLD);
        obs.recordToolPrefilter(50, 50, 0L, TurMeterNames.REASON_BLANK_QUERY);

        Timer passthroughTimer = registry.find(TurMeterNames.CHAT_TOOL_PREFILTER)
                .tags(Tags.of(TurMeterNames.TAG_OUTCOME, TurMeterNames.OUTCOME_PASSTHROUGH))
                .timer();
        assertThat(passthroughTimer.count()).isEqualTo(2);

        assertThat(registry.find(TurMeterNames.CHAT_TOOL_PREFILTER_BYPASS)
                .tags(Tags.of(TurMeterNames.TAG_REASON, TurMeterNames.REASON_BELOW_THRESHOLD))
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.find(TurMeterNames.CHAT_TOOL_PREFILTER_BYPASS)
                .tags(Tags.of(TurMeterNames.TAG_REASON, TurMeterNames.REASON_BLANK_QUERY))
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void recordToolPrefilter_allFiveBypassReasonsExposeIndependentCounters() {
        // Pin the cardinality on the bypass counter — each reason gets its
        // own bucket; the engaged reason also lives alongside as a sixth.
        for (String reason : new String[] {
                TurMeterNames.REASON_DISABLED,
                TurMeterNames.REASON_BELOW_THRESHOLD,
                TurMeterNames.REASON_BLANK_QUERY,
                TurMeterNames.REASON_ALREADY_COVERED,
                TurMeterNames.REASON_CAP_COVERS_POOL,
                TurMeterNames.REASON_ENGAGED }) {
            obs.recordToolPrefilter(10, 10, 0L, reason);
        }
        long uniqueCounters = registry.find(TurMeterNames.CHAT_TOOL_PREFILTER_BYPASS)
                .counters().size();
        assertThat(uniqueCounters).isEqualTo(6);
    }
}

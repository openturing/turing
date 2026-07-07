/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval.grader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.eval.grader.TurEvalStackAggregator.Aggregate;
import com.viglet.turing.genai.eval.grader.TurEvalStackAggregator.Contribution;

/**
 * T600 / §XXXIII.15 — the weighted grader-stack aggregator: weighted mean,
 * per-grader thresholds, the blocking policy, and byte-identical legacy default.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurEvalStackAggregatorTest {

    @Test
    void emptyStackScoresOneAndPasses() {
        Aggregate a = TurEvalStackAggregator.aggregate(List.of());
        assertThat(a.score()).isEqualTo(1d);
        assertThat(a.passed()).isTrue();
    }

    @Test
    void defaultStackIsMeanAndAllMustPass() {
        // weight 1, threshold 0, blocking true — the legacy behaviour.
        Aggregate a = TurEvalStackAggregator.aggregate(List.of(
                new Contribution(1.0, true, 1d, 0d, true),
                new Contribution(0.0, false, 1d, 0d, true)));
        assertThat(a.score()).isCloseTo(0.5d, within(1e-9));
        assertThat(a.passed()).isFalse();
    }

    @Test
    void weightsBiasTheScore() {
        Aggregate a = TurEvalStackAggregator.aggregate(List.of(
                new Contribution(1.0, true, 3d, 0d, true),
                new Contribution(0.0, true, 1d, 0d, true)));
        // (3*1 + 1*0) / 4 = 0.75
        assertThat(a.score()).isCloseTo(0.75d, within(1e-9));
        assertThat(a.passed()).isTrue();
    }

    @Test
    void thresholdCanFailAGraderEvenWhenItPassed() {
        Aggregate a = TurEvalStackAggregator.aggregate(List.of(
                new Contribution(0.6, true, 1d, 0.8d, true)));
        assertThat(a.passed()).isFalse();
    }

    @Test
    void nonBlockingGraderCountsTowardScoreButNeverFailsTheCase() {
        Aggregate a = TurEvalStackAggregator.aggregate(List.of(
                new Contribution(1.0, true, 1d, 0d, true),
                new Contribution(0.0, false, 1d, 0d, false)));
        assertThat(a.score()).isCloseTo(0.5d, within(1e-9));
        assertThat(a.passed()).isTrue();
    }

    @Test
    void nonPositiveWeightTreatedAsOne() {
        Aggregate a = TurEvalStackAggregator.aggregate(List.of(
                new Contribution(1.0, true, 0d, 0d, true),
                new Contribution(0.0, true, 0d, 0d, true)));
        assertThat(a.score()).isCloseTo(0.5d, within(1e-9));
    }
}

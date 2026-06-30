/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.rag.rerank.TurLogprobConfidence.TokenLogprob;

class TurLogprobConfidenceTest {

    @Test
    void relevanceProbabilityNormalizesYesAgainstNo() {
        // ln(0.8) ≈ -0.223, ln(0.2) ≈ -1.609 → P(yes) = 0.8/(0.8+0.2) = 0.8.
        double p = TurLogprobConfidence.relevanceProbability(List.of(
                new TokenLogprob("yes", Math.log(0.8)),
                new TokenLogprob("no", Math.log(0.2))));
        assertThat(p).isCloseTo(0.8, within(1e-9));
    }

    @Test
    void relevanceProbabilityIsCaseAndWhitespaceInsensitiveAndPrefixMatches() {
        double p = TurLogprobConfidence.relevanceProbability(List.of(
                new TokenLogprob(" YES", Math.log(0.6)),
                new TokenLogprob("No", Math.log(0.4))));
        assertThat(p).isCloseTo(0.6, within(1e-9));
    }

    @Test
    void onlyNoTokenYieldsItsComplement() {
        // ln(0.9) for "no" with no "yes" alternative → P(yes) = 1 - 0.9 = 0.1.
        double p = TurLogprobConfidence.relevanceProbability(List.of(
                new TokenLogprob("no", Math.log(0.9))));
        assertThat(p).isCloseTo(0.1, within(1e-9));
    }

    @Test
    void neutralWhenNeitherPolarityPresentOrEmpty() {
        assertThat(TurLogprobConfidence.relevanceProbability(List.of(
                new TokenLogprob("maybe", Math.log(0.5))))).isEqualTo(0.5);
        assertThat(TurLogprobConfidence.relevanceProbability(List.of())).isEqualTo(0.5);
        assertThat(TurLogprobConfidence.relevanceProbability(null)).isEqualTo(0.5);
    }

    @Test
    void reciprocalRankScoreDecaysWithRank() {
        assertThat(TurLogprobConfidence.reciprocalRankScore(0)).isEqualTo(1.0);
        assertThat(TurLogprobConfidence.reciprocalRankScore(1)).isCloseTo(0.5, within(1e-9));
        assertThat(TurLogprobConfidence.reciprocalRankScore(3)).isCloseTo(0.25, within(1e-9));
        // Defensive: negative rank treated as 0.
        assertThat(TurLogprobConfidence.reciprocalRankScore(-5)).isEqualTo(1.0);
    }

    @Test
    void blendIsConvexAndClampsWeight() {
        // weight 0.5 → simple average.
        assertThat(TurLogprobConfidence.blend(1.0, 0.0, 0.5)).isCloseTo(0.5, within(1e-9));
        // weight 1 → retrieval only; weight 0 → confidence only.
        assertThat(TurLogprobConfidence.blend(0.3, 0.9, 1.0)).isCloseTo(0.3, within(1e-9));
        assertThat(TurLogprobConfidence.blend(0.3, 0.9, 0.0)).isCloseTo(0.9, within(1e-9));
        // out-of-range weight clamps to [0,1].
        assertThat(TurLogprobConfidence.blend(0.2, 0.8, 5.0)).isCloseTo(0.2, within(1e-9));
    }

    @Test
    void confidenceCanPromoteALowerRankedConfidentCandidate() {
        // Rank-0 hit the model hedged on (P=0.5) vs a rank-1 hit it is sure about (P=0.95).
        double top = TurLogprobConfidence.blend(
                TurLogprobConfidence.reciprocalRankScore(0), 0.5, 0.5);   // 0.5*1.0 + 0.5*0.5 = 0.75
        double second = TurLogprobConfidence.blend(
                TurLogprobConfidence.reciprocalRankScore(1), 0.95, 0.5);  // 0.5*0.5 + 0.5*0.95 = 0.725
        // Still close, retrieval prior keeps #1 on top here...
        assertThat(top).isGreaterThan(second);
        // ...but a strongly-hedged #1 (P=0.1) loses to the confident #2.
        double hedgedTop = TurLogprobConfidence.blend(
                TurLogprobConfidence.reciprocalRankScore(0), 0.1, 0.5);   // 0.55
        assertThat(hedgedTop).isLessThan(second);
    }
}

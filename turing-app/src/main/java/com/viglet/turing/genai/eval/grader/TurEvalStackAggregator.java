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

import java.util.List;

/**
 * T600 / §XXXIII.15 — aggregates a grader stack's per-grader results into a
 * case score + pass, honouring each grader's weight / pass-threshold / blocking
 * policy. Replaces the historical hardcoded mean + all-must-pass:
 *
 * <ul>
 *   <li><b>score</b> = weighted mean of grader scores (weight ≤ 0 treated as 1);</li>
 *   <li><b>a grader passes</b> when its result passed AND its score ≥ its threshold;</li>
 *   <li><b>the case passes</b> when every <em>blocking</em> grader passed
 *       (non-blocking graders count toward the score but never fail the case).</li>
 * </ul>
 *
 * <p>With the default stack (weight 1, threshold 0, blocking on for every
 * grader) this is exactly the legacy mean + all-must-pass — byte-identical.
 * Pure → unit-testable.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurEvalStackAggregator {

    private TurEvalStackAggregator() {
    }

    /** One grader's contribution to the aggregate. */
    public record Contribution(double score, boolean passed, double weight, double threshold,
            boolean blocking) {
    }

    /** The aggregated case outcome. */
    public record Aggregate(double score, boolean passed) {
    }

    /** Aggregates contributions; an empty stack scores 1.0 and passes (nothing asserted). */
    public static Aggregate aggregate(List<Contribution> contributions) {
        if (contributions.isEmpty()) {
            return new Aggregate(1d, true);
        }
        double weightedSum = 0d;
        double weightTotal = 0d;
        boolean allBlockingPassed = true;
        for (Contribution c : contributions) {
            double weight = c.weight() > 0d ? c.weight() : 1d;
            weightedSum += weight * c.score();
            weightTotal += weight;
            boolean graderPassed = c.passed() && c.score() >= c.threshold();
            if (c.blocking()) {
                allBlockingPassed = allBlockingPassed && graderPassed;
            }
        }
        double score = weightTotal == 0d ? 1d : weightedSum / weightTotal;
        return new Aggregate(score, allBlockingPassed);
    }
}

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
 * T586 / §XXXIII.1 — the verdict a {@link TurEvalGrader} produces for one case.
 *
 * @param score     the grader's score in {@code [0,1]}
 * @param passed    whether the grader considers the case a pass
 * @param verdict   a short machine label ({@code "pass"} / {@code "fail"} /
 *                  {@code "na"} / grader-specific)
 * @param rationale a human-readable explanation (nullable)
 * @param deferred  {@code true} when the grader cannot decide now and defers to
 *                  a later step (HUMAN review, T592); the aggregate ignores a
 *                  deferred result's score/passed
 * @param details   optional structured per-field breakdown (e.g. slot diffs)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurEvalGraderResult(
        double score,
        boolean passed,
        String verdict,
        String rationale,
        boolean deferred,
        List<Detail> details) {

    /**
     * One structured expected-vs-actual comparison a grader may surface (slot
     * equality, JSON-path match, ...).
     *
     * @param key      the compared field / key
     * @param expected the expected value
     * @param actual   the observed value (nullable)
     * @param match    whether {@code actual} satisfied {@code expected}
     */
    public record Detail(String key, String expected, String actual, boolean match) {
    }

    /** A pass/fail verdict with the canonical 1.0 / 0.0 score and label. */
    public static TurEvalGraderResult binary(boolean pass) {
        return new TurEvalGraderResult(pass ? 1d : 0d, pass, pass ? "pass" : "fail", null, false, List.of());
    }

    /** A fractional score with an explicit pass flag, verdict and rationale. */
    public static TurEvalGraderResult scored(double score, boolean passed, String verdict, String rationale) {
        return new TurEvalGraderResult(score, passed, verdict, rationale, false, List.of());
    }

    /** A scored result carrying a structured {@link Detail} breakdown. */
    public static TurEvalGraderResult withDetails(double score, boolean passed, List<Detail> details) {
        return new TurEvalGraderResult(score, passed, passed ? "pass" : "fail", null, false, details);
    }

    /** A deferred verdict — the case is parked for HUMAN review (T592). */
    public static TurEvalGraderResult deferred(String rationale) {
        return new TurEvalGraderResult(0d, false, "pending_review", rationale, true, List.of());
    }
}

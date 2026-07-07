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

/**
 * T590 / §XXXIII.5 — the MODEL-grader judging strategies, generalizing the lone
 * rubric judge into a family.
 *
 * <ul>
 *   <li>{@link #LABEL} — binary pass/fail against an instruction.</li>
 *   <li>{@link #SCORE} — a 0..1 quality score; passes at/above a threshold.</li>
 *   <li>{@link #CRITERIA} — a multi-criterion checklist scored as a whole.</li>
 *   <li>{@link #PAIRWISE} — the answer judged against a reference answer.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurEvalModelStrategy {
    LABEL,
    SCORE,
    CRITERIA,
    PAIRWISE;

    /** Parse a config string to a strategy, defaulting to {@link #SCORE}. */
    public static TurEvalModelStrategy from(String value) {
        if (value == null || value.isBlank()) {
            return SCORE;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SCORE;
        }
    }
}

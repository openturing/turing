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
 * T586 / §XXXIII.1 — the grader SPI. A grader scores one replayed eval case
 * along a single dimension; the eval runner resolves an ordered list of them
 * (the "grader stack") and aggregates their results.
 *
 * <p>Implementations are Spring beans discovered by {@link
 * TurEvalGraderRegistry}. Built-in graders wrap the historical four scoring
 * dimensions (slot / outcome / node / rubric); custom graders (T589) and model
 * strategies (T590) plug in via the same contract.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurEvalGrader {

    /** Stable id used to reference this grader in config and the registry. */
    String graderId();

    /** The kind bucket (MODEL / CODE / HUMAN) this grader belongs to. */
    TurEvalGraderKind kind();

    /** A human label for reports/UX; defaults to {@link #graderId()}. */
    default String displayName() {
        return graderId();
    }

    /**
     * Whether this grader has anything to assert for the given case. Built-in
     * graders return {@code false} when the case declares no matching
     * expectation (e.g. the slot grader when {@code expectedSlotsJson} is
     * empty), so an unasserted dimension never contributes to the score — the
     * behaviour the legacy runner had inline.
     */
    boolean appliesTo(TurEvalGradingContext ctx);

    /** Scores the case. Called only when {@link #appliesTo(TurEvalGradingContext)}. */
    TurEvalGraderResult grade(TurEvalGradingContext ctx);
}

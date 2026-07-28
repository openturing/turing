/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.match.dto;

import java.time.Instant;
import java.util.List;

/**
 * How a project's fit <em>moved</em> between its last two runs (Block AT /
 * §XLIII, T704) — the answer to "this content dropped below fit for persona X
 * since last run". Entries are ordered by the sharpest regression first, so the
 * studio can lead with what needs attention.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPersonaMatchDriftDto(
        String projectId,
        /** Distinct runs on record; drift needs at least two. */
        int runCount,
        Instant previousRunAt,
        Instant currentRunAt,
        /** Project-average fit at the previous / current run. */
        double previousAvg,
        double currentAvg,
        /**
         * T826 — the effective good-fit bar this report was computed against (the
         * project's own, or the deployment default when it names none), echoed so
         * the studio's bands, the T827 alert and the T828 gate all cite the same
         * number instead of each assuming 75.
         */
        double fitThreshold,
        int regressedCount,
        int improvedCount,
        List<Entry> entries) {

    /**
     * One {@code (content, persona)} pair's movement.
     *
     * @param crossedBelowFit the pair was at/above the project's "good fit"
     *                        threshold and is now below it — the alert case
     * @param crossedAboveFit the pair recovered across that same threshold
     */
    public record Entry(String sourceId, String personaId, double previousFit,
            double currentFit, double delta, boolean crossedBelowFit,
            boolean crossedAboveFit, boolean contentChanged) {
    }
}

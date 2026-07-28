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
 * A project's fit history over time (Block AT / §XLIII, T704): one series per
 * matrix cell for the drill-in sparkline, plus the run-level average series for
 * the project trend chart. Points are ordered oldest first so a chart can render
 * them straight off the wire.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPersonaMatchTrendDto(
        String projectId,
        /** Number of distinct runs the series covers. */
        int runCount,
        /** Project-wide average fit per run, oldest first. */
        List<Point> average,
        /** Per-cell series, oldest first within each cell. */
        List<CellSeries> cells) {

    /** One measurement. {@code reused} marks a run that skipped re-evaluation. */
    public record Point(Instant evaluatedAt, double fitScore, double readabilityScore,
            boolean llmUsed, boolean reused) {
    }

    public record CellSeries(String sourceId, String personaId, List<Point> points) {
    }
}

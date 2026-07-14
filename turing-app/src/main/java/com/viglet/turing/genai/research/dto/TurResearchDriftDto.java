/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.research.dto;

import java.util.List;

/**
 * A study's insight-drift report (Block AW / §XLVI.4, T729 — Continuous Insight):
 * the ordered series of per-run sufficiency snapshots that turns one-shot research
 * into continuous validation. {@code schedule} echoes the study's cadence so the
 * studio can render the schedule control alongside the trend chart.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchDriftDto(
        String schedule,
        int snapshotCount,
        List<TurResearchDriftPointDto> points) {

    public static TurResearchDriftDto empty(String schedule) {
        return new TurResearchDriftDto(schedule, 0, List.of());
    }
}

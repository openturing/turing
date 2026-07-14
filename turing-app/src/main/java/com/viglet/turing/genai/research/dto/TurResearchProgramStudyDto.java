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

import java.time.Instant;

/**
 * One study's row in a multi-study program rollup (Block AW / §XLVI.5, T733 —
 * PRISMA). A deterministic per-study summary — participants, interviews, whether
 * a synthesized report exists, its theme count, and the T723 saturation verdict —
 * so the program board shows each study's contribution and sufficiency at a glance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchProgramStudyDto(
        String studyId,
        String name,
        String protocol,
        int participants,
        int interviews,
        boolean reportAvailable,
        int themeCount,
        boolean saturated,
        int adequateAtN,
        Instant lastRunAt) {
}

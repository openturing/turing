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
 * One theme aggregated across a program's studies (Block AW / §XLVI.5, T733 —
 * PRISMA). Themes with the same (normalized) title are folded together;
 * {@code studyCount} is how many distinct studies raised it (a theme in ≥2 studies
 * is a <em>cross-study</em> signal — the whole point of the rollup) and
 * {@code totalPrevalence} sums each study's per-theme prevalence. Purely derived
 * from the already-synthesized (cached) reports — no new inference.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchProgramThemeDto(
        String title,
        int studyCount,
        int totalPrevalence,
        List<String> studyNames) {
}

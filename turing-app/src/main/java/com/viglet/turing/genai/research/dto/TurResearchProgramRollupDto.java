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
 * A program-level rollup across several research studies (Block AW / §XLVI.5,
 * T733 — the multi-study planner / PRISMA view). Rolls the deliberately per-study
 * insights up into a program view: totals, each study's sufficiency, and themes
 * aggregated across studies (surfacing which pain points recur across audiences).
 * Deterministic — it reuses each study's already-synthesized (cached) report + the
 * T723 saturation counts, adding no new inference (the T724 discipline).
 *
 * <p>Fail-open: {@code available} is {@code false} (with {@code message}) when no
 * study was resolvable; a single unavailable study is simply skipped, not fatal.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchProgramRollupDto(
        boolean available,
        String message,
        int studyCount,
        int totalParticipants,
        int totalInterviews,
        int totalDistinctThemes,
        int sharedThemeCount,
        List<TurResearchProgramStudyDto> studies,
        List<TurResearchProgramThemeDto> themes) {

    public static TurResearchProgramRollupDto unavailable(String message) {
        return new TurResearchProgramRollupDto(false, message, 0, 0, 0, 0, 0,
                List.of(), List.of());
    }
}

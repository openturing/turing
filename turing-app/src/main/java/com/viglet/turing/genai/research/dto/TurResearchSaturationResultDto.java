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
 * The deterministic saturation report for a study (Block AW / §XLVI.3, T723) —
 * the honest, LLM-free antidote to fabricated-confidence synthetic research. It
 * makes the <em>sufficiency</em> claim measurable: as personas are interviewed in
 * roster order, do marginal participants keep raising new themes, or has the
 * cohort stopped teaching us anything?
 *
 * <p>{@code adequateAtN} is the smallest N such that every persona beyond the Nth
 * added no meaningful novelty (a trailing dry streak of at least
 * {@code minDryStreak} personas) — i.e. "the sample was adequate at N." It is
 * {@code -1} when the cohort never saturated (the last personas were still
 * contributing new themes, so N was too small).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchSaturationResultDto(
        boolean available,
        String message,
        int personaCount,
        int totalUniqueThemes,
        boolean saturated,
        int adequateAtN,
        double noveltyThreshold,
        int minDryStreak,
        List<TurResearchSaturationStepDto> steps) {

    /** An unavailable result (fewer than two completed interviews to compare). */
    public static TurResearchSaturationResultDto unavailable(String message, int personaCount) {
        return new TurResearchSaturationResultDto(false, message, personaCount, 0, false, -1,
                0.0, 0, List.of());
    }
}

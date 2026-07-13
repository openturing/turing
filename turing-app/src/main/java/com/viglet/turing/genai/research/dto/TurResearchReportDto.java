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
 * The synthesized insights report for a study (Block AW / §XLVI.3, T722): an
 * executive summary, ranked {@link TurResearchThemeDto themes} each carrying
 * verbatim participant quotes, recommendations, and the same material re-projected
 * through the {@link TurResearchPersonaLensDto by-persona} lens.
 *
 * <p>Fail-open like the sibling {@code TurLlmSummaryService.SummaryResult}:
 * {@code available} is {@code false} (with {@code error}) when there is no default
 * LLM or no completed interviews to synthesize; {@code canRegenerate} mirrors the
 * global cache-regenerate switch. {@code rawContent} is the model's unparsed
 * output, retained for the studio to fall back to (and for debugging) when the
 * structured parse could not be trusted.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchReportDto(
        boolean available,
        String error,
        boolean canRegenerate,
        String executiveSummary,
        List<TurResearchThemeDto> themes,
        List<String> recommendations,
        List<TurResearchPersonaLensDto> byPersona,
        String rawContent) {

    /** An unavailable report (no LLM, or nothing to synthesize yet). */
    public static TurResearchReportDto unavailable(String error, boolean canRegenerate) {
        return new TurResearchReportDto(false, error, canRegenerate, null,
                List.of(), List.of(), List.of(), null);
    }
}

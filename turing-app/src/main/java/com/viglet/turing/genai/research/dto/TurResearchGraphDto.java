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
 * A study's theme/affinity knowledge graph (Block AW / §XLVI.3, T724) — a
 * concept↔problem↔persona graph derived <em>deterministically</em> from the T722
 * insights report (which cohorts cluster on which pain point). Visualization only:
 * it adds no new inference over the already-synthesized themes, so it never calls
 * the LLM itself — it reuses the cached report.
 *
 * <p>Fail-open like the sibling report / saturation surfaces: {@code available} is
 * {@code false} (with {@code message}) when there is no report to graph yet.
 * Headless — the interactive graph view ships in the {@code /bento/persona/research}
 * studio (T730).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchGraphDto(
        boolean available,
        String message,
        List<TurResearchGraphNodeDto> nodes,
        List<TurResearchGraphEdgeDto> edges) {

    /** An unavailable graph (no synthesized themes to project yet). */
    public static TurResearchGraphDto unavailable(String message) {
        return new TurResearchGraphDto(false, message, List.of(), List.of());
    }
}

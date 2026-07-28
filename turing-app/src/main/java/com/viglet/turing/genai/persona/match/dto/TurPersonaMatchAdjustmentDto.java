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
 * Suggested adjustments that close the bidirectional tuning loop (Block AT /
 * §XLIII, T704): concrete edits to make either to a <em>content</em> (so it
 * serves a persona better) or to a <em>persona</em> (so it describes the audience
 * the content actually reaches). Advisory only — nothing here is ever applied
 * automatically; an operator reads the actions and decides.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPersonaMatchAdjustmentDto(
        String projectId,
        /** Whether the grounded LLM produced these, or the deterministic fallback did. */
        boolean llmUsed,
        /** Why the LLM was not used, when it was not. */
        String error,
        Instant generatedAt,
        List<Item> items) {

    /** Which side of the loop an item adjusts. */
    public enum Target {
        CONTENT, PERSONA
    }

    /**
     * One adjustment.
     *
     * @param avgFit  the target's current average fit across the matrix
     * @param delta   how that average moved since the previous run ({@code null}
     *                when there is no previous run to compare against)
     * @param actions the concrete edits to consider, most important first
     */
    public record Item(Target target, String targetId, String targetName, double avgFit,
            Double delta, String rationale, List<String> actions) {
    }
}

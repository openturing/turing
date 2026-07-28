/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.fit;

import java.util.List;

import com.viglet.turing.genai.persona.readability.TurReadabilityMetrics;
import com.viglet.turing.system.TurVerdictSource;

/**
 * The content-fit verdict for one source against an audience persona
 * (Block AA / §XXVI.4). {@code fitScore} (0–100) <strong>fuses</strong> the
 * deterministic readability sub-score (hard signal) with the LLM's grounded
 * judgment; {@code fits}/{@code misfits} are the qualitative "condiz / não
 * condiz". When the LLM is unavailable or its output can't be parsed,
 * {@code llmUsed} is false and the score falls back to readability only.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurContentFitResult(
        double fitScore,
        String summary,
        List<String> fits,
        List<TurContentFitMisfit> misfits,
        double readabilityScore,
        TurReadabilityMetrics metrics,
        boolean llmUsed,
        String error,
        boolean canRegenerate,
        String sourceId,
        String sourceName,
        /**
         * T829 — how this verdict was obtained, so the UI can distinguish "no LLM is
         * configured" ({@link TurVerdictSource#UNAVAILABLE}) from "the model answered
         * and we could not read it" ({@link TurVerdictSource#UNREADABLE}). Both used
         * to render as a bare "heuristic only".
         */
        TurVerdictSource verdictSource) {

    /**
     * Pre-T829 form, kept so the existing callers and fixtures compile unchanged:
     * the verdict source is inferred from {@code llmUsed} (an LLM-backed result went
     * through the legacy prompt-JSON path; anything else had no verdict at all).
     */
    public TurContentFitResult(double fitScore, String summary, List<String> fits,
            List<TurContentFitMisfit> misfits, double readabilityScore,
            TurReadabilityMetrics metrics, boolean llmUsed, String error,
            boolean canRegenerate, String sourceId, String sourceName) {
        this(fitScore, summary, fits, misfits, readabilityScore, metrics, llmUsed, error,
                canRegenerate, sourceId, sourceName,
                llmUsed ? TurVerdictSource.PROMPT_JSON : TurVerdictSource.UNAVAILABLE);
    }
}

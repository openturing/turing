/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.system.lane;

import java.util.Locale;

/**
 * T517 / §XXVIII.13 — a cross-provider "model lane": a coarse class of LLM work
 * the admin can bind to a specific {@link com.viglet.turing.persistence.model.llm.TurLLMInstance},
 * independent of which vendor answers the user's chat turn. The multi-provider
 * seam (Block AD) is what makes this pay off — point each lane at the vendor that
 * fits it.
 *
 * <ul>
 *   <li>{@link #FAST} — sub-100ms interactive micro-calls (autocomplete, spell,
 *       query-rewrite). A Groq/Cerebras-style endpoint shines here.</li>
 *   <li>{@link #REASONING} — quality-sensitive analysis (router, rerank, judge):
 *       a DeepSeek-R1 / o-series reasoning model.</li>
 *   <li>{@link #CHEAP} — non-interactive background work (nightly summarization,
 *       insights): the cheapest capable model.</li>
 * </ul>
 *
 * <p>A lane with no instance bound falls back to the platform default LLM, so
 * leaving every lane blank reproduces the legacy single-model behaviour exactly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurModelLane {
    FAST,
    REASONING,
    CHEAP;

    /** Lenient parse; unknown/blank/null falls back to {@link #REASONING}. */
    public static TurModelLane fromValue(String value) {
        if (value == null || value.isBlank()) {
            return REASONING;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return REASONING;
        }
    }
}

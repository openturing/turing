/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.sn.genai;

import java.util.Locale;

/**
 * T818 / §LIX.1 (Block BK) — the selectable <strong>query-planning strategy</strong>
 * the catalog copilot uses to turn a natural-language question into a structured
 * {@code TurDslQueryRequest}.
 *
 * <ul>
 *   <li>{@link #DETERMINISTIC} — the historical behaviour (default): the T811
 *       deterministic ranking planner resolves any superlative / "sorted by"
 *       intent, the ranking clause is stripped, and the remainder goes through a
 *       single LLM facet parse. Instant, cheap and CI-testable, but the ranking
 *       lexicon is English-only.</li>
 *   <li>{@link #LLM_ASSISTED} — T819. A multi-pass LLM plan:
 *       <em>parse → judge → refine</em>, bounded by the configured analysis depth.
 *       Understands languages natively (no per-language lexicon) and repairs the
 *       exact failure a weak model shows (empty query, dropped facet, missing
 *       sort), at the cost of N× LLM calls per turn.</li>
 *   <li>{@link #HYBRID} — T820. Runs the cheap {@link #DETERMINISTIC} fast-path
 *       first and escalates to the {@link #LLM_ASSISTED} judge/refine passes
 *       <em>only</em> when retrieval comes back empty or the plan is degenerate.
 *       Common questions stay instant and free; the LLM budget is spent only to
 *       rescue a failure.</li>
 * </ul>
 *
 * <p>Selected per SN site ({@code TurSNSiteGenAi.copilotPlanningStrategy}); a
 * {@code null} site value falls back to the global
 * {@code turing.genai.copilot.planning.strategy} property, which itself defaults
 * to {@link #DETERMINISTIC} — so the whole block is additive and no existing
 * site needs revalidation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurCopilotPlanningStrategy {
    DETERMINISTIC,
    LLM_ASSISTED,
    HYBRID;

    /** Whether this strategy may spend LLM judge/refine passes (T819/T820). */
    public boolean usesLlmPasses() {
        return this == LLM_ASSISTED || this == HYBRID;
    }

    /**
     * Lenient parse used wherever a stored/config string is turned into a
     * strategy. Unknown, blank or {@code null} values fall back to
     * {@link #DETERMINISTIC}, so a corrupt config row can never silently start
     * spending LLM calls nor throw — it just behaves like today's path.
     */
    public static TurCopilotPlanningStrategy fromValue(String value) {
        if (value == null || value.isBlank()) {
            return DETERMINISTIC;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DETERMINISTIC;
        }
    }
}

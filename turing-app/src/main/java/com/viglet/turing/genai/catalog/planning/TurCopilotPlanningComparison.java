/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.catalog.planning;

import java.util.List;

import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalReport.CaseResult;

/**
 * T821 / §LIX.4 (Block BK) — the side-by-side result of running one NL→facet eval
 * pack through <em>every</em> planning strategy, so the three axes an operator has
 * to trade off (answer quality, latency, LLM calls per turn) are measurable
 * <strong>per catalog</strong> before switching a site's
 * {@link TurCopilotPlanningStrategy}.
 *
 * <p>The report is deliberately blunt about what it cannot measure. It is a
 * <strong>plan-only</strong> comparison: each strategy's planned query is scored
 * against the pack's golden clauses with the T385 scorer, never executed against a
 * live index. {@link TurCopilotPlanningStrategy#HYBRID} only escalates on live
 * retrieval (zero hits, or a degenerate plan on a specific question), which a
 * plan-only run cannot produce — so its numbers here are
 * {@link TurCopilotPlanningStrategy#DETERMINISTIC}'s by construction and
 * <em>understate</em> production behaviour. {@link #caveats()} says exactly that, and
 * every consumer is expected to show it next to the numbers rather than let the table
 * imply parity.
 *
 * @param packName    the eval pack that was run
 * @param caseCount   how many cases each strategy was scored over
 * @param maxPasses   the analysis depth the LLM strategies ran at
 * @param strategies  one outcome per compared strategy, in enum order
 * @param caveats     what the numbers do <b>not</b> prove (never empty on a real run)
 * @param error       non-null when the comparison could not run at all
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurCopilotPlanningComparison(
        String packName,
        int caseCount,
        int maxPasses,
        List<StrategyOutcome> strategies,
        List<String> caveats,
        String error) {

    public TurCopilotPlanningComparison {
        strategies = strategies == null ? List.of() : List.copyOf(strategies);
        caveats = caveats == null ? List.of() : List.copyOf(caveats);
    }

    /** A comparison that could not run (no pack, no schema, no usable LLM). */
    public static TurCopilotPlanningComparison error(String packName, String error) {
        return new TurCopilotPlanningComparison(packName, 0, 0, List.of(), List.of(), error);
    }

    /**
     * How one strategy scored on the pack.
     *
     * @param strategy      the strategy that produced the plans
     * @param passed        every case matched all its golden clauses and stayed grounded
     * @param passedCount   how many cases passed
     * @param score         mean per-case score (0..1) — the <b>quality</b> axis
     * @param llmPasses     total LLM calls across every case — the <b>cost</b> axis
     * @param elapsedMillis wall-clock to plan every case — the <b>latency</b> axis
     * @param note          how to read this row (depth used, plan-only equivalence…)
     * @param results       the per-case breakdown, same shape the NL→facet report uses
     */
    public record StrategyOutcome(
            TurCopilotPlanningStrategy strategy,
            boolean passed,
            int passedCount,
            double score,
            int llmPasses,
            long elapsedMillis,
            String note,
            List<CaseResult> results) {

        public StrategyOutcome {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }
}

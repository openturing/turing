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
import java.util.Optional;

import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;
import com.viglet.turing.sn.dsl.eval.TurNLFacetField;

/**
 * T818 / §LIX.1 (Block BK) — the copilot's <strong>query-planning seam</strong>:
 * the single step that turns a natural-language catalog question into an
 * executable structured query, so the strategy behind it becomes a choosable
 * per-site option instead of hardcoded logic inside
 * {@code TurCatalogCopilotService.retrieve()}.
 *
 * <p>Three implementations back the three
 * {@link TurCopilotPlanningStrategy} values:
 * {@link TurDeterministicCopilotQueryPlanner} (today's behaviour),
 * {@link TurLlmAssistedCopilotQueryPlanner} (T819 multi-pass parse → judge →
 * refine) and {@link TurHybridCopilotQueryPlanner} (T820 deterministic
 * fast-path + LLM escalation).
 *
 * <p>Planning is a two-phase contract so a strategy can react to what retrieval
 * actually returned:
 * <ol>
 *   <li>{@link #plan(PlanRequest)} — always called; produces the query to execute.</li>
 *   <li>{@link #replan(PlanRequest, TurCopilotQueryPlan, long)} — called once
 *       <em>after</em> execution with the hit count, so a strategy may escalate
 *       and hand back a repaired query (the T820 HYBRID path). The default
 *       implementation never escalates.</li>
 * </ol>
 *
 * <p>Every implementation must be <strong>fail-open</strong>: it never throws, and
 * the worst case is a plain full-text query — never an error surfaced to the user.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurCopilotQueryPlanner {

    /**
     * The bounded top-k page size a plan guarantees, so a sort-only
     * {@code match_all} query returns a ranked page rather than an unbounded scan.
     * Shared with {@code TurCatalogCopilotService}'s grounding cap so the LLM sees
     * exactly the page that was retrieved.
     */
    int DEFAULT_TOP_K = 8;

    /** Which {@link TurCopilotPlanningStrategy} this planner implements. */
    TurCopilotPlanningStrategy strategy();

    /**
     * Plan the query to execute for {@code request}. Never throws — degrades to a
     * free-text query when nothing better can be produced.
     */
    TurCopilotQueryPlan plan(PlanRequest request);

    /**
     * Second-chance hook invoked once after the plan was executed, carrying the
     * total hit count retrieval produced. Return a repaired plan to have the
     * caller re-execute, or {@link Optional#empty()} (the default) to keep the
     * first result. Never throws.
     *
     * @param request  the original planning request
     * @param previous the plan that was just executed
     * @param hits     the total hit count that plan produced ({@code 0} when empty)
     */
    default Optional<TurCopilotQueryPlan> replan(PlanRequest request, TurCopilotQueryPlan previous,
            long hits) {
        return Optional.empty();
    }

    /**
     * One planning request.
     *
     * @param siteName  the target SN site / index
     * @param locale    resolved locale code (never blank — the caller defaults it)
     * @param query     the user's natural-language question
     * @param schema    the site's declared fields the plan may reference
     * @param maxPasses resolved analysis depth for LLM strategies ({@code 0} =
     *                  parse only, {@code 1} = + judge, {@code 2} = + refine)
     */
    record PlanRequest(String siteName, String locale, String query, List<TurNLFacetField> schema,
            int maxPasses) {
    }
}

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

import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;

/**
 * T818 / §LIX.1 (Block BK) — the outcome of one
 * {@link TurCopilotQueryPlanner#plan(TurCopilotQueryPlanner.PlanRequest)}: the
 * structured query to execute plus the observability trail (which strategy
 * produced it, how many LLM passes it cost, and a short human note).
 *
 * <p>The note and pass count exist so a "no results" answer stays traceable to the
 * plan that produced it — the same reason T823 made the retrieval mode loggable.
 *
 * @param request    the query body to execute (never {@code null})
 * @param strategy   the strategy that produced it
 * @param llmPasses  how many LLM calls the plan cost (parse + judge + refine)
 * @param notes      short human-readable trail, e.g. {@code "parse+judge+refine"}
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurCopilotQueryPlan(TurDslQueryRequest request, TurCopilotPlanningStrategy strategy,
        int llmPasses, String notes) {

    /** A plan carrying no LLM cost (the deterministic overlay / a canned body). */
    public static TurCopilotQueryPlan of(TurDslQueryRequest request,
            TurCopilotPlanningStrategy strategy, String notes) {
        return new TurCopilotQueryPlan(request, strategy, 0, notes);
    }

    /** The same plan re-labelled under {@code other} (used by the HYBRID wrapper). */
    public TurCopilotQueryPlan as(TurCopilotPlanningStrategy other, String extraNotes) {
        return new TurCopilotQueryPlan(request, other, llmPasses,
                extraNotes == null || extraNotes.isBlank() ? notes : notes + " → " + extraNotes);
    }
}

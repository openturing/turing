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

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * T820 / §LIX.3 (Block BK) — the {@link TurCopilotPlanningStrategy#HYBRID} planner and
 * the recommended target default: run the cheap deterministic fast-path first, and
 * spend LLM judge/refine passes <strong>only to rescue a failure</strong>.
 *
 * <p>Escalation fires when either signal says the plan didn't work:
 * <ul>
 *   <li><b>empty</b> — retrieval returned zero hits. A structured question that
 *       matches nothing usually means a bad filter, not an empty catalog.</li>
 *   <li><b>degenerate</b> — the executed query carried no filter and no sort (a bare
 *       {@code match_all}) while the question was specific. That is "the parse came
 *       back empty": it looks like a success — the catalog is returned in arbitrary
 *       order — while answering nothing.</li>
 * </ul>
 * A broad question ("what do you have?") legitimately plans to {@code match_all} and is
 * <em>not</em> escalated, so the common case still costs one parse.
 *
 * <p>The escalation itself is the T819 multi-pass planner, reused verbatim but labelled
 * {@code HYBRID}. It is fail-open: when the escalated plan is itself degenerate (or
 * identical to what already ran), nothing is returned and the first result stands.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurHybridCopilotQueryPlanner implements TurCopilotQueryPlanner {

    private final TurDeterministicCopilotQueryPlanner deterministic;
    private final TurLlmAssistedCopilotQueryPlanner llmAssisted;
    private final TurCopilotPlanValidator validator;

    public TurHybridCopilotQueryPlanner(TurDeterministicCopilotQueryPlanner deterministic,
            TurLlmAssistedCopilotQueryPlanner llmAssisted, TurCopilotPlanValidator validator) {
        this.deterministic = deterministic;
        this.llmAssisted = llmAssisted;
        this.validator = validator;
    }

    @Override
    public TurCopilotPlanningStrategy strategy() {
        return TurCopilotPlanningStrategy.HYBRID;
    }

    @Override
    public TurCopilotQueryPlan plan(PlanRequest request) {
        return deterministic.plan(request).as(strategy(), "fast-path");
    }

    @Override
    public Optional<TurCopilotQueryPlan> replan(PlanRequest request, TurCopilotQueryPlan previous,
            long hits) {
        String reason = escalationReason(request, previous, hits);
        if (reason == null) {
            return Optional.empty();
        }
        TurCopilotQueryPlan escalated = llmAssisted.plan(request, strategy());
        if (escalated == null || escalated.llmPasses() <= 0
                || validator.isDegenerate(escalated.request())) {
            log.info("[CopilotPlanner] site '{}' HYBRID escalation ({}) produced nothing better — "
                    + "keeping the deterministic result", request.siteName(), reason);
            return Optional.empty();
        }
        log.info("[CopilotPlanner] site '{}' HYBRID escalated to the LLM planner ({}): {} pass(es), {}",
                request.siteName(), reason, escalated.llmPasses(), escalated.notes());
        return Optional.of(escalated.as(strategy(), "escalated (" + reason + ")"));
    }

    /**
     * Why the deterministic plan should be escalated, or {@code null} to keep it.
     * Kept as a human-readable reason rather than a boolean so the escalation shows up
     * in the log with its cause.
     */
    private String escalationReason(PlanRequest request, TurCopilotQueryPlan previous, long hits) {
        if (previous == null) {
            return null;
        }
        if (hits <= 0) {
            return "zero hits";
        }
        if (validator.isDegenerate(previous.request()) && validator.isSpecific(request.query())) {
            return "degenerate plan on a specific question";
        }
        return null;
    }
}

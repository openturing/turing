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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.genai.catalog.planning.TurCopilotQueryPlanner.PlanRequest;
import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetField;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T820 / §LIX.3 (Block BK) — the HYBRID escalation gate: cheap by default, LLM passes
 * spent only to rescue an empty or degenerate result, and fail-open when the escalation
 * itself yields nothing better.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurHybridCopilotQueryPlannerTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final List<TurNLFacetField> SCHEMA = List.of(
            new TurNLFacetField("kind", TurSEFieldType.STRING, true, "Model kind"));

    @Mock
    private TurDeterministicCopilotQueryPlanner deterministic;
    @Mock
    private TurLlmAssistedCopilotQueryPlanner llmAssisted;

    private final TurCopilotPlanValidator validator = new TurCopilotPlanValidator();

    private TurHybridCopilotQueryPlanner planner() {
        return new TurHybridCopilotQueryPlanner(deterministic, llmAssisted, validator);
    }

    @Test
    void reportsItsStrategy() {
        assertThat(planner().strategy()).isEqualTo(TurCopilotPlanningStrategy.HYBRID);
    }

    @Test
    void runsTheDeterministicFastPathFirstLabelledAsHybrid() {
        when(deterministic.plan(any())).thenReturn(deterministicPlan(kindChat()));

        TurCopilotQueryPlan plan = planner().plan(request("chat models"));

        assertThat(plan.strategy()).isEqualTo(TurCopilotPlanningStrategy.HYBRID);
        assertThat(plan.notes()).contains("fast-path");
        assertThat(plan.request().query()).isEqualTo(kindChat().query());
    }

    @Test
    void doesNotEscalateWhenTheFastPathFoundHits() {
        TurCopilotQueryPlan previous = deterministicPlan(kindChat());

        assertThat(planner().replan(request("chat models"), previous, 12L)).isEmpty();
        verify(llmAssisted, never()).plan(any(), any());
    }

    @Test
    void escalatesOnZeroHits() {
        TurCopilotQueryPlan previous = deterministicPlan(kindChat());
        when(llmAssisted.plan(any(), eq(TurCopilotPlanningStrategy.HYBRID)))
                .thenReturn(new TurCopilotQueryPlan(kindChat(), TurCopilotPlanningStrategy.HYBRID,
                        3, "parse+judge+refine"));

        TurCopilotQueryPlan escalated =
                planner().replan(request("open licence chat models"), previous, 0L).orElseThrow();

        assertThat(escalated.strategy()).isEqualTo(TurCopilotPlanningStrategy.HYBRID);
        assertThat(escalated.notes()).contains("escalated").contains("zero hits");
    }

    @Test
    void escalatesOnADegeneratePlanForASpecificQuestion() {
        // Hits came back, but the plan carried no filter and no sort: the catalog in
        // arbitrary order, which looks like a success and answers nothing.
        TurCopilotQueryPlan previous = deterministicPlan(body("{\"query\":{\"match_all\":{}}}"));
        when(llmAssisted.plan(any(), eq(TurCopilotPlanningStrategy.HYBRID)))
                .thenReturn(new TurCopilotQueryPlan(kindChat(), TurCopilotPlanningStrategy.HYBRID,
                        2, "parse+judge"));

        TurCopilotQueryPlan escalated =
                planner().replan(request("cheapest open weight chat model"), previous, 800L)
                        .orElseThrow();

        assertThat(escalated.notes()).contains("degenerate plan");
    }

    @Test
    void doesNotEscalateADegeneratePlanForABroadQuestion() {
        // "what do you have?" legitimately plans to match_all — no LLM budget spent.
        TurCopilotQueryPlan previous = deterministicPlan(body("{\"query\":{\"match_all\":{}}}"));

        assertThat(planner().replan(request("what do you have?"), previous, 800L)).isEmpty();
        verify(llmAssisted, never()).plan(any(), any());
    }

    @Test
    void keepsTheFastPathWhenTheEscalationIsItselfDegenerate() {
        TurCopilotQueryPlan previous = deterministicPlan(kindChat());
        when(llmAssisted.plan(any(), any())).thenReturn(new TurCopilotQueryPlan(
                body("{\"query\":{\"match_all\":{}}}"), TurCopilotPlanningStrategy.HYBRID, 2,
                "parse+judge"));

        assertThat(planner().replan(request("open licence chat models"), previous, 0L)).isEmpty();
    }

    @Test
    void keepsTheFastPathWhenTheEscalationSpentNoLlmPass() {
        // No LLM pass ran (e.g. no default LLM configured), so the escalated plan is
        // just the free-text fallback — re-executing it would waste a round trip.
        TurCopilotQueryPlan previous = deterministicPlan(kindChat());
        when(llmAssisted.plan(any(), any())).thenReturn(new TurCopilotQueryPlan(kindChat(),
                TurCopilotPlanningStrategy.HYBRID, 0, "parse-only"));

        assertThat(planner().replan(request("open licence chat models"), previous, 0L)).isEmpty();
    }

    @Test
    void keepsTheFastPathWhenThereIsNoPreviousPlan() {
        assertThat(planner().replan(request("chat models"), null, 0L)).isEmpty();
        verify(llmAssisted, never()).plan(any(), any());
    }

    private static PlanRequest request(String query) {
        return new PlanRequest("catalog", "en", query, SCHEMA, 2);
    }

    private static TurCopilotQueryPlan deterministicPlan(TurDslQueryRequest body) {
        return TurCopilotQueryPlan.of(body, TurCopilotPlanningStrategy.DETERMINISTIC, "deterministic");
    }

    private static TurDslQueryRequest kindChat() {
        return body("""
                {"query":{"bool":{"filter":[{"term":{"kind":"chat"}}]}}}""");
    }

    private static TurDslQueryRequest body(String json) {
        return MAPPER.readValue(json, TurDslQueryRequest.class);
    }
}

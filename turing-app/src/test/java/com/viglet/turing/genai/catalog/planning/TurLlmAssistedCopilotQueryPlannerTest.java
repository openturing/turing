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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.genai.catalog.planning.TurCopilotPlanRepair.TermFilter;
import com.viglet.turing.genai.catalog.planning.TurCopilotQueryPlanner.PlanRequest;
import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;
import com.viglet.turing.sn.dsl.TurDslQuery;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetField;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser.TurNLFacetParseException;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T819 / §LIX.2 (Block BK) — the multi-pass planner with the two LLM passes stubbed:
 * depth gating (0 = parse only, 1 = + judge, 2 = + refine), the deterministic sort
 * repair, and the fail-open behaviour at every step.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurLlmAssistedCopilotQueryPlannerTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final List<TurNLFacetField> SCHEMA = List.of(
            new TurNLFacetField("kind", TurSEFieldType.STRING, true, "Model kind"),
            new TurNLFacetField("pricing_inputPer1M", TurSEFieldType.DOUBLE, false,
                    "Input price per 1M tokens (USD)"));

    @Mock
    private TurNLFacetParser parser;
    @Mock
    private TurCopilotPlanJudge judge;

    private final TurCopilotPlanCompiler compiler = new TurCopilotPlanCompiler();
    private final TurCopilotPlanValidator validator = new TurCopilotPlanValidator();

    private TurLlmAssistedCopilotQueryPlanner planner() {
        return new TurLlmAssistedCopilotQueryPlanner(parser, judge, compiler, validator);
    }

    @Test
    void reportsItsStrategy() {
        assertThat(planner().strategy()).isEqualTo(TurCopilotPlanningStrategy.LLM_ASSISTED);
    }

    @Test
    void parsesTheFullQuestionWithoutStrippingTheRankingClause() {
        // The LLM path is i18n-native precisely because it sees the whole question —
        // that is what lets "modelo mais barato" work with no per-language lexicon.
        ArgumentCaptor<TurNLFacetParser.ParseRequest> captor =
                ArgumentCaptor.forClass(TurNLFacetParser.ParseRequest.class);
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(captor.capture())).thenReturn(kindChat());

        planner().plan(request("modelo de chat mais barato", 0));

        assertThat(captor.getValue().query()).isEqualTo("modelo de chat mais barato");
    }

    @Test
    void stopsAfterTheParseAtDepthZero() {
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenReturn(kindChat());

        TurCopilotQueryPlan plan = planner().plan(request("cheap chat models", 0));

        verify(judge, never()).judge(any(), any());
        assertThat(plan.llmPasses()).isEqualTo(1);
        assertThat(plan.notes()).contains("parse-only");
        // Even a parse that omitted "size" comes back bounded.
        assertThat(plan.request().size()).isEqualTo(TurCopilotQueryPlanner.DEFAULT_TOP_K);
    }

    @Test
    void keepsTheParseWhenTheJudgeAcceptsIt() {
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenReturn(kindChat());
        when(judge.judge(any(), any())).thenReturn(Optional.of(
                new TurCopilotPlanVerdict(true, List.of(), null, null, null, "looks right")));

        TurCopilotQueryPlan plan = planner().plan(request("chat models", 2));

        verify(judge, never()).refine(any(), any(), any());
        assertThat(plan.llmPasses()).isEqualTo(2);
        assertThat(plan.notes()).contains("query accepted");
        assertThat(plan.request().query()).isInstanceOf(TurDslQuery.Bool.class);
    }

    @Test
    void appliesTheJudgedSortDeterministicallyAtDepthOne() {
        // Depth 1 = judge but no refine. The one repair we can still make without a
        // second generation call is the sort the judge named — verified against the
        // schema, not trusted.
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenReturn(kindChat());
        when(judge.judge(any(), any())).thenReturn(Optional.of(new TurCopilotPlanVerdict(
                false, List.of(), "pricing_inputPer1M", "ASC", null, "sort was dropped")));

        TurCopilotQueryPlan plan = planner().plan(request("cheapest chat model", 1));

        verify(judge, never()).refine(any(), any(), any());
        assertThat(plan.request().sort()).containsExactly(Map.of("pricing_inputPer1M", "asc"));
        // The facet the parse got right survives the repair.
        assertThat(plan.request().query()).isInstanceOf(TurDslQuery.Bool.class);
        assertThat(plan.llmPasses()).isEqualTo(2);
    }

    @Test
    void ignoresAJudgedSortOnAFieldTheSchemaDoesNotGround() {
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenReturn(kindChat());
        when(judge.judge(any(), any())).thenReturn(Optional.of(new TurCopilotPlanVerdict(
                false, List.of(), "invented_score", "desc", null, "hallucinated field")));

        TurCopilotQueryPlan plan = planner().plan(request("smartest chat model", 1));

        assertThat(plan.request().sort()).isNull();
    }

    @Test
    void refinesIntoAGroundedQueryAtDepthTwo() {
        when(parser.isAvailable()).thenReturn(true);
        // The failure the block exists for: the parse came back empty.
        when(parser.parse(any())).thenReturn(body("{\"query\":{\"match_all\":{}}}"));
        when(judge.judge(any(), any())).thenReturn(Optional.of(new TurCopilotPlanVerdict(
                false, List.of("kind=chat was dropped"), "pricing_inputPer1M", "asc", null,
                "empty query")));
        when(judge.refine(any(), any(), any())).thenReturn(Optional.of(new TurCopilotPlanRepair(
                List.of(new TermFilter("kind", "chat")), null, null,
                "pricing_inputPer1M", "asc", "restated")));

        TurCopilotQueryPlan plan = planner().plan(request("modelo de chat mais barato", 2));

        assertThat(plan.llmPasses()).isEqualTo(3);
        assertThat(plan.notes()).contains("parse+judge+refine");
        assertThat(plan.request().sort()).containsExactly(Map.of("pricing_inputPer1M", "asc"));
        TurDslQuery.Bool bool = (TurDslQuery.Bool) plan.request().query();
        assertThat(bool.filter()).containsExactly(new TurDslQuery.Term("kind", "chat"));
    }

    @Test
    void keepsTheParseWhenTheRefineProducesNothingGrounded() {
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenReturn(kindChat());
        when(judge.judge(any(), any())).thenReturn(Optional.of(new TurCopilotPlanVerdict(
                false, List.of("vendor filter dropped"), null, null, null, "incomplete")));
        // Everything the model proposed is ungrounded → the compiler yields nothing.
        when(judge.refine(any(), any(), any())).thenReturn(Optional.of(new TurCopilotPlanRepair(
                List.of(new TermFilter("licence", "open")), null, null, "licence", "desc", null)));

        TurCopilotQueryPlan plan = planner().plan(request("open licence chat models", 2));

        TurDslQuery.Bool bool = (TurDslQuery.Bool) plan.request().query();
        assertThat(bool.filter()).containsExactly(new TurDslQuery.Term("kind", "chat"));
        assertThat(plan.notes()).contains("repair ungrounded");
    }

    @Test
    void keepsTheParseWhenTheJudgeIsUnavailable() {
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenReturn(kindChat());
        when(judge.judge(any(), any())).thenReturn(Optional.empty());

        TurCopilotQueryPlan plan = planner().plan(request("chat models", 2));

        verify(judge, never()).refine(any(), any(), any());
        assertThat(plan.llmPasses()).isEqualTo(1);
        assertThat(plan.notes()).contains("judge unavailable");
    }

    @Test
    void keepsTheJudgedPlanWhenTheRefineIsUnavailable() {
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenReturn(kindChat());
        when(judge.judge(any(), any())).thenReturn(Optional.of(new TurCopilotPlanVerdict(
                false, List.of(), "pricing_inputPer1M", "asc", null, "sort missing")));
        when(judge.refine(any(), any(), any())).thenReturn(Optional.empty());

        TurCopilotQueryPlan plan = planner().plan(request("cheapest chat model", 2));

        assertThat(plan.notes()).contains("refine unavailable");
        // The deterministic sort repair still landed.
        assertThat(plan.request().sort()).containsExactly(Map.of("pricing_inputPer1M", "asc"));
    }

    @Test
    void degradesToFreeTextWhenTheParseFails() {
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenThrow(new TurNLFacetParseException("truncated JSON"));
        when(judge.judge(any(), any())).thenReturn(Optional.empty());

        TurCopilotQueryPlan plan = planner().plan(request("cheap chat models", 2));

        assertThat(plan.request().query()).isInstanceOf(TurDslQuery.Match.class);
    }

    @Test
    void clampsAnOutOfRangeDepthToTheFullLoop() {
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenReturn(kindChat());
        when(judge.judge(any(), any())).thenReturn(Optional.of(new TurCopilotPlanVerdict(
                false, List.of("dropped"), null, null, null, null)));
        when(judge.refine(any(), any(), any())).thenReturn(Optional.of(new TurCopilotPlanRepair(
                List.of(new TermFilter("kind", "chat")), null, null, null, null, null)));

        TurCopilotQueryPlan plan = planner().plan(request("chat models", 99));

        // 99 clamps to 2 — the refine ran rather than being treated as depth 99.
        assertThat(plan.notes()).contains("parse+judge+refine");
    }

    @Test
    void labelsThePlanUnderTheCallersStrategyForHybridReuse() {
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenReturn(kindChat());

        TurCopilotQueryPlan plan = planner()
                .plan(request("chat models", 0), TurCopilotPlanningStrategy.HYBRID);

        assertThat(plan.strategy()).isEqualTo(TurCopilotPlanningStrategy.HYBRID);
    }

    private static PlanRequest request(String query, int maxPasses) {
        return new PlanRequest("catalog", "en", query, SCHEMA, maxPasses);
    }

    private static TurDslQueryRequest kindChat() {
        return body("""
                {"query":{"bool":{"filter":[{"term":{"kind":"chat"}}]}}}""");
    }

    private static TurDslQueryRequest body(String json) {
        return MAPPER.readValue(json, TurDslQueryRequest.class);
    }
}

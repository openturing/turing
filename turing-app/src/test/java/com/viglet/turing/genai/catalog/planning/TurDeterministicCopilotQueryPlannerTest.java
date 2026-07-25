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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.genai.catalog.planning.TurCopilotQueryPlanner.PlanRequest;
import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;
import com.viglet.turing.sn.dsl.TurDslQuery;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetField;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser.TurNLFacetParseException;
import com.viglet.turing.sn.dsl.eval.TurNLRankingPlanner;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T818 / §LIX.1 (Block BK) — the DETERMINISTIC planner must reproduce the pre-block
 * behaviour exactly: deterministic ranking overlay, ranking clause stripped before the
 * LLM facet parse, sort + bounded size injected afterwards, and no LLM call at all for
 * a sort-only question.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurDeterministicCopilotQueryPlannerTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final List<TurNLFacetField> SCHEMA = List.of(
            new TurNLFacetField("kind", TurSEFieldType.STRING, true, "Model kind"),
            new TurNLFacetField("pricing_inputPer1M", TurSEFieldType.DOUBLE, false,
                    "Input price per 1M tokens (USD)"),
            new TurNLFacetField("benchmarks_intelligenceIndex", TurSEFieldType.FLOAT, false,
                    "Overall intelligence index"));

    @Mock
    private TurNLFacetParser parser;

    private final TurNLRankingPlanner rankingPlanner = new TurNLRankingPlanner();

    private TurDeterministicCopilotQueryPlanner planner() {
        return new TurDeterministicCopilotQueryPlanner(parser, rankingPlanner);
    }

    @Test
    void reportsItsStrategy() {
        assertThat(planner().strategy()).isEqualTo(TurCopilotPlanningStrategy.DETERMINISTIC);
    }

    @Test
    void stripsTheRankingClauseBeforeTheParseAndInjectsTheSortAfter() {
        ArgumentCaptor<TurNLFacetParser.ParseRequest> captor =
                ArgumentCaptor.forClass(TurNLFacetParser.ParseRequest.class);
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(captor.capture())).thenReturn(body("""
                {"query":{"bool":{"filter":[{"term":{"kind":"chat"}}]}}}"""));

        TurCopilotQueryPlan plan = planner().plan(request("chat models sorted by intelligence index"));

        // The LLM saw the question WITHOUT the ranking clause, so the facet survived...
        assertThat(captor.getValue().query())
                .doesNotContainIgnoringCase("sorted by")
                .containsIgnoringCase("chat");
        // ...and the deterministic sort + a bounded page were injected on top.
        assertThat(plan.request().sort())
                .containsExactly(Map.of("benchmarks_intelligenceIndex", "desc"));
        assertThat(plan.request().size()).isEqualTo(TurCopilotQueryPlanner.DEFAULT_TOP_K);
        assertThat(plan.llmPasses()).isEqualTo(1);
        assertThat(plan.strategy()).isEqualTo(TurCopilotPlanningStrategy.DETERMINISTIC);
    }

    @Test
    void skipsTheLlmEntirelyForASortOnlyQuestion() {
        TurCopilotQueryPlan plan = planner().plan(request("which model has the lowest price?"));

        verify(parser, never()).parse(any());
        assertThat(plan.request().query()).isInstanceOf(TurDslQuery.MatchAll.class);
        assertThat(plan.request().sort()).containsExactly(Map.of("pricing_inputPer1M", "asc"));
        assertThat(plan.llmPasses()).isZero();
        assertThat(plan.notes()).contains("sort-only");
    }

    @Test
    void fallsBackToFreeTextWhenTheParserIsUnavailable() {
        when(parser.isAvailable()).thenReturn(false);

        TurCopilotQueryPlan plan = planner().plan(request("open weight multimodal models"));

        verify(parser, never()).parse(any());
        assertThat(plan.request().query())
                .isEqualTo(new TurDslQuery.Match("_text_", "open weight multimodal models", null));
        assertThat(plan.llmPasses()).isZero();
    }

    @Test
    void fallsBackToFreeTextWhenTheParseThrows() {
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenThrow(new TurNLFacetParseException("model returned prose"));

        TurCopilotQueryPlan plan = planner().plan(request("open weight multimodal models"));

        assertThat(plan.request().query()).isInstanceOf(TurDslQuery.Match.class);
        // The parse was attempted and cost a call even though it failed.
        assertThat(plan.llmPasses()).isEqualTo(1);
    }

    @Test
    void fallsBackToFreeTextWhenTheSiteDeclaresNoSchema() {
        TurCopilotQueryPlan plan = planner().plan(new PlanRequest("catalog", "en",
                "cheapest chat model", List.of(), 2));

        verify(parser, never()).parse(any());
        assertThat(plan.request().query()).isInstanceOf(TurDslQuery.Match.class);
    }

    @Test
    void neverOverridesASortTheParserAlreadyProduced() {
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenReturn(body("""
                {"query":{"match_all":{}},"sort":[{"pricing_inputPer1M":"desc"}]}"""));

        TurCopilotQueryPlan plan = planner().plan(request("chat models sorted by intelligence index"));

        assertThat(plan.request().sort()).containsExactly(Map.of("pricing_inputPer1M", "desc"));
    }

    @Test
    void leavesAQuestionWithNoRankingIntentUntouched() {
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenReturn(body("""
                {"query":{"bool":{"filter":[{"term":{"kind":"chat"}}]}}}"""));

        TurCopilotQueryPlan plan = planner().plan(request("chat models from OpenAI"));

        assertThat(plan.request().sort()).isNull();
        assertThat(plan.notes()).contains("ranking=none");
    }

    private static PlanRequest request(String query) {
        return new PlanRequest("catalog", "en", query, SCHEMA, 2);
    }

    private static TurDslQueryRequest body(String json) {
        return MAPPER.readValue(json, TurDslQueryRequest.class);
    }
}

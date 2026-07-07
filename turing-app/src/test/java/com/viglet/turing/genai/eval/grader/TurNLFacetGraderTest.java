/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval.grader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.eval.grader.builtin.TurNLFacetGrader;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalReport.CaseResult;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalScorer;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser;

/**
 * T601 / §XXXIII.16 — the NL→facet scorer exposed as a grader: applies-gating,
 * mapping the scorer's {@link CaseResult} to a grader result, and fail-closed.
 * The scorer/parser are mocked (the scorer itself is exhaustively tested in
 * {@code sn.dsl.eval}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurNLFacetGraderTest {

    @Mock
    private TurNLFacetEvalScorer scorer;
    @Mock
    private TurNLFacetParser parser;

    private TurNLFacetGrader grader() {
        return new TurNLFacetGrader(scorer, parser);
    }

    private static TurEvalGraderConfigView cfg(String extra) {
        String json = "{\"query\":\"cheap laptops\",\"index\":\"products\","
                + "\"expectation\":{\"filters\":[{\"field\":\"category\",\"value\":\"laptop\"}]},"
                + "\"schema\":[{\"name\":\"category\",\"type\":\"STRING\",\"facet\":true}]" + extra + "}";
        return new TurEvalGraderConfigView("nl-facet", "nlf", "CODE", json, 1d, 0d, 1);
    }

    private static TurEvalGradingContext ctx() {
        TurAgentEvalCase c = new TurAgentEvalCase();
        c.setName("facet-case");
        return new TurEvalGradingContext(c, "conv-1", List.of(), List.of(), java.util.Map.of(),
                null, null, null);
    }

    @Test
    void appliesWhenConfiguredAndParserAvailable() {
        when(parser.isAvailable()).thenReturn(true);
        assertThat(grader().appliesTo(ctx(), cfg(""))).isTrue();
    }

    @Test
    void doesNotApplyWithoutParser() {
        when(parser.isAvailable()).thenReturn(false);
        assertThat(grader().appliesTo(ctx(), cfg(""))).isFalse();
    }

    @Test
    void doesNotApplyWithoutQuery() {
        TurEvalGraderConfigView noQuery = new TurEvalGraderConfigView("nl-facet", "nlf", "CODE",
                "{\"expectation\":{},\"schema\":[]}", 1d, 0d, 1);
        assertThat(grader().appliesTo(ctx(), noQuery)).isFalse();
    }

    @Test
    void mapsScorerResultToGraderResult() {
        lenient().when(parser.parse(any())).thenReturn(org.mockito.Mockito.mock(TurDslQueryRequest.class));
        when(scorer.score(any(), any(), any(), any())).thenReturn(
                new CaseResult("facet-case", true, 1.0d, List.of(), List.of(), null));

        TurEvalGraderResult r = grader().grade(ctx(), cfg(""));

        assertThat(r.passed()).isTrue();
        assertThat(r.score()).isEqualTo(1.0d);
    }

    @Test
    void failingCaseCarriesFindingsRationale() {
        lenient().when(parser.parse(any())).thenReturn(org.mockito.Mockito.mock(TurDslQueryRequest.class));
        when(scorer.score(any(), any(), any(), any())).thenReturn(new CaseResult("facet-case", false,
                0.5d, List.of("missing filter category=laptop"), List.of(), null));

        TurEvalGraderResult r = grader().grade(ctx(), cfg(""));

        assertThat(r.passed()).isFalse();
        assertThat(r.score()).isEqualTo(0.5d);
        assertThat(r.rationale()).contains("missing filter");
    }

    @Test
    void failsClosedWhenParserThrows() {
        when(parser.parse(any())).thenThrow(new RuntimeException("no llm"));
        TurEvalGraderResult r = grader().grade(ctx(), cfg(""));
        assertThat(r.passed()).isFalse();
        assertThat(r.rationale()).contains("nl-facet error");
    }
}

/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.dsl.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalReport.CaseResult;
import com.viglet.turing.sn.dsl.eval.TurNLFacetExpectation.ExpectedFilter;
import com.viglet.turing.sn.dsl.eval.TurNLFacetExpectation.ExpectedRange;
import com.viglet.turing.sn.dsl.eval.TurNLFacetExpectation.ExpectedSort;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deterministic tests for the NL→facet scorer (T385 / §XX.5). No LLM, no DB —
 * the scorer is the regression-test core, so we exercise filter/range/sort
 * matching, the range-direction tolerance, and the hard grounding rule.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurNLFacetEvalScorerTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final TurNLFacetEvalScorer scorer = new TurNLFacetEvalScorer();

    private static final List<TurNLFacetField> SCHEMA = List.of(
            new TurNLFacetField("modality", TurSEFieldType.STRING, true, null),
            new TurNLFacetField("degree", TurSEFieldType.STRING, true, null),
            new TurNLFacetField("area", TurSEFieldType.STRING, true, null),
            new TurNLFacetField("tuition", TurSEFieldType.CURRENCY, false, null),
            new TurNLFacetField("durationMonths", TurSEFieldType.INT, false, null));

    private TurDslQueryRequest req(String json) {
        return MAPPER.readValue(json, TurDslQueryRequest.class);
    }

    @Test
    void exactFilterRangeAndSortAllMatch() {
        TurDslQueryRequest q = req("""
                {"query":{"bool":{"filter":[
                    {"term":{"modality":"online"}},
                    {"term":{"degree":"pos"}},
                    {"range":{"tuition":{"lte":20000}}}
                ]}}}""");
        TurNLFacetExpectation expect = new TurNLFacetExpectation(
                List.of(new ExpectedFilter("modality", "online"),
                        new ExpectedFilter("degree", "pos")),
                List.of(new ExpectedRange("tuition", null, null, 20000d, null)),
                null);

        CaseResult r = scorer.score("c", expect, q, SCHEMA);

        assertThat(r.passed()).isTrue();
        assertThat(r.score()).isEqualTo(1d);
        assertThat(r.findings()).isEmpty();
    }

    @Test
    void missingFilterFailsAndIsReported() {
        TurDslQueryRequest q = req("""
                {"query":{"bool":{"filter":[{"term":{"modality":"online"}}]}}}""");
        TurNLFacetExpectation expect = new TurNLFacetExpectation(
                List.of(new ExpectedFilter("modality", "online"),
                        new ExpectedFilter("degree", "pos")),
                List.of(), null);

        CaseResult r = scorer.score("c", expect, q, SCHEMA);

        assertThat(r.passed()).isFalse();
        assertThat(r.score()).isEqualTo(0.5d);
        assertThat(r.findings()).anyMatch(f -> f.contains("degree"));
    }

    @Test
    void upperBoundToleratesLtVersusLte() {
        // expectation says lte:20000, parser produced lt:20000 — same direction, same value
        TurDslQueryRequest q = req("""
                {"query":{"range":{"tuition":{"lt":20000}}}}""");
        TurNLFacetExpectation expect = new TurNLFacetExpectation(List.of(),
                List.of(new ExpectedRange("tuition", null, null, 20000d, null)), null);

        assertThat(scorer.score("c", expect, q, SCHEMA).passed()).isTrue();
    }

    @Test
    void betweenRangeMatchesGteAndLte() {
        TurDslQueryRequest q = req("""
                {"query":{"range":{"durationMonths":{"gte":12,"lte":24}}}}""");
        TurNLFacetExpectation expect = new TurNLFacetExpectation(List.of(),
                List.of(new ExpectedRange("durationMonths", 12d, null, 24d, null)), null);

        assertThat(scorer.score("c", expect, q, SCHEMA).passed()).isTrue();
    }

    @Test
    void wrongRangeValueFails() {
        TurDslQueryRequest q = req("""
                {"query":{"range":{"tuition":{"lte":50000}}}}""");
        TurNLFacetExpectation expect = new TurNLFacetExpectation(List.of(),
                List.of(new ExpectedRange("tuition", null, null, 20000d, null)), null);

        assertThat(scorer.score("c", expect, q, SCHEMA).passed()).isFalse();
    }

    @Test
    void termsMultiValueMatchesAnyExpectedValue() {
        TurDslQueryRequest q = req("""
                {"query":{"terms":{"modality":["online","hibrido"]}}}""");
        TurNLFacetExpectation expect = new TurNLFacetExpectation(
                List.of(new ExpectedFilter("modality", "hibrido")), List.of(), null);

        assertThat(scorer.score("c", expect, q, SCHEMA).passed()).isTrue();
    }

    @Test
    void sortViaMapWithOrderObjectMatches() {
        TurDslQueryRequest q = req("""
                {"query":{"match_all":{}},"sort":[{"tuition":{"order":"asc"}}]}""");
        TurNLFacetExpectation expect = new TurNLFacetExpectation(List.of(), List.of(),
                new ExpectedSort("tuition", "asc"));

        assertThat(scorer.score("c", expect, q, SCHEMA).passed()).isTrue();
    }

    @Test
    void ungroundedFieldFailsEvenWhenExpectationsMet() {
        // "scholarship" is not in the schema — a hallucinated facet
        TurDslQueryRequest q = req("""
                {"query":{"bool":{"filter":[
                    {"term":{"modality":"online"}},
                    {"term":{"scholarship":"true"}}
                ]}}}""");
        TurNLFacetExpectation expect = new TurNLFacetExpectation(
                List.of(new ExpectedFilter("modality", "online")), List.of(), null);

        CaseResult r = scorer.score("c", expect, q, SCHEMA);

        assertThat(r.passed()).isFalse();
        assertThat(r.ungroundedFields()).containsExactly("scholarship");
    }

    @Test
    void specialTextFieldIsAlwaysGrounded() {
        TurDslQueryRequest q = req("""
                {"query":{"match":{"_text_":"artificial intelligence"}}}""");
        TurNLFacetExpectation expect = new TurNLFacetExpectation(List.of(), List.of(), null);

        CaseResult r = scorer.score("c", expect, q, SCHEMA);

        assertThat(r.passed()).isTrue();
        assertThat(r.ungroundedFields()).isEmpty();
    }

    @Test
    void nullQueryIsAFailure() {
        CaseResult r = scorer.score("c",
                new TurNLFacetExpectation(List.of(), List.of(), null), null, SCHEMA);
        assertThat(r.passed()).isFalse();
        assertThat(r.error()).isNotNull();
    }
}

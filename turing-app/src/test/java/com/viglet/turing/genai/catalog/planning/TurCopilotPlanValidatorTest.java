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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetField;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T819–T820 / §LIX (Block BK) — the deterministic guards the LLM planning strategies
 * lean on. These run with no LLM at all, which is the point: the model proposes and
 * this validates.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurCopilotPlanValidatorTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final TurCopilotPlanValidator validator = new TurCopilotPlanValidator();

    private static final List<TurNLFacetField> SCHEMA = List.of(
            new TurNLFacetField("kind", TurSEFieldType.STRING, true, "Model kind"),
            new TurNLFacetField("price", TurSEFieldType.DOUBLE, false, "Input price per 1M tokens"));

    @Test
    void collectsEveryReferencedFieldAcrossClausesAndSorts() {
        TurDslQueryRequest request = body("""
                {"query":{"bool":{"filter":[
                  {"term":{"kind":"chat"}},
                  {"range":{"price":{"lte":5}}},
                  {"exists":{"field":"vendor"}}
                ]}},"sort":[{"price":"asc"}]}""");

        assertThat(validator.referencedFields(request))
                .containsExactlyInAnyOrder("kind", "price", "vendor");
    }

    @Test
    void ignoresTheDefaultTextFieldWhenCollectingReferences() {
        // _text_ is the free-text catch-all, never a declared field — reporting it as
        // ungrounded would make every free-text fallback look broken.
        TurDslQueryRequest request = body("""
                {"query":{"match":{"_text_":"cheap chat model"}}}""");

        assertThat(validator.referencedFields(request)).isEmpty();
        assertThat(validator.ungroundedFields(request, SCHEMA)).isEmpty();
    }

    @Test
    void reportsFieldsMissingFromTheDeclaredSchema() {
        TurDslQueryRequest request = body("""
                {"query":{"bool":{"filter":[
                  {"term":{"kind":"chat"}},
                  {"term":{"licence":"open"}}
                ]}}}""");

        assertThat(validator.ungroundedFields(request, SCHEMA)).containsExactly("licence");
    }

    @Test
    void groundsNothingWhenNoSchemaIsDeclared() {
        // Fail-open: with no schema to check against, never accuse the plan.
        TurDslQueryRequest request = body("""
                {"query":{"term":{"licence":"open"}}}""");

        assertThat(validator.ungroundedFields(request, List.of())).isEmpty();
        assertThat(validator.ungroundedFields(request, null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // a bare match_all with no sort — "the parse came back empty"
            "{\"query\":{\"match_all\":{}}}                             | true",
            // an empty bool is the same shape wearing a different hat
            "{\"query\":{\"bool\":{\"filter\":[]}}}                     | true",
            // match_all + a sort DOES retrieve something meaningful (T811 sort-only)
            "{\"query\":{\"match_all\":{}},\"sort\":[{\"price\":\"asc\"}]} | false",
            // a real filter is never degenerate
            "{\"query\":{\"term\":{\"kind\":\"chat\"}}}                 | false",
            // free text counts as a retrieval signal
            "{\"query\":{\"match\":{\"_text_\":\"chat\"}}}              | false",
    })
    void detectsDegeneratePlans(String json, boolean degenerate) {
        assertThat(validator.isDegenerate(body(json.trim()))).isEqualTo(degenerate);
    }

    @Test
    void treatsAnAbsentQueryAsDegenerate() {
        assertThat(validator.isDegenerate(null)).isTrue();
        assertThat(validator.isDegenerate(body("{}"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "which chat model is the cheapest",
            "qual o modelo de chat mais barato",
            "open weight models under 5 dollars",
    })
    void treatsAConstrainedQuestionAsSpecific(String query) {
        assertThat(validator.isSpecific(query)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "what do you have?",
            "o que vocês tem?",
            "show me all",
            "hi",
    })
    void treatsABroadQuestionAsNotSpecific(String query) {
        // A broad question legitimately plans to match_all, so it must NOT trigger the
        // T820 "degenerate plan" escalation.
        assertThat(validator.isSpecific(query)).isFalse();
    }

    @Test
    void treatsBlankAsNotSpecific() {
        assertThat(validator.isSpecific(null)).isFalse();
        assertThat(validator.isSpecific("   ")).isFalse();
    }

    private static TurDslQueryRequest body(String json) {
        return MAPPER.readValue(json, TurDslQueryRequest.class);
    }
}

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
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.genai.catalog.planning.TurCopilotPlanRepair.RangeFilter;
import com.viglet.turing.genai.catalog.planning.TurCopilotPlanRepair.TermFilter;
import com.viglet.turing.sn.dsl.TurDslQuery;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetField;

/**
 * T819 / §LIX.2 (Block BK) — the refine pass's deterministic half: a model-proposed
 * constraint list only becomes a query after every field has been checked against the
 * declared schema. These tests are the block's grounding invariant, expressed without
 * an LLM.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurCopilotPlanCompilerTest {

    private static final int SIZE = 8;

    private static final List<TurNLFacetField> SCHEMA = List.of(
            new TurNLFacetField("kind", TurSEFieldType.STRING, true, "Model kind"),
            new TurNLFacetField("vendor", TurSEFieldType.STRING, true, "Vendor name"),
            new TurNLFacetField("price", TurSEFieldType.DOUBLE, false, "Input price per 1M tokens"),
            new TurNLFacetField("intelligence", TurSEFieldType.FLOAT, false, "Overall intelligence index"));

    private final TurCopilotPlanCompiler compiler = new TurCopilotPlanCompiler();

    @Test
    void compilesTermRangeAndSortIntoOneGroundedBody() {
        TurCopilotPlanRepair repair = new TurCopilotPlanRepair(
                List.of(new TermFilter("kind", "chat")),
                List.of(new RangeFilter("price", null, 5.0)),
                null, "intelligence", "desc", "ranked by the overall index");

        TurDslQueryRequest body = compiler.compile(repair, SCHEMA, SIZE).orElseThrow();

        assertThat(body.size()).isEqualTo(SIZE);
        assertThat(body.sort()).containsExactly(Map.of("intelligence", "desc"));
        assertThat(body.query()).isInstanceOf(TurDslQuery.Bool.class);
        TurDslQuery.Bool bool = (TurDslQuery.Bool) body.query();
        assertThat(bool.filter()).containsExactly(
                new TurDslQuery.Term("kind", "chat"),
                new TurDslQuery.Range("price", null, null, 5.0, null));
    }

    @Test
    void dropsFiltersOnFieldsTheSchemaDoesNotDeclare() {
        // The whole point: a field the model invented never reaches the engine.
        TurCopilotPlanRepair repair = new TurCopilotPlanRepair(
                List.of(new TermFilter("licence", "open"), new TermFilter("vendor", "OpenAI")),
                List.of(new RangeFilter("context_window", 100.0, null)),
                null, null, null, null);

        TurDslQueryRequest body = compiler.compile(repair, SCHEMA, SIZE).orElseThrow();

        TurDslQuery.Bool bool = (TurDslQuery.Bool) body.query();
        assertThat(bool.filter()).containsExactly(new TurDslQuery.Term("vendor", "OpenAI"));
    }

    @Test
    void dropsARangeOnANonNumericField() {
        // A range on a text field silently matches nothing in the engine — drop it here
        // so the copilot returns a usable page instead of a mysterious zero-hit answer.
        TurCopilotPlanRepair repair = new TurCopilotPlanRepair(
                List.of(new TermFilter("kind", "chat")),
                List.of(new RangeFilter("vendor", 1.0, 9.0)),
                null, null, null, null);

        TurDslQuery.Bool bool = (TurDslQuery.Bool) compiler.compile(repair, SCHEMA, SIZE)
                .orElseThrow().query();
        assertThat(bool.filter()).containsExactly(new TurDslQuery.Term("kind", "chat"));
    }

    @Test
    void dropsARangeCarryingNoBound() {
        TurCopilotPlanRepair repair = new TurCopilotPlanRepair(
                List.of(new TermFilter("kind", "chat")),
                List.of(new RangeFilter("price", null, null)),
                null, null, null, null);

        TurDslQuery.Bool bool = (TurDslQuery.Bool) compiler.compile(repair, SCHEMA, SIZE)
                .orElseThrow().query();
        assertThat(bool.filter()).containsExactly(new TurDslQuery.Term("kind", "chat"));
    }

    @Test
    void emitsMatchAllPlusSortForASortOnlyRepair() {
        // The "missed the sort" repair: no filters survived, but the ranking did — a
        // bounded ranked page is exactly right.
        TurCopilotPlanRepair repair = new TurCopilotPlanRepair(null, null, null,
                "price", "ASC", null);

        TurDslQueryRequest body = compiler.compile(repair, SCHEMA, SIZE).orElseThrow();

        assertThat(body.query()).isInstanceOf(TurDslQuery.MatchAll.class);
        // Direction casing from the model is normalised.
        assertThat(body.sort()).containsExactly(Map.of("price", "asc"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "kind", "unknown_field" })
    void refusesToSortOnAFieldThatIsNotDeclaredNumeric(String sortField) {
        // kind is declared but not numeric; unknown_field isn't declared at all.
        TurCopilotPlanRepair repair = new TurCopilotPlanRepair(
                List.of(new TermFilter("vendor", "OpenAI")), null, null, sortField, "desc", null);

        assertThat(compiler.compile(repair, SCHEMA, SIZE).orElseThrow().sort()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "descending", "up", "1" })
    void refusesToSortWithAnUnusableDirection(String order) {
        TurCopilotPlanRepair repair = new TurCopilotPlanRepair(
                List.of(new TermFilter("vendor", "OpenAI")), null, null, "price", order, null);

        assertThat(compiler.compile(repair, SCHEMA, SIZE).orElseThrow().sort()).isNull();
    }

    @Test
    void keepsResidualFreeTextAsAMatchOnTheDefaultField() {
        TurCopilotPlanRepair repair = new TurCopilotPlanRepair(null, null,
                "  multilingual reasoning  ", null, null, null);

        TurDslQuery.Bool bool = (TurDslQuery.Bool) compiler.compile(repair, SCHEMA, SIZE)
                .orElseThrow().query();
        assertThat(bool.filter())
                .containsExactly(new TurDslQuery.Match("_text_", "multilingual reasoning", null));
    }

    @Test
    void returnsEmptyWhenNothingSurvivedGrounding() {
        // Nothing usable came back — the caller must keep the plan it already had rather
        // than replacing it with an unbounded match_all.
        TurCopilotPlanRepair repair = new TurCopilotPlanRepair(
                List.of(new TermFilter("licence", "open")), null, "  ", "licence", "desc", null);

        assertThat(compiler.compile(repair, SCHEMA, SIZE)).isEmpty();
    }

    @Test
    void returnsEmptyForANullRepair() {
        assertThat(compiler.compile(null, SCHEMA, SIZE)).isEqualTo(Optional.empty());
    }

    @Test
    void toleratesNullEntriesInsideTheProposedLists() {
        // Structured output from a weak model can carry nulls inside the arrays.
        TurCopilotPlanRepair repair = new TurCopilotPlanRepair(
                java.util.Arrays.asList(null, new TermFilter("kind", "chat")),
                java.util.Arrays.asList((RangeFilter) null),
                null, null, null, null);

        TurDslQuery.Bool bool = (TurDslQuery.Bool) compiler.compile(repair, SCHEMA, SIZE)
                .orElseThrow().query();
        assertThat(bool.filter()).containsExactly(new TurDslQuery.Term("kind", "chat"));
    }
}

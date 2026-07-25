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

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.viglet.turing.sn.dsl.eval.TurNLFacetExpectation.ExpectedFilter;
import com.viglet.turing.sn.dsl.eval.TurNLFacetExpectation.ExpectedSort;
import com.viglet.turing.sn.dsl.eval.TurNLRankingPlanner.RankingIntent;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T813 / §LVII.3 (Block BI) — the model-catalog ranking regression, run against the
 * shared sample content ({@code model-catalog.nl-facet-eval.json}) that reproduces
 * the live copilot bug. This is the <strong>always-on</strong> half (no LLM, no
 * index): it asserts the deterministic {@link TurNLRankingPlanner} resolves every
 * golden {@code sort} in the pack and — the crux of the fix — that stripping the
 * ranking clause leaves the facet token intact, so the LLM faceting pass never
 * loses {@code kind=CHAT}/{@code kind=EMBEDDING}. The same pack is a real-LLM +
 * live-Lucene fixture for the {@code -Pnl-facet-eval} eval harness.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurNLRankingPlannerSampleTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final TurNLRankingPlanner planner = new TurNLRankingPlanner();

    private TurNLFacetEvalPack loadPack() throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("nl-facet-eval/model-catalog.nl-facet-eval.json")) {
            assertThat(in).as("sample pack must be on the test classpath").isNotNull();
            return MAPPER.readValue(in, TurNLFacetEvalPack.class);
        }
    }

    @Test
    void everyGoldenSortIsResolvedDeterministicallyAndFacetSurvivesTheStrip() throws IOException {
        TurNLFacetEvalPack pack = loadPack();
        assertThat(pack.cases()).isNotEmpty();

        for (TurNLFacetEvalCase evalCase : pack.cases()) {
            RankingIntent intent = planner.detect(evalCase.query(), pack.fields());
            ExpectedSort expectedSort = evalCase.expect().sort();

            if (expectedSort != null && expectedSort.field() != null) {
                assertThat(intent.hasSort())
                        .as("case '%s' must resolve a ranking sort", evalCase.name())
                        .isTrue();
                assertThat(intent.field())
                        .as("case '%s' sort field", evalCase.name())
                        .isEqualTo(expectedSort.field());
                assertThat(intent.order())
                        .as("case '%s' sort order", evalCase.name())
                        .isEqualTo(expectedSort.order());

                // The facet must survive the ranking strip: each expected filter value
                // still appears in the stripped query the LLM will facet on (unless the
                // query was pure ranking, which strips to blank → sort-only match_all).
                for (ExpectedFilter filter : evalCase.expect().filters()) {
                    assertThat(intent.strippedQuery())
                            .as("case '%s' must keep facet '%s' after stripping",
                                    evalCase.name(), filter.value())
                            .containsIgnoringCase(filter.value());
                }
            } else {
                assertThat(intent.hasSort())
                        .as("case '%s' must NOT invent a sort", evalCase.name())
                        .isFalse();
            }
        }
    }
}

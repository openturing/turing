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
import static org.mockito.Mockito.lenient;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.catalog.planning.TurCopilotPlanningComparison.StrategyOutcome;
import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;
import com.viglet.turing.properties.TurCatalogCopilotProperty;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalPack;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalScorer;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalService;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser;
import com.viglet.turing.sn.dsl.eval.TurNLRankingPlanner;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T821 / §LIX.4 (Block BK) — the comparative planning-strategy eval. The three
 * planners are real (only the judge's LLM call is mocked), so the test pins the two
 * properties the report exists to deliver: the three axes are measured per strategy
 * over the <em>same</em> cases, and the plan-only limit of HYBRID is stated rather
 * than hidden behind numbers that look like parity.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurCopilotPlanningComparisonServiceTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final TurNLFacetEvalScorer scorer = new TurNLFacetEvalScorer();
    private final TurCopilotPlanValidator validator = new TurCopilotPlanValidator();
    private final TurCopilotPlanCompiler compiler = new TurCopilotPlanCompiler();

    @Mock
    private TurCopilotPlanJudge judge;

    // ─────────────────────────── Tests ───────────────────────────

    @Test
    void comparesEveryStrategyOverTheSameCases() {
        acceptingJudge();
        TurCopilotPlanningComparison comparison = service(new GoldenParser())
                .compare(TurCopilotPlanningComparisonRequest.of(loadCoursesPack()));

        assertThat(comparison.error()).isNull();
        assertThat(comparison.caseCount()).isEqualTo(4);
        assertThat(comparison.strategies())
                .extracting(StrategyOutcome::strategy)
                .containsExactly(TurCopilotPlanningStrategy.DETERMINISTIC,
                        TurCopilotPlanningStrategy.LLM_ASSISTED,
                        TurCopilotPlanningStrategy.HYBRID);
        assertThat(comparison.strategies())
                .allSatisfy(outcome -> assertThat(outcome.results()).hasSize(4));

        // Quality: a golden parser means every strategy plans the golden clauses.
        assertThat(comparison.strategies())
                .allSatisfy(outcome -> {
                    assertThat(outcome.score()).isEqualTo(1d);
                    assertThat(outcome.passed()).isTrue();
                    assertThat(outcome.passedCount()).isEqualTo(4);
                });
    }

    @Test
    void reportsTheCostAxisPerStrategy() {
        acceptingJudge();
        TurCopilotPlanningComparison comparison = service(new GoldenParser())
                .compare(TurCopilotPlanningComparisonRequest.of(loadCoursesPack()));

        // DETERMINISTIC: one facet parse per case.
        assertThat(outcome(comparison, TurCopilotPlanningStrategy.DETERMINISTIC).llmPasses())
                .isEqualTo(4);
        // LLM_ASSISTED at the default depth: parse + judge per case — the trade-off the
        // whole comparison exists to make visible.
        assertThat(outcome(comparison, TurCopilotPlanningStrategy.LLM_ASSISTED).llmPasses())
                .isEqualTo(8);
        assertThat(comparison.strategies())
                .allSatisfy(o -> assertThat(o.elapsedMillis()).isNotNegative());
    }

    @Test
    void hybridScoresLikeDeterministicAndTheReportSaysWhy() {
        acceptingJudge();
        TurCopilotPlanningComparison comparison = service(new GoldenParser())
                .compare(TurCopilotPlanningComparisonRequest.of(loadCoursesPack()));

        StrategyOutcome deterministic = outcome(comparison, TurCopilotPlanningStrategy.DETERMINISTIC);
        StrategyOutcome hybrid = outcome(comparison, TurCopilotPlanningStrategy.HYBRID);
        assertThat(hybrid.score()).isEqualTo(deterministic.score());
        assertThat(hybrid.llmPasses()).isEqualTo(deterministic.llmPasses());

        // …and the report must say that is an artefact of the plan-only run, not parity.
        assertThat(hybrid.note()).contains("identical to DETERMINISTIC");
        assertThat(comparison.caveats())
                .contains(TurCopilotPlanningComparisonService.CAVEAT_PLAN_ONLY,
                        TurCopilotPlanningComparisonService.CAVEAT_HYBRID,
                        TurCopilotPlanningComparisonService.CAVEAT_LATENCY);
    }

    @Test
    void comparesOnlyTheRequestedStrategiesAndDropsTheHybridCaveatWithIt() {
        TurCopilotPlanningComparison comparison = service(new GoldenParser())
                .compare(new TurCopilotPlanningComparisonRequest(loadCoursesPack(),
                        List.of(TurCopilotPlanningStrategy.DETERMINISTIC), null));

        assertThat(comparison.strategies())
                .extracting(StrategyOutcome::strategy)
                .containsExactly(TurCopilotPlanningStrategy.DETERMINISTIC);
        assertThat(comparison.caveats())
                .contains(TurCopilotPlanningComparisonService.CAVEAT_PLAN_ONLY)
                .doesNotContain(TurCopilotPlanningComparisonService.CAVEAT_HYBRID);
    }

    @Test
    void depthDefaultsToThePropertyAndIsOverridable() {
        acceptingJudge();
        TurCopilotPlanningComparison configured = service(new GoldenParser())
                .compare(TurCopilotPlanningComparisonRequest.of(loadCoursesPack()));
        assertThat(configured.maxPasses()).isEqualTo(2);

        TurCopilotPlanningComparison parseOnly = service(new GoldenParser())
                .compare(new TurCopilotPlanningComparisonRequest(loadCoursesPack(),
                        List.of(TurCopilotPlanningStrategy.LLM_ASSISTED), 0));

        assertThat(parseOnly.maxPasses()).isZero();
        StrategyOutcome llm = outcome(parseOnly, TurCopilotPlanningStrategy.LLM_ASSISTED);
        assertThat(llm.note()).contains("parse only");
        // Depth 0 skips the judge entirely — one call per case, not two.
        assertThat(llm.llmPasses()).isEqualTo(4);
    }

    @Test
    void aStrategyThatBlowsUpOnACaseStillReportsTheRest() {
        TurCopilotPlanningComparison comparison = service(new ExplodingParser())
                .compare(new TurCopilotPlanningComparisonRequest(loadCoursesPack(),
                        List.of(TurCopilotPlanningStrategy.DETERMINISTIC), null));

        StrategyOutcome deterministic = outcome(comparison, TurCopilotPlanningStrategy.DETERMINISTIC);
        assertThat(comparison.error()).isNull();
        assertThat(deterministic.results()).hasSize(4);
        assertThat(deterministic.passed()).isFalse();
        assertThat(deterministic.results())
                .anySatisfy(r -> assertThat(r.error()).contains("planning failed"));
    }

    @Test
    void noUsableLlmYieldsAnErrorReport() {
        TurCopilotPlanningComparison comparison = service(new UnavailableParser())
                .compare(TurCopilotPlanningComparisonRequest.of(loadCoursesPack()));

        assertThat(comparison.error()).contains("LLM");
        assertThat(comparison.strategies()).isEmpty();
    }

    @Test
    void emptyOrUnresolvablePacksErrorRatherThanRunEmpty() {
        TurCopilotPlanningComparisonService service = service(new GoldenParser());

        assertThat(service.compare(null).error()).contains("No eval pack");

        TurNLFacetEvalPack caseless = new TurNLFacetEvalPack("empty", "courses", "en",
                loadCoursesPack().fields(), List.of());
        assertThat(service.compare(TurCopilotPlanningComparisonRequest.of(caseless)).error())
                .contains("no cases");

        TurNLFacetEvalPack schemaless = new TurNLFacetEvalPack("schemaless", "", "en", List.of(),
                loadCoursesPack().cases());
        assertThat(service.compare(TurCopilotPlanningComparisonRequest.of(schemaless)).error())
                .contains("No field schema");
    }

    @Test
    void availabilityDelegatesToTheEvalService() {
        assertThat(service(new GoldenParser()).isAvailable()).isTrue();
        assertThat(service(new UnavailableParser()).isAvailable()).isFalse();
    }

    // ─────────────────────────── Wiring ───────────────────────────

    private TurCopilotPlanningComparisonService service(TurNLFacetParser parser) {
        TurNLFacetEvalService evalService = new TurNLFacetEvalService(parser, scorer, null, null);
        TurDeterministicCopilotQueryPlanner deterministic =
                new TurDeterministicCopilotQueryPlanner(parser, new TurNLRankingPlanner());
        TurLlmAssistedCopilotQueryPlanner llmAssisted =
                new TurLlmAssistedCopilotQueryPlanner(parser, judge, compiler, validator);
        TurHybridCopilotQueryPlanner hybrid =
                new TurHybridCopilotQueryPlanner(deterministic, llmAssisted, validator);
        return new TurCopilotPlanningComparisonService(evalService, scorer,
                new TurCatalogCopilotProperty(), List.of(deterministic, llmAssisted, hybrid));
    }

    /** A judge that runs and accepts the parsed query — one extra pass, no refine. */
    private void acceptingJudge() {
        lenient().when(judge.judge(any(), any())).thenReturn(Optional.of(
                new TurCopilotPlanVerdict(true, List.of(), null, null, null, "looks right")));
    }

    private static StrategyOutcome outcome(TurCopilotPlanningComparison comparison,
            TurCopilotPlanningStrategy strategy) {
        return comparison.strategies().stream()
                .filter(o -> o.strategy() == strategy)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outcome for " + strategy));
    }

    private TurNLFacetEvalPack loadCoursesPack() {
        try (InputStream in = getClass().getResourceAsStream(
                "/nl-facet-eval/courses.nl-facet-eval.json")) {
            assertThat(in).as("fixture present on classpath").isNotNull();
            return MAPPER.readValue(in, TurNLFacetEvalPack.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ─────────────────────────── Fake parsers ───────────────────────────

    private static TurDslQueryRequest dsl(String json) {
        return MAPPER.readValue(json, TurDslQueryRequest.class);
    }

    /**
     * Returns the golden DSL for each fixture case. It keys off words that survive
     * the DETERMINISTIC planner's ranking-clause strip, so all three strategies see
     * the same parser behaviour and the comparison isolates the planners.
     */
    private static class GoldenParser implements TurNLFacetParser {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TurDslQueryRequest parse(ParseRequest request) {
            String q = request.query().toLowerCase();
            if (q.contains("graduate")) {
                return dsl("""
                        {"query":{"bool":{"filter":[
                            {"term":{"modality":"online"}},
                            {"term":{"degree":"pos"}},
                            {"range":{"tuition":{"lte":20000}}}
                        ]}}}""");
            }
            if (q.contains("in-person")) {
                return dsl("""
                        {"query":{"bool":{"filter":[
                            {"term":{"modality":"presencial"}},
                            {"term":{"city":"Sao Paulo"}}
                        ]}},"sort":[{"tuition":"asc"}]}""");
            }
            if (q.contains("data science")) {
                return dsl("""
                        {"query":{"bool":{"filter":[
                            {"term":{"area":"data science"}},
                            {"range":{"durationMonths":{"gte":12,"lte":24}}}
                        ]}}}""");
            }
            return dsl("""
                    {"query":{"match":{"_text_":"artificial intelligence"}}}""");
        }
    }

    /** Available, but throws an unchecked error the planners do not catch. */
    private static class ExplodingParser implements TurNLFacetParser {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TurDslQueryRequest parse(ParseRequest request) {
            throw new IllegalStateException("provider exploded");
        }
    }

    /** No usable LLM at all. */
    private static class UnavailableParser implements TurNLFacetParser {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public TurDslQueryRequest parse(ParseRequest request) {
            throw new TurNLFacetParseException("unavailable");
        }
    }
}

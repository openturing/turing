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

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.viglet.turing.sn.dsl.TurDslQueryRequest;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests the NL→facet eval service end-to-end with a deterministic fake parser
 * (T385 / §XX.5): proves the catalog fixture loads, that a golden parser scores
 * 100%, that a broken/hallucinating parser is caught, and that a parse failure is
 * scored as a case failure rather than aborting the run. No LLM is involved.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurNLFacetEvalServiceTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final TurNLFacetEvalScorer scorer = new TurNLFacetEvalScorer();

    private TurNLFacetEvalPack loadCoursesPack() {
        try (InputStream in = getClass().getResourceAsStream(
                "/nl-facet-eval/courses.nl-facet-eval.json")) {
            assertThat(in).as("fixture present on classpath").isNotNull();
            return MAPPER.readValue(in, TurNLFacetEvalPack.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private TurNLFacetEvalService service(TurNLFacetParser parser) {
        return new TurNLFacetEvalService(parser, scorer, null, null);
    }

    @Test
    void fixtureLoadsWithSchemaAndCases() {
        TurNLFacetEvalPack pack = loadCoursesPack();
        assertThat(pack.name()).isEqualTo("courses-catalog");
        assertThat(pack.fields()).extracting(TurNLFacetField::name)
                .contains("modality", "degree", "tuition", "durationMonths");
        assertThat(pack.cases()).hasSize(4);
    }

    @Test
    void goldenParserScoresEveryCasePass() {
        TurNLFacetEvalReport report = service(new GoldenParser()).run(loadCoursesPack());

        assertThat(report.error()).isNull();
        assertThat(report.passed()).isTrue();
        assertThat(report.caseCount()).isEqualTo(4);
        assertThat(report.passedCount()).isEqualTo(4);
        assertThat(report.score()).isEqualTo(1d);
    }

    @Test
    void hallucinatingParserIsCaught() {
        TurNLFacetEvalReport report = service(new HallucinatingParser()).run(loadCoursesPack());

        assertThat(report.passed()).isFalse();
        assertThat(report.results()).anyMatch(r -> !r.ungroundedFields().isEmpty());
    }

    @Test
    void parseFailureIsScoredAsCaseFailureNotAborted() {
        TurNLFacetEvalReport report = service(new ThrowingParser()).run(loadCoursesPack());

        assertThat(report.passed()).isFalse();
        assertThat(report.caseCount()).isEqualTo(4); // all cases still reported
        assertThat(report.results()).allMatch(r -> r.error() != null);
    }

    @Test
    void noUsableLlmYieldsErrorReport() {
        TurNLFacetParser unavailable = new GoldenParser() {
            @Override
            public boolean isAvailable() {
                return false;
            }
        };
        TurNLFacetEvalReport report = service(unavailable).run(loadCoursesPack());

        assertThat(report.error()).contains("LLM");
        assertThat(report.passed()).isFalse();
    }

    @Test
    void availabilityDelegatesToParser() {
        assertThat(service(new GoldenParser()).isAvailable()).isTrue();
        TurNLFacetParser unavailable = new GoldenParser() {
            @Override
            public boolean isAvailable() {
                return false;
            }
        };
        assertThat(service(unavailable).isAvailable()).isFalse();
    }

    // ─────────────────────────── Fake parsers ───────────────────────────

    private static TurDslQueryRequest dsl(String json) {
        return MAPPER.readValue(json, TurDslQueryRequest.class);
    }

    /** Returns the correct golden DSL for each fixture query. */
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
            if (q.contains("cheapest")) {
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

    /** Always filters on a field outside the schema. */
    private static class HallucinatingParser implements TurNLFacetParser {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TurDslQueryRequest parse(ParseRequest request) {
            return dsl("""
                    {"query":{"bool":{"filter":[{"term":{"scholarship":"true"}}]}}}""");
        }
    }

    /** Available, but throws on every parse (simulates unparseable LLM output). */
    private static class ThrowingParser implements TurNLFacetParser {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TurDslQueryRequest parse(ParseRequest request) {
            throw new TurNLFacetParseException("simulated parse failure");
        }
    }
}

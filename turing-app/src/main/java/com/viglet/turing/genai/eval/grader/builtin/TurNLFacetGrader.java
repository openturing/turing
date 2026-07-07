/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval.grader.builtin;

import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.eval.grader.TurEvalBuiltinGraders;
import com.viglet.turing.genai.eval.grader.TurEvalGrader;
import com.viglet.turing.genai.eval.grader.TurEvalGraderConfigView;
import com.viglet.turing.genai.eval.grader.TurEvalGraderKind;
import com.viglet.turing.genai.eval.grader.TurEvalGraderResult;
import com.viglet.turing.genai.eval.grader.TurEvalGradingContext;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalReport.CaseResult;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalScorer;
import com.viglet.turing.sn.dsl.eval.TurNLFacetExpectation;
import com.viglet.turing.sn.dsl.eval.TurNLFacetField;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * T601 / §XXXIII.16 — brings the Block R NL→facet eval (`sn.dsl.eval`) onto the
 * shared grader model: this CODE grader parses a natural-language {@code query}
 * over a declared {@code schema} (via the {@link TurNLFacetParser} SPI) and
 * scores the structured result against a golden {@code expectation} with the
 * pure {@link TurNLFacetEvalScorer}, so chat-flow and SN evals share one grader
 * registry, one stack model and (with T599) one Studio.
 *
 * <p>Config: {@code query} (required), {@code expectation} + {@code schema}
 * (required, the Block R shapes), optional {@code index} / {@code locale}.
 * Applies only when a parser is available; fails closed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurNLFacetGrader implements TurEvalGrader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurNLFacetEvalScorer scorer;
    private final TurNLFacetParser parser;

    public TurNLFacetGrader(TurNLFacetEvalScorer scorer, TurNLFacetParser parser) {
        this.scorer = scorer;
        this.parser = parser;
    }

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.NL_FACET;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.CODE;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        return config.configString("query") != null && config.has("expectation")
                && config.has("schema") && parser.isAvailable();
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        try {
            TurNLFacetExpectation expectation = OBJECT_MAPPER.convertValue(
                    config.config().get("expectation"), TurNLFacetExpectation.class);
            List<TurNLFacetField> schema = OBJECT_MAPPER.convertValue(
                    config.config().get("schema"), new TypeReference<>() {
                    });
            TurDslQueryRequest actual = parser.parse(new TurNLFacetParser.ParseRequest(
                    config.configString("index"), config.configString("locale"),
                    config.configString("query"), schema));
            String caseName = ctx.evalCase() == null ? graderId() : ctx.evalCase().getName();
            CaseResult result = scorer.score(caseName, expectation, actual, schema);
            String rationale = result.passed() || result.findings().isEmpty()
                    ? null : String.join("; ", result.findings());
            return TurEvalGraderResult.scored(result.score(), result.passed(),
                    result.passed() ? "pass" : "fail", rationale);
        } catch (RuntimeException e) {
            log.warn("[AgentEval] nl-facet grader failed: {}", e.getMessage());
            return TurEvalGraderResult.scored(0d, false, "fail", "nl-facet error: " + e.getMessage());
        }
    }
}

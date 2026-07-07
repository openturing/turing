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
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.eval.grader.builtin.TurGroovyGrader;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;

/**
 * T589 / §XXXIII.4 — the custom Groovy eval grader: return-value interpretation
 * (map / boolean / number), grading-context bindings, and errors-as-fail.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurGroovyGraderTest {

    private final TurGroovyGraderEngine engine = new TurGroovyGraderEngine();

    private static TurEvalGradingContext ctx(String answer, Map<String, String> slots, String outcome) {
        return new TurEvalGradingContext(new TurAgentEvalCase(), "conv-1", List.of(),
                List.of(answer), slots, null, outcome, null);
    }

    private static TurEvalGraderConfigView script(String groovy) {
        return new TurEvalGraderConfigView("groovy", "groovy", "CODE",
                "{\"script\":" + quote(groovy) + "}", 1d, 0d, 1);
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    void mapReturnCarriesScorePassRationale() {
        TurEvalGraderResult r = engine.evaluate(
                "[score: 0.8, pass: true, rationale: 'looks good']", ctx("hi", Map.of(), null));
        assertThat(r.score()).isCloseTo(0.8d, within(1e-9));
        assertThat(r.passed()).isTrue();
        assertThat(r.rationale()).isEqualTo("looks good");
    }

    @Test
    void mapPassDefaultsFromScore() {
        assertThat(engine.evaluate("[score: 1.0]", ctx("x", Map.of(), null)).passed()).isTrue();
        assertThat(engine.evaluate("[score: 0.4]", ctx("x", Map.of(), null)).passed()).isFalse();
    }

    @Test
    void booleanReturnFromBindings() {
        assertThat(engine.evaluate("answer.contains('42')", ctx("total is 42", Map.of(), null))
                .passed()).isTrue();
        assertThat(engine.evaluate("outcome == 'CAPTURED'", ctx("x", Map.of(), "ABANDONED"))
                .passed()).isFalse();
    }

    @Test
    void numberReturnIsScorePassAtFull() {
        assertThat(engine.evaluate("1", ctx("x", Map.of(), null)).passed()).isTrue();
        TurEvalGraderResult half = engine.evaluate("0.5", ctx("x", Map.of(), null));
        assertThat(half.score()).isCloseTo(0.5d, within(1e-9));
        assertThat(half.passed()).isFalse();
    }

    @Test
    void slotBindingIsAvailable() {
        TurEvalGraderResult r = engine.evaluate("slots['plan'] == 'pro'",
                ctx("x", Map.of("plan", "pro"), null));
        assertThat(r.passed()).isTrue();
    }

    @Test
    void scoreIsClampedToUnitInterval() {
        assertThat(engine.evaluate("[score: 5]", ctx("x", Map.of(), null)).score()).isEqualTo(1d);
        assertThat(engine.evaluate("-3", ctx("x", Map.of(), null)).score()).isEqualTo(0d);
    }

    @Test
    void runtimeErrorFailsClosed() {
        TurEvalGraderResult r = engine.evaluate("throw new RuntimeException('boom')",
                ctx("x", Map.of(), null));
        assertThat(r.passed()).isFalse();
        assertThat(r.score()).isEqualTo(0d);
        assertThat(r.rationale()).contains("boom");
    }

    @Test
    void emptyScriptFailsClosed() {
        assertThat(engine.evaluate("   ", ctx("x", Map.of(), null)).passed()).isFalse();
    }

    @Test
    void graderDelegatesToEngineAndGatesOnScript() {
        TurGroovyGrader grader = new TurGroovyGrader(engine);
        TurEvalGradingContext ctx = ctx("hello world", Map.of(), null);
        assertThat(grader.appliesTo(ctx, script("answer.contains('world')"))).isTrue();
        assertThat(grader.grade(ctx, script("answer.contains('world')")).passed()).isTrue();
        assertThat(grader.appliesTo(ctx,
                new TurEvalGraderConfigView("groovy", "g", "CODE", "{}", 1d, 0d, 1))).isFalse();
    }
}

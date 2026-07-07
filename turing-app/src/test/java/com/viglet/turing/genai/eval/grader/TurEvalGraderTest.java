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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.eval.grader.builtin.TurContainsGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurExactMatchGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurJsonPathGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurLatencyBudgetGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurNodeGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurNumericRangeGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurOutcomeGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurRegexGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurRubricJudgeGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurSlotMatchGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurSlotToleranceGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurToolCalledGrader;
import com.viglet.turing.genai.tool.TurChatToolCall;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurAgentEvalSet;
import com.viglet.turing.persistence.model.agent.TurEvalGraderConfig;
import com.viglet.turing.service.chatanalytics.TurToolCallTraceService;

/**
 * T586–T588 / §XXXIII — unit coverage for the grader SPI: registry resolution
 * (default stack / config-driven / per-case override) and the CI-safe CODE
 * graders (dimension wrappers + the T588 config-driven library).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurEvalGraderTest {

    private final TurSlotMatchGrader slot = new TurSlotMatchGrader();
    private final TurOutcomeGrader outcome = new TurOutcomeGrader();
    private final TurNodeGrader node = new TurNodeGrader();

    private static final TurEvalGraderConfigView NO_CFG =
            TurEvalGraderConfigView.defaults("x", TurEvalGraderKind.CODE);

    private static TurEvalGraderConfigView cfg(String json) {
        return new TurEvalGraderConfigView("x", "x", "CODE", json, 1d, 0d, 1);
    }

    private static TurEvalGradingContext ctx(TurAgentEvalCase c, Map<String, String> slots,
            String finalNodeId, String actualOutcome) {
        return new TurEvalGradingContext(c, null, List.of(), List.of(), slots, finalNodeId,
                actualOutcome, null);
    }

    private static TurEvalGradingContext answerCtx(String answer) {
        return new TurEvalGradingContext(new TurAgentEvalCase(), "conv-1", List.of(),
                List.of(answer), Map.of(), null, null, null);
    }

    private static TurEvalGraderConfig config(String graderId, int order, String caseId) {
        TurEvalGraderConfig c = new TurEvalGraderConfig();
        c.setGraderId(graderId);
        c.setSortOrder(order);
        c.setCaseId(caseId);
        c.setEnabled(1);
        return c;
    }

    // ─────────────────── registry resolution ───────────────────

    private TurEvalGraderRegistry registry() {
        // Stack repo null: these fixtures never bind a reusable stack (T600).
        return new TurEvalGraderRegistry(List.of(slot, outcome, node, new TurRubricJudgeGrader()), null);
    }

    @Test
    void registryResolvesLegacyDefaultStackWhenNoConfig() {
        assertThat(registry().resolveStack(null, new TurAgentEvalCase()))
                .extracting(rg -> rg.grader().graderId())
                .containsExactly(TurEvalBuiltinGraders.SLOT_MATCH, TurEvalBuiltinGraders.OUTCOME,
                        TurEvalBuiltinGraders.NODE, TurEvalBuiltinGraders.RUBRIC);
    }

    @Test
    void emptyGraderConfigsFallBackToDefaultStack() {
        assertThat(registry().resolveStack(new TurAgentEvalSet(), new TurAgentEvalCase()))
                .extracting(rg -> rg.grader().graderId())
                .containsExactly(TurEvalBuiltinGraders.SLOT_MATCH, TurEvalBuiltinGraders.OUTCOME,
                        TurEvalBuiltinGraders.NODE, TurEvalBuiltinGraders.RUBRIC);
    }

    @Test
    void setLevelConfigResolvesInSortOrderAndSkipsDisabledAndUnknown() {
        TurAgentEvalSet set = new TurAgentEvalSet();
        set.getGraderConfigs().add(config(TurEvalBuiltinGraders.RUBRIC, 10, null));
        set.getGraderConfigs().add(config(TurEvalBuiltinGraders.SLOT_MATCH, 0, null));
        TurEvalGraderConfig disabled = config(TurEvalBuiltinGraders.NODE, 5, null);
        disabled.setEnabled(0);
        set.getGraderConfigs().add(disabled);
        set.getGraderConfigs().add(config("does-not-exist-yet", 3, null));

        assertThat(registry().resolveStack(set, new TurAgentEvalCase()))
                .extracting(rg -> rg.grader().graderId())
                .containsExactly(TurEvalBuiltinGraders.SLOT_MATCH, TurEvalBuiltinGraders.RUBRIC);
    }

    @Test
    void perCaseOverrideAddsToSetLevelStackForThatCaseOnly() {
        TurAgentEvalSet set = new TurAgentEvalSet();
        set.getGraderConfigs().add(config(TurEvalBuiltinGraders.SLOT_MATCH, 0, null));
        set.getGraderConfigs().add(config(TurEvalBuiltinGraders.NODE, 0, "case-1"));

        TurAgentEvalCase case1 = new TurAgentEvalCase();
        case1.setId("case-1");
        TurAgentEvalCase case2 = new TurAgentEvalCase();
        case2.setId("case-2");

        assertThat(registry().resolveStack(set, case1)).extracting(rg -> rg.grader().graderId())
                .containsExactly(TurEvalBuiltinGraders.SLOT_MATCH, TurEvalBuiltinGraders.NODE);
        assertThat(registry().resolveStack(set, case2)).extracting(rg -> rg.grader().graderId())
                .containsExactly(TurEvalBuiltinGraders.SLOT_MATCH);
    }

    // ─────────────────── dimension CODE graders ───────────────────

    @Test
    void slotGraderScoresPartialMatchWithDetails() {
        TurAgentEvalCase c = new TurAgentEvalCase();
        c.setExpectedSlotsJson("{\"name\":\"Ada\",\"email\":\"a@b.com\"}");
        TurEvalGradingContext ctx = ctx(c, Map.of("name", " Ada ", "email", "wrong@x.com"), null, null);

        assertThat(slot.appliesTo(ctx, NO_CFG)).isTrue();
        TurEvalGraderResult r = slot.grade(ctx, NO_CFG);
        assertThat(r.score()).isEqualTo(0.5d);
        assertThat(r.passed()).isFalse();
        assertThat(r.details()).hasSize(2);
    }

    @Test
    void outcomeGraderMatchesLabel() {
        TurAgentEvalCase c = new TurAgentEvalCase();
        c.setExpectedOutcome(TurAgentEvalExpectedOutcome.CAPTURED);
        assertThat(outcome.grade(ctx(c, Map.of(), null, "CAPTURED"), NO_CFG).passed()).isTrue();
        assertThat(outcome.grade(ctx(c, Map.of(), null, "ABANDONED"), NO_CFG).passed()).isFalse();
        c.setExpectedOutcome(TurAgentEvalExpectedOutcome.ANY);
        assertThat(outcome.appliesTo(ctx(c, Map.of(), null, "CAPTURED"), NO_CFG)).isFalse();
    }

    @Test
    void nodeGraderMatchesCursor() {
        TurAgentEvalCase c = new TurAgentEvalCase();
        c.setExpectedNodeId("collect-email");
        assertThat(node.grade(ctx(c, Map.of(), "collect-email", null), NO_CFG).passed()).isTrue();
        assertThat(node.grade(ctx(c, Map.of(), "welcome", null), NO_CFG).passed()).isFalse();
    }

    // ─────────────────── T588 config-driven library ───────────────────

    @Test
    void exactGrader() {
        TurExactMatchGrader g = new TurExactMatchGrader();
        assertThat(g.grade(answerCtx("  Hello "), cfg("{\"expected\":\"Hello\"}")).passed()).isTrue();
        assertThat(g.grade(answerCtx("hello"), cfg("{\"expected\":\"Hello\"}")).passed()).isFalse();
        assertThat(g.grade(answerCtx("hello"),
                cfg("{\"expected\":\"Hello\",\"ignoreCase\":true}")).passed()).isTrue();
        assertThat(g.appliesTo(answerCtx("x"), cfg("{}"))).isFalse();
    }

    @Test
    void containsGrader() {
        TurContainsGrader g = new TurContainsGrader();
        assertThat(g.grade(answerCtx("the total is 42 dollars"),
                cfg("{\"substring\":\"42 dollars\"}")).passed()).isTrue();
        assertThat(g.grade(answerCtx("nope"), cfg("{\"substring\":\"42\"}")).passed()).isFalse();
    }

    @Test
    void regexGrader() {
        TurRegexGrader g = new TurRegexGrader();
        assertThat(g.grade(answerCtx("order #A1234 shipped"),
                cfg("{\"pattern\":\"#[A-Z]\\\\d+\"}")).passed()).isTrue();
        assertThat(g.grade(answerCtx("no code"), cfg("{\"pattern\":\"#[A-Z]\\\\d+\"}")).passed()).isFalse();
        assertThat(g.grade(answerCtx("x"), cfg("{\"pattern\":\"[\"}")).passed()).isFalse();
    }

    @Test
    void jsonPathGrader() {
        TurJsonPathGrader g = new TurJsonPathGrader();
        String answer = "{\"status\":\"ok\",\"count\":3}";
        assertThat(g.grade(answerCtx(answer), cfg("{\"path\":\"$.status\",\"expected\":\"ok\"}"))
                .passed()).isTrue();
        assertThat(g.grade(answerCtx(answer), cfg("{\"path\":\"$.status\",\"expected\":\"bad\"}"))
                .passed()).isFalse();
        assertThat(g.grade(answerCtx(answer), cfg("{\"path\":\"$.missing\"}")).passed()).isFalse();
        assertThat(g.grade(answerCtx("not json"), cfg("{\"path\":\"$.x\"}")).passed()).isFalse();
    }

    @Test
    void slotToleranceGrader() {
        TurSlotToleranceGrader g = new TurSlotToleranceGrader();
        TurEvalGradingContext ctx = new TurEvalGradingContext(new TurAgentEvalCase(), null,
                List.of(), List.of(), Map.of("price", "10.4"), null, null, null);
        assertThat(g.grade(ctx, cfg("{\"slot\":\"price\",\"expected\":\"10\",\"tolerance\":0.5}"))
                .passed()).isTrue();
        assertThat(g.grade(ctx, cfg("{\"slot\":\"price\",\"expected\":\"10\",\"tolerance\":0.1}"))
                .passed()).isFalse();
        assertThat(g.grade(ctx, cfg("{\"slot\":\"missing\",\"expected\":\"10\"}")).passed()).isFalse();
    }

    @Test
    void numericRangeGrader() {
        TurNumericRangeGrader g = new TurNumericRangeGrader();
        assertThat(g.grade(answerCtx("about 7 items"), cfg("{\"min\":5,\"max\":10}")).passed()).isTrue();
        assertThat(g.grade(answerCtx("about 3 items"), cfg("{\"min\":5}")).passed()).isFalse();
        assertThat(g.grade(answerCtx("no number here"), cfg("{\"max\":10}")).passed()).isFalse();
        assertThat(g.appliesTo(answerCtx("x"), cfg("{}"))).isFalse();
    }

    @Test
    void toolCalledGraderComposesTrace() {
        TurToolCallTraceService trace = new TurToolCallTraceService();
        trace.record("conv-1", List.of(TurChatToolCall.completed("1", "search_kb", "{}", true, 120)));
        TurToolCalledGrader g = new TurToolCalledGrader(trace);

        assertThat(g.grade(answerCtx("x"), cfg("{\"tool\":\"search_kb\"}")).passed()).isTrue();
        assertThat(g.grade(answerCtx("x"), cfg("{\"tool\":\"other\"}")).passed()).isFalse();
        assertThat(g.grade(answerCtx("x"),
                cfg("{\"tool\":\"search_kb\",\"status\":\"error\"}")).passed()).isFalse();
    }

    @Test
    void latencyBudgetGraderComposesTrace() {
        TurToolCallTraceService trace = new TurToolCallTraceService();
        trace.record("conv-1", List.of(
                TurChatToolCall.completed("1", "a", "{}", true, 120),
                TurChatToolCall.completed("2", "b", "{}", true, 300)));
        TurLatencyBudgetGrader g = new TurLatencyBudgetGrader(trace);

        assertThat(g.grade(answerCtx("x"), cfg("{\"maxMs\":500}")).passed()).isTrue();
        assertThat(g.grade(answerCtx("x"), cfg("{\"maxMs\":200}")).passed()).isFalse();
        // max(120,300)=300 <= 400 passes, but sum(420) > 400 fails — aggregate matters
        assertThat(g.grade(answerCtx("x"), cfg("{\"maxMs\":400}")).passed()).isTrue();
        assertThat(g.grade(answerCtx("x"), cfg("{\"maxMs\":400,\"aggregate\":\"sum\"}")).passed())
                .isFalse();
        // no timed calls for this conversation -> budget trivially met
        assertThat(g.grade(new TurEvalGradingContext(new TurAgentEvalCase(), "empty-conv",
                List.of(), List.of("x"), Map.of(), null, null, null),
                cfg("{\"maxMs\":1}")).passed()).isTrue();
    }

    @Test
    void resultFactoriesProduceCanonicalShapes() {
        assertThat(TurEvalGraderResult.binary(true).score()).isEqualTo(1d);
        assertThat(TurEvalGraderResult.binary(false).verdict()).isEqualTo("fail");
        assertThat(TurEvalGraderResult.deferred("later").deferred()).isTrue();
    }
}

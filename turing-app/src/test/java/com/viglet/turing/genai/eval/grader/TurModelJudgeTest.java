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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.viglet.turing.genai.eval.grader.builtin.TurModelJudgeGrader;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;

/**
 * T590 / §XXXIII.5 — MODEL grader strategies: strategy-flavored prompt building,
 * SCORE threshold gating, and fail-closed behaviour. The per-grader-model
 * resolution collaborators aren't exercised here (default path uses the run's
 * judge model), so they're passed as {@code null}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurModelJudgeTest {

    private final TurModelJudgeGrader grader = new TurModelJudgeGrader(null, null, null);

    private static ChatModel modelReturning(String text) {
        ChatModel model = mock(ChatModel.class);
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        when(model.call(any(Prompt.class))).thenReturn(response);
        return model;
    }

    private static TurEvalGradingContext ctx(ChatModel model, String answer) {
        return new TurEvalGradingContext(new TurAgentEvalCase(), "conv-1", List.of("hi"),
                List.of(answer), Map.of(), null, null, model);
    }

    private static TurEvalGraderConfigView cfg(String json) {
        return new TurEvalGraderConfigView("model-judge", "mj", "MODEL", json, 1d, 0d, 1);
    }

    // ─────────────── pure prompt building ───────────────

    @Test
    void systemPromptsAreStrategySpecific() {
        assertThat(TurEvalModelJudge.system(TurEvalModelStrategy.LABEL)).contains("pass or fail");
        assertThat(TurEvalModelJudge.system(TurEvalModelStrategy.SCORE)).contains("SCORE");
        assertThat(TurEvalModelJudge.system(TurEvalModelStrategy.CRITERIA)).contains("CHECKLIST");
        assertThat(TurEvalModelJudge.system(TurEvalModelStrategy.PAIRWISE)).contains("REFERENCE");
    }

    @Test
    void criteriaUserPromptListsChecklist() {
        String user = TurEvalModelJudge.user(TurEvalModelStrategy.CRITERIA, "ctx",
                List.of("is polite", "cites a source"), null, List.of("hi"), "the answer");
        assertThat(user).contains("CHECKLIST:").contains("- is polite").contains("- cites a source")
                .contains("AGENT ANSWER:");
    }

    @Test
    void pairwiseUserPromptCarriesReference() {
        String user = TurEvalModelJudge.user(TurEvalModelStrategy.PAIRWISE, null, null,
                "the golden answer", List.of("hi"), "the answer");
        assertThat(user).contains("REFERENCE:").contains("the golden answer");
    }

    @Test
    void strategyFromDefaultsToScore() {
        assertThat(TurEvalModelStrategy.from(null)).isEqualTo(TurEvalModelStrategy.SCORE);
        assertThat(TurEvalModelStrategy.from("garbage")).isEqualTo(TurEvalModelStrategy.SCORE);
        assertThat(TurEvalModelStrategy.from("label")).isEqualTo(TurEvalModelStrategy.LABEL);
    }

    // ─────────────── grader behaviour ───────────────

    @Test
    void scoreStrategyGatesOnThreshold() {
        ChatModel model = modelReturning("{\"verdict\":\"pass\",\"score\":0.7,\"rationale\":\"ok\"}");
        TurEvalGradingContext ctx = ctx(model, "answer");
        assertThat(grader.grade(ctx, cfg("{\"strategy\":\"SCORE\",\"passThreshold\":0.6}")).passed())
                .isTrue();
        assertThat(grader.grade(ctx, cfg("{\"strategy\":\"SCORE\",\"passThreshold\":0.8}")).passed())
                .isFalse();
    }

    @Test
    void labelStrategyUsesVerdict() {
        ChatModel fail = modelReturning("{\"verdict\":\"fail\",\"score\":0.0}");
        assertThat(grader.grade(ctx(fail, "a"), cfg("{\"strategy\":\"LABEL\"}")).passed()).isFalse();
        ChatModel pass = modelReturning("{\"verdict\":\"pass\",\"score\":1.0}");
        assertThat(grader.grade(ctx(pass, "a"), cfg("{\"strategy\":\"LABEL\"}")).passed()).isTrue();
    }

    @Test
    void appliesOnlyWhenConfiguredAndModelAvailable() {
        ChatModel model = modelReturning("{}");
        assertThat(grader.appliesTo(ctx(model, "a"), cfg("{}"))).isFalse();
        assertThat(grader.appliesTo(ctx(model, "a"), cfg("{\"strategy\":\"SCORE\"}"))).isTrue();
        assertThat(grader.appliesTo(ctx(null, "a"), cfg("{\"strategy\":\"SCORE\"}"))).isFalse();
    }

    @Test
    void usesDatasetReferenceWhenConfigHasNone() {
        ChatModel model = modelReturning("{\"verdict\":\"pass\",\"score\":1.0}");
        // no strategy/prompt/reference in config, but the dataset row supplies a reference (T595)
        TurEvalGradingContext ctx = new TurEvalGradingContext(new TurAgentEvalCase(), "conv-1",
                List.of("hi"), List.of("answer"), Map.of(), null, null, model, "golden");

        assertThat(grader.appliesTo(ctx, cfg("{}"))).isTrue();
        assertThat(grader.grade(ctx, cfg("{\"strategy\":\"PAIRWISE\"}")).passed()).isTrue();
    }

    @Test
    void failsClosedWithoutModel() {
        TurEvalGraderResult r = grader.grade(ctx(null, "a"), cfg("{\"strategy\":\"SCORE\"}"));
        assertThat(r.passed()).isFalse();
        assertThat(r.rationale()).contains("no judge model");
    }

    @Test
    void failsClosedWhenModelThrows() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));
        TurEvalGraderResult r = grader.grade(ctx(model, "a"), cfg("{\"strategy\":\"LABEL\"}"));
        assertThat(r.passed()).isFalse();
        assertThat(r.rationale()).contains("boom");
    }
}

/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * F.7 / §X.8.d — verifies the shared judge prompt + verdict parser (used by both
 * the synchronous and Batch judge paths).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurEvalJudgePromptTest {

    @Test
    void buildUser_embedsRubricAndTurns() {
        String user = TurEvalJudgePrompt.buildUser("be polite", List.of("hi", "bye"));
        assertThat(user).contains("RUBRIC:\nbe polite").contains("USER TURNS:\nhi\nbye");
    }

    @Test
    void parse_passVerdict() {
        TurEvalJudgePrompt.Verdict v = TurEvalJudgePrompt.parse(
                "{\"verdict\":\"pass\",\"score\":0.9,\"rationale\":\"ok\"}");
        assertThat(v.pass()).isTrue();
        assertThat(v.score()).isEqualTo(0.9, within(1e-9));
        assertThat(v.rationale()).isEqualTo("ok");
    }

    @Test
    void parse_stripsProseAroundJson() {
        TurEvalJudgePrompt.Verdict v = TurEvalJudgePrompt.parse(
                "Here is my verdict: {\"verdict\":\"fail\",\"score\":0.1} thanks");
        assertThat(v.pass()).isFalse();
        assertThat(v.score()).isEqualTo(0.1, within(1e-9));
    }

    @Test
    void parse_emptyOrUnparseable_failsClosed() {
        assertThat(TurEvalJudgePrompt.parse("").pass()).isFalse();
        assertThat(TurEvalJudgePrompt.parse(null).pass()).isFalse();
        assertThat(TurEvalJudgePrompt.parse("not json at all").pass()).isFalse();
    }

    @Test
    void parse_clampsScoreAndDefaultsByVerdict() {
        // score out of range is clamped; missing score defaults to verdict.
        assertThat(TurEvalJudgePrompt.parse("{\"verdict\":\"pass\",\"score\":5}").score())
                .isEqualTo(1.0, within(1e-9));
        assertThat(TurEvalJudgePrompt.parse("{\"verdict\":\"pass\"}").score())
                .isEqualTo(1.0, within(1e-9));
        assertThat(TurEvalJudgePrompt.parse("{\"verdict\":\"fail\"}").score())
                .isEqualTo(0.0, within(1e-9));
    }
}

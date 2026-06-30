/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.safety.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.safety.TurModerationService;

/**
 * T516 — the OpenAI moderation guardrail adapter maps a moderation verdict onto
 * the answer-guardrail contract (flags as categories, grounding untouched).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurOpenAiModerationGuardrailTest {

    @Mock
    private TurModerationService moderationService;

    @Test
    void passesOnCleanModeration() {
        when(moderationService.moderateAlways("safe answer"))
                .thenReturn(TurModerationService.Verdict.clean());
        TurOpenAiModerationGuardrail guardrail = new TurOpenAiModerationGuardrail(moderationService);

        TurAnswerGuardrailVerdict verdict = guardrail.evaluate(
                new TurAnswerGuardrailRequest("q", "safe answer", List.of()));

        assertThat(verdict.isClean()).isTrue();
        assertThat(verdict.strategy()).isEqualTo(TurAnswerGuardrailType.OPENAI_MODERATION);
    }

    @Test
    void flagsWithCategoriesWhenModerationFlags() {
        when(moderationService.moderateAlways("bad answer"))
                .thenReturn(new TurModerationService.Verdict(true, List.of("hate", "violence")));
        TurOpenAiModerationGuardrail guardrail = new TurOpenAiModerationGuardrail(moderationService);

        TurAnswerGuardrailVerdict verdict = guardrail.evaluate(
                new TurAnswerGuardrailRequest("q", "bad answer", List.of()));

        assertThat(verdict.action()).isEqualTo(TurAnswerGuardrailAction.FLAG);
        assertThat(verdict.categories()).containsExactly("hate", "violence");
        assertThat(verdict.grounded()).isTrue(); // moderation does not assess grounding
        assertThat(verdict.isClean()).isFalse();
    }
}

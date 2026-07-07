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

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.safety.TurModerationService;

/**
 * T516 / §XXVIII.12 — answer-guardrail adapter that reuses the T182 OpenAI
 * {@code omni-moderation} pre-filter to screen the model <em>answer</em> for
 * unsafe content. Moderation does not assess grounding, so {@code grounded} is
 * always {@code true}; the value is the category flags (hate, violence, …).
 *
 * <p>Fail-open by delegation: {@link TurModerationService#moderate(String)} is
 * itself fully fail-open (disabled / no OpenAI client / API error →
 * {@link TurModerationService.Verdict#clean() clean}), so a flagged verdict here
 * is a real positive. Note this adapter answers regardless of whether the T182
 * <em>query/index</em> moderation toggle is on — the guardrail's own
 * {@code turing.safety.guardrail.*} switch governs it; it only needs the
 * moderation service's OpenAI-client resolution, which {@code moderate} performs
 * internally and which no-ops to clean if the T182 switch is off. For that
 * reason the adapter calls the lower-level OpenAI moderation directly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurOpenAiModerationGuardrail implements TurAnswerGuardrailStrategy {

    private final TurModerationService moderationService;

    public TurOpenAiModerationGuardrail(TurModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @Override
    public TurAnswerGuardrailType getType() {
        return TurAnswerGuardrailType.OPENAI_MODERATION;
    }

    @Override
    public TurAnswerGuardrailVerdict evaluate(TurAnswerGuardrailRequest request) {
        TurModerationService.Verdict verdict = moderationService.moderateAlways(request.answer());
        if (!verdict.flagged()) {
            return TurAnswerGuardrailVerdict.pass(TurAnswerGuardrailType.OPENAI_MODERATION);
        }
        return new TurAnswerGuardrailVerdict(true, null, null,
                TurAnswerGuardrailAction.FLAG, verdict.categories(), null,
                TurAnswerGuardrailType.OPENAI_MODERATION);
    }
}

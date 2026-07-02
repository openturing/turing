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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * T516 / §XXVIII.12 — boots the full Spring context to assert the
 * answer-grounding guardrail seam is wired and <strong>inert by default</strong>
 * (legacy chat path byte-for-byte unchanged): every adapter bean registers, the
 * factory resolves each type (falling back to {@code NONE}), and the facade
 * reports disabled + returns no verdict until {@code turing.safety.guardrail.*}
 * is configured.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAnswerGuardrailIT extends AbstractTuringSpringIT {

    @Autowired
    private TurAnswerGuardrailStrategyFactory strategyFactory;

    @Autowired
    private TurAnswerGuardrailService guardrailService;

    @Test
    void everyStrategyBeanIsRegistered() {
        assertThat(strategyFactory.resolve(TurAnswerGuardrailType.NONE).getType())
                .isEqualTo(TurAnswerGuardrailType.NONE);
        assertThat(strategyFactory.resolve(TurAnswerGuardrailType.BEDROCK).getType())
                .isEqualTo(TurAnswerGuardrailType.BEDROCK);
        assertThat(strategyFactory.resolve(TurAnswerGuardrailType.OPENAI_MODERATION).getType())
                .isEqualTo(TurAnswerGuardrailType.OPENAI_MODERATION);
        assertThat(strategyFactory.resolve(TurAnswerGuardrailType.MISTRAL_MODERATION).getType())
                .isEqualTo(TurAnswerGuardrailType.MISTRAL_MODERATION);
    }

    @Test
    void guardrailIsDisabledAndInertByDefault() {
        assertThat(guardrailService.isEnabled()).isFalse();
        assertThat(guardrailService.evaluate("query", "an answer", List.of("ctx"))).isEmpty();
    }
}

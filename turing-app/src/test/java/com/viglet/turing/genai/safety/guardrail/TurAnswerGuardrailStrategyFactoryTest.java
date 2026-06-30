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

/**
 * T516 — resolution + fallback semantics of {@link TurAnswerGuardrailStrategyFactory}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAnswerGuardrailStrategyFactoryTest {

    private static TurAnswerGuardrailStrategy stub(TurAnswerGuardrailType type) {
        return new TurAnswerGuardrailStrategy() {
            @Override
            public TurAnswerGuardrailType getType() {
                return type;
            }

            @Override
            public TurAnswerGuardrailVerdict evaluate(TurAnswerGuardrailRequest request) {
                return TurAnswerGuardrailVerdict.pass(type);
            }
        };
    }

    @Test
    void resolvesEachRegisteredType() {
        TurAnswerGuardrailStrategy none = stub(TurAnswerGuardrailType.NONE);
        TurAnswerGuardrailStrategy bedrock = stub(TurAnswerGuardrailType.BEDROCK);
        TurAnswerGuardrailStrategyFactory factory =
                new TurAnswerGuardrailStrategyFactory(List.of(none, bedrock));

        assertThat(factory.resolve(TurAnswerGuardrailType.NONE)).isSameAs(none);
        assertThat(factory.resolve(TurAnswerGuardrailType.BEDROCK)).isSameAs(bedrock);
    }

    @Test
    void fallsBackToNoneWhenTypeHasNoImplementation() {
        TurAnswerGuardrailStrategy none = stub(TurAnswerGuardrailType.NONE);
        // MISTRAL_MODERATION is not registered.
        TurAnswerGuardrailStrategyFactory factory =
                new TurAnswerGuardrailStrategyFactory(List.of(none));

        assertThat(factory.resolve(TurAnswerGuardrailType.MISTRAL_MODERATION)).isSameAs(none);
    }
}

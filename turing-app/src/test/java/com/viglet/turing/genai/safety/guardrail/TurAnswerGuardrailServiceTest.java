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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.properties.TurGuardrailProperty;

/**
 * T516 — the fail-open / opt-in contract of {@link TurAnswerGuardrailService}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurAnswerGuardrailServiceTest {

    @Mock
    private TurAnswerGuardrailStrategyFactory strategyFactory;

    private TurGuardrailProperty property;
    private TurAnswerGuardrailService service;

    @BeforeEach
    void setUp() {
        property = new TurGuardrailProperty();
        service = new TurAnswerGuardrailService(property, strategyFactory);
    }

    private void stubStrategy(TurAnswerGuardrailVerdict verdict) {
        when(strategyFactory.resolve(TurAnswerGuardrailType.BEDROCK)).thenReturn(
                new TurAnswerGuardrailStrategy() {
                    @Override
                    public TurAnswerGuardrailType getType() {
                        return TurAnswerGuardrailType.BEDROCK;
                    }

                    @Override
                    public TurAnswerGuardrailVerdict evaluate(TurAnswerGuardrailRequest request) {
                        if (verdict == null) {
                            throw new IllegalStateException("boom");
                        }
                        return verdict;
                    }
                });
    }

    @Test
    void returnsEmptyWhenDisabled() {
        property.setEnabled(false);
        property.setStrategy("BEDROCK");
        assertThat(service.isEnabled()).isFalse();
        assertThat(service.evaluate("q", "a", List.of())).isEmpty();
    }

    @Test
    void returnsEmptyWhenStrategyIsNone() {
        property.setEnabled(true);
        property.setStrategy("NONE");
        assertThat(service.isEnabled()).isFalse();
        assertThat(service.evaluate("q", "a", List.of())).isEmpty();
    }

    @Test
    void returnsEmptyForBlankAnswer() {
        property.setEnabled(true);
        property.setStrategy("BEDROCK");
        assertThat(service.evaluate("q", "  ", List.of())).isEmpty();
    }

    @Test
    void returnsEmptyOnCleanVerdict() {
        property.setEnabled(true);
        property.setStrategy("BEDROCK");
        stubStrategy(TurAnswerGuardrailVerdict.pass(TurAnswerGuardrailType.BEDROCK));
        assertThat(service.evaluate("q", "a grounded answer", List.of("ctx"))).isEmpty();
    }

    @Test
    void surfacesNonCleanVerdict() {
        property.setEnabled(true);
        property.setStrategy("BEDROCK");
        TurAnswerGuardrailVerdict ungrounded = new TurAnswerGuardrailVerdict(false, 0.2, 0.9,
                TurAnswerGuardrailAction.FLAG, List.of("ungrounded"), null,
                TurAnswerGuardrailType.BEDROCK);
        stubStrategy(ungrounded);

        assertThat(service.evaluate("q", "a hallucinated answer", List.of("ctx")))
                .contains(ungrounded);
    }

    @Test
    void failsOpenWhenStrategyThrows() {
        property.setEnabled(true);
        property.setStrategy("BEDROCK");
        stubStrategy(null); // stub throws
        assertThat(service.evaluate("q", "an answer", List.of("ctx"))).isEmpty();
    }
}

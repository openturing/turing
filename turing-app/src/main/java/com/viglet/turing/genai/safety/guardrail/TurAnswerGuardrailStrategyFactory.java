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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * T516 / §XXVIII.12 — resolves the configured {@link TurAnswerGuardrailStrategy},
 * mirroring the {@code TurRagRerankStrategyFactory} pattern (collect
 * implementations by their declared type, route by an enum).
 *
 * <p>Falls back to {@link TurAnswerGuardrailType#NONE} when the requested type
 * has no registered implementation, so a config pointing at an absent adapter
 * degrades to the always-present no-op guardrail instead of throwing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurAnswerGuardrailStrategyFactory {

    private final Map<TurAnswerGuardrailType, TurAnswerGuardrailStrategy> byType =
            new EnumMap<>(TurAnswerGuardrailType.class);

    public TurAnswerGuardrailStrategyFactory(List<TurAnswerGuardrailStrategy> strategies) {
        for (TurAnswerGuardrailStrategy strategy : strategies) {
            byType.put(strategy.getType(), strategy);
        }
        log.info("[Guardrail] answer-guardrail strategies registered: {}", byType.keySet());
    }

    /**
     * @return the strategy for {@code type}, or the {@code NONE} strategy when
     *         {@code type} has no registered implementation.
     */
    public TurAnswerGuardrailStrategy resolve(TurAnswerGuardrailType type) {
        TurAnswerGuardrailStrategy strategy = byType.get(type);
        if (strategy == null) {
            log.warn("[Guardrail] no answer-guardrail strategy for {}; falling back to NONE", type);
            strategy = byType.get(TurAnswerGuardrailType.NONE);
        }
        return strategy;
    }
}

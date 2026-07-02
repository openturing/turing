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

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.properties.TurGuardrailProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * T516 / §XXVIII.12 — the single fail-open boundary in front of the pluggable
 * {@link TurAnswerGuardrailStrategy} backends, mirroring the Block N
 * {@link com.viglet.turing.genai.rag.TurRagReranker} facade.
 *
 * <p>Strictly opt-in: when {@code turing.safety.guardrail.enabled=false}
 * (default) or the configured strategy resolves to {@code NONE}, {@link #evaluate}
 * returns {@link Optional#empty()} so the chat path is byte-for-byte unchanged —
 * no extra call, no extra SSE event.
 *
 * <p>Fully fail-open: a strategy that throws, returns {@code null}, or returns a
 * clean PASS verdict yields {@code Optional.empty()} too, so a guardrail outage
 * can never block a legitimate answer — it just means "no verdict surfaced".
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurAnswerGuardrailService {

    private final TurGuardrailProperty guardrailProperty;
    private final TurAnswerGuardrailStrategyFactory strategyFactory;

    public TurAnswerGuardrailService(TurGuardrailProperty guardrailProperty,
            TurAnswerGuardrailStrategyFactory strategyFactory) {
        this.guardrailProperty = guardrailProperty;
        this.strategyFactory = strategyFactory;
    }

    /** Whether the guardrail is enabled and bound to a real (non-{@code NONE}) strategy. */
    public boolean isEnabled() {
        return guardrailProperty.isEnabled()
                && TurAnswerGuardrailType.fromValue(guardrailProperty.getStrategy())
                        != TurAnswerGuardrailType.NONE;
    }

    /** Whether a flagged/ungrounded answer should be blocked rather than delivered. */
    public boolean isBlockOnViolation() {
        return guardrailProperty.isBlockOnViolation();
    }

    /** The replacement text used when {@link #isBlockOnViolation()} blocks an answer. */
    public String getBlockedMessage() {
        return guardrailProperty.getBlockedMessage();
    }

    /**
     * Validates {@code answer} against {@code contextPassages} using the
     * configured strategy. Returns {@link Optional#empty()} when the guardrail is
     * off, the answer is blank, the verdict is clean, or anything fails (fail-open);
     * otherwise the non-clean verdict to surface beside the answer.
     */
    public Optional<TurAnswerGuardrailVerdict> evaluate(String query, String answer,
            List<String> contextPassages) {
        if (!isEnabled() || !StringUtils.hasText(answer)) {
            return Optional.empty();
        }
        TurAnswerGuardrailType type =
                TurAnswerGuardrailType.fromValue(guardrailProperty.getStrategy());
        try {
            TurAnswerGuardrailStrategy strategy = strategyFactory.resolve(type);
            TurAnswerGuardrailVerdict verdict = strategy.evaluate(
                    new TurAnswerGuardrailRequest(query, answer, contextPassages));
            if (verdict == null || verdict.isClean()) {
                return Optional.empty();
            }
            return Optional.of(verdict);
        } catch (RuntimeException e) {
            log.warn("[Guardrail] strategy {} failed ({}); delivering answer unchanged",
                    type, e.getMessage());
            return Optional.empty();
        }
    }
}

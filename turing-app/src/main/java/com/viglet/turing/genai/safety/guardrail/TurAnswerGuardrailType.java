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

import java.util.Locale;

/**
 * T516 / §XXVIII.12 — selectable answer-grounding guardrail backend.
 *
 * <ul>
 *   <li>{@link #NONE} — no guardrail (default). Legacy behaviour: the answer
 *       streams exactly as the model produced it, no extra event.</li>
 *   <li>{@link #BEDROCK} — AWS Bedrock Guardrails {@code ApplyGuardrail}:
 *       contextual-grounding (is the answer grounded in the retrieved context?)
 *       + relevance + PII redaction, in one managed call.</li>
 *   <li>{@link #OPENAI_MODERATION} — wraps the existing T182 OpenAI
 *       {@code omni-moderation} pre-filter to also screen the <em>answer</em>
 *       (category flags only, no grounding signal).</li>
 *   <li>{@link #MISTRAL_MODERATION} — Mistral {@code mistral-moderation-latest}
 *       classification of the answer (category flags only, no grounding).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurAnswerGuardrailType {
    NONE,
    BEDROCK,
    OPENAI_MODERATION,
    MISTRAL_MODERATION;

    /**
     * Lenient parse used wherever a stored/config string is turned into a type.
     * Unknown, blank, or {@code null} values fall back to {@link #NONE} (the
     * legacy default), so a corrupt config row can never break the chat path —
     * it just disables the guardrail.
     */
    public static TurAnswerGuardrailType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}

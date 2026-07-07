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

/**
 * T516 / §XXVIII.12 — the outcome of an answer-grounding guardrail check, in a
 * provider-agnostic shape so every adapter (Bedrock, OpenAI/Mistral moderation)
 * maps onto one contract the chat client renders identically — a confidence
 * badge beside the answer.
 *
 * @param grounded        {@code true} when the answer is grounded in the
 *                        retrieved context (or grounding was not assessed); only
 *                        the Bedrock contextual-grounding adapter sets this
 *                        {@code false}
 * @param groundingScore  the contextual-grounding confidence in {@code [0,1]}
 *                        when assessed, else {@code null}
 * @param relevanceScore  the answer↔query relevance confidence in {@code [0,1]}
 *                        when assessed, else {@code null}
 * @param action          the recommended action (PASS / FLAG / BLOCK)
 * @param categories      flagged content/PII categories (e.g. {@code "hate"},
 *                        {@code "EMAIL"}), empty when clean
 * @param redactedAnswer  the answer with PII masked when the guardrail produced
 *                        a redacted variant, else {@code null} (deliver the
 *                        original)
 * @param strategy        the {@link TurAnswerGuardrailType} that produced this
 *                        verdict, for the UI/telemetry
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurAnswerGuardrailVerdict(
        boolean grounded,
        Double groundingScore,
        Double relevanceScore,
        TurAnswerGuardrailAction action,
        List<String> categories,
        String redactedAnswer,
        TurAnswerGuardrailType strategy) {

    public TurAnswerGuardrailVerdict {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }

    /** A clean, grounded PASS verdict (nothing to flag) for {@code strategy}. */
    public static TurAnswerGuardrailVerdict pass(TurAnswerGuardrailType strategy) {
        return new TurAnswerGuardrailVerdict(true, null, null,
                TurAnswerGuardrailAction.PASS, List.of(), null, strategy);
    }

    /** Whether this verdict carries any signal worth surfacing to the client. */
    public boolean isClean() {
        return action == TurAnswerGuardrailAction.PASS && grounded && categories.isEmpty();
    }

    /** Returns a copy with {@code action} replaced (e.g. escalated to BLOCK). */
    public TurAnswerGuardrailVerdict withAction(TurAnswerGuardrailAction newAction) {
        return new TurAnswerGuardrailVerdict(grounded, groundingScore, relevanceScore,
                newAction, categories, redactedAnswer, strategy);
    }
}

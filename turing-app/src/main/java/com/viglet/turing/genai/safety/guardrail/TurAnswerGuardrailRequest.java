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
 * T516 / §XXVIII.12 — the inputs a {@link TurAnswerGuardrailStrategy} evaluates:
 * the user {@code query}, the model {@code answer} to check, and the
 * {@code contextPassages} the answer was supposed to be grounded in (the
 * retrieved RAG chunks, possibly empty when the turn ran without retrieval).
 *
 * <p>Moderation-only strategies ({@code OPENAI_MODERATION} /
 * {@code MISTRAL_MODERATION}) read only {@code answer}; contextual-grounding
 * strategies ({@code BEDROCK}) also read {@code query} and
 * {@code contextPassages}.
 *
 * @param query           the user query the answer responds to (may be blank)
 * @param answer          the model answer to validate (never blank when invoked)
 * @param contextPassages the retrieved grounding passages (never {@code null},
 *                        may be empty)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurAnswerGuardrailRequest(String query, String answer,
        List<String> contextPassages) {

    public TurAnswerGuardrailRequest {
        contextPassages = contextPassages == null ? List.of() : List.copyOf(contextPassages);
    }

    /** Whether any grounding context is available to check the answer against. */
    public boolean hasContext() {
        return !contextPassages.isEmpty();
    }

    /** The grounding passages joined into a single source block (one per line). */
    public String joinedContext() {
        return String.join("\n\n", contextPassages);
    }
}

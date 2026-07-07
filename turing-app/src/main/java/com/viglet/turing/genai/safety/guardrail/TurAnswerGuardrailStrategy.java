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

/**
 * T516 / §XXVIII.12 — a selectable answer-grounding guardrail backend.
 *
 * <p>Each implementation validates a model answer (optionally against the
 * retrieved context) and returns a {@link TurAnswerGuardrailVerdict}. The
 * {@link TurAnswerGuardrailService} facade is the single fail-open boundary:
 * a strategy is free to throw or return {@code null} and the facade degrades to
 * "no verdict" (the answer streams unchanged), so strategies do not need their
 * own fallback bookkeeping — mirroring the Block N
 * {@link com.viglet.turing.genai.rag.rerank.TurRagRerankStrategy} pattern.
 *
 * <p>Implementations are discovered by Spring and indexed by {@link #getType()};
 * see {@link TurAnswerGuardrailStrategyFactory}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurAnswerGuardrailStrategy {

    /** The type this strategy serves; used by the factory to route. */
    TurAnswerGuardrailType getType();

    /**
     * Validates {@code request.answer()} (optionally against
     * {@code request.contextPassages()}).
     *
     * @return the verdict, or {@code null} when this strategy cannot produce a
     *         usable signal (the facade then emits no verdict). May throw — the
     *         facade treats any exception as "no verdict" (fail-open).
     */
    TurAnswerGuardrailVerdict evaluate(TurAnswerGuardrailRequest request);
}

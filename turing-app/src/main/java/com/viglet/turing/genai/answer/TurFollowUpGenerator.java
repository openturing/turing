/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.answer;

import java.util.List;

import com.viglet.turing.genai.citation.TurAnswerCitation;

/**
 * T834 / §LXII.3 — the seam {@link TurAnswerStreamFramer} calls, once the answer
 * has finished streaming, to obtain the 3–5 follow-up questions for the
 * {@code followups} SSE event. Kept as a function (not a hard service dependency)
 * so the framer stays a pure, LLM-free transform: production wires it to
 * {@link TurFollowUpQuestionService}; tests pass a stub.
 *
 * <p>Implementations must be fail-open — return an empty list rather than throw —
 * so a follow-up failure degrades to no chips, never a broken answer stream.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@FunctionalInterface
public interface TurFollowUpGenerator {

    /** A generator that never produces chips (the framer's default). */
    TurFollowUpGenerator NONE = (answer, citations) -> List.of();

    /**
     * @param answer    the synthesized answer text
     * @param citations the answer's numbered citations (may be empty)
     * @return 3–5 follow-up questions, or an empty list when unavailable
     */
    List<String> generate(String answer, List<TurAnswerCitation> citations);
}

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

/**
 * T833 / §LXII.2 + T839 / §LXII.8 — payload of the answer stream's {@code trace}
 * SSE event: the auditable "why this answer" provenance. It reports what the answer
 * engine did — how many sources grounded the answer, how long the answer ran, the
 * knowledge-base mode, and the rerank strategy (when reranking was on) — the
 * signals the {@code TuringAnswerProvenance} panel surfaces, alongside the
 * per-citation {@code rankingExplanation} already carried on the {@code citations}
 * event. T843 (§LXII.12) will fold in the live tool-call timeline.
 *
 * @param sourceCount    number of distinct sources that grounded the answer
 * @param answerLength   character length of the synthesized answer text
 * @param kbMode         the site's {@code TurSNKnowledgeBaseMode} (VECTOR /
 *                       VECTORLESS_STRUCTURED / HYBRID), or {@code null}
 * @param rerankStrategy the rerank strategy applied before synthesis, or
 *                       {@code null} when reranking was off
 * @param rerankEnabled  whether the SN RAG reranker ran for this answer
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurAnswerTraceEvent(
        int sourceCount,
        int answerLength,
        String kbMode,
        String rerankStrategy,
        boolean rerankEnabled) {
}

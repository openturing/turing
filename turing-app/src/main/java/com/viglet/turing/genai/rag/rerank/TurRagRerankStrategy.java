/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.rerank;

import java.util.List;

import org.springframework.ai.document.Document;

/**
 * T337 / §XVII.1 — a selectable reranking backend for the SN RAG path.
 *
 * <p>Each implementation re-orders the request's candidates by query relevance
 * and returns the most relevant first. The
 * {@link com.viglet.turing.genai.rag.TurRagReranker} facade is the
 * single fail-open boundary: a strategy is free to throw or return an empty list
 * to signal "no usable signal" and the facade falls back to retrieval order.
 * Strategies therefore do <em>not</em> need their own fallback bookkeeping.
 *
 * <p>Implementations are discovered by Spring and indexed by {@link #getType()};
 * see {@link TurRagRerankStrategyFactory}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurRagRerankStrategy {

    /** The type this strategy serves; used by the factory to route. */
    TurRagRerankStrategyType getType();

    /**
     * Re-orders {@code request.candidates()} by relevance to
     * {@code request.query()}, returning at most {@code request.topK()}
     * documents, most relevant first.
     *
     * @return the reranked top-k, or an empty list when this strategy cannot
     *         produce a usable ranking (the facade then keeps retrieval order).
     *         May throw — the facade treats any exception as "keep retrieval
     *         order".
     */
    List<Document> rerank(TurRagRerankRequest request);
}

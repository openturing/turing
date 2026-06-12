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

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;

/**
 * T338 / §XVII.2 — maps a server-returned ranking (ordered 0-based indices)
 * back to {@link Document}s, capped at {@code topK}. Shared by the HTTP-backed
 * strategies ({@code CROSS_ENCODER}, {@code COHERE}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
final class TurRerankIndexMapper {

    private TurRerankIndexMapper() {
    }

    /**
     * @param candidates the original candidate list (index space the order
     *                   refers to)
     * @param order      ordered, in-bounds, deduped 0-based indices
     * @param topK       cap on the returned size
     * @return the reranked documents, most relevant first; empty when
     *         {@code order} is empty (facade then keeps retrieval order)
     */
    static List<Document> map(List<Document> candidates, List<Integer> order, int topK) {
        if (order == null || order.isEmpty()) {
            return List.of();
        }
        List<Document> reranked = new ArrayList<>(Math.min(topK, order.size()));
        for (Integer idx : order) {
            reranked.add(candidates.get(idx));
            if (reranked.size() == topK) {
                break;
            }
        }
        return reranked;
    }
}

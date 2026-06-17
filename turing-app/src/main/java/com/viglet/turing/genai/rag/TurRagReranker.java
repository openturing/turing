/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.rag.rerank.TurRagRerankRequest;
import com.viglet.turing.genai.rag.rerank.TurRagRerankStrategy;
import com.viglet.turing.genai.rag.rerank.TurRagRerankStrategyFactory;
import com.viglet.turing.genai.rag.rerank.TurRagRerankStrategyType;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * T328 / §XVII — optional reranker for the SN RAG path; the single fail-open
 * boundary in front of the pluggable {@link TurRagRerankStrategy} backends
 * (T337).
 *
 * <p>The hybrid retriever orders candidates by vector/BM25 score, which is a
 * coarse proxy for "answers the question". When the relevance gate alone is too
 * blunt, this stage re-orders the top-N candidates and keeps the top-k for
 * stuffing — fewer, higher-precision chunks in the prompt.
 *
 * <p><b>Strategy-selectable (T337).</b> Which backend runs is resolved from
 * Global Settings ({@code GLOBAL_RAG_SN_RERANK_STRATEGY}, default {@code LLM}):
 * the legacy LLM-as-reranker, a self-hosted cross-encoder, or managed Cohere.
 *
 * <p><b>Fail-open, centrally.</b> On <em>any</em> failure — no chat model, a
 * strategy exception, an empty/garbled response, fewer candidates than we'd keep
 * anyway — the original retrieval order's top-k is returned unchanged, so
 * enabling the reranker (with any strategy) can never make retrieval worse than
 * the gate-only path. Every present and future strategy inherits this guarantee.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurRagReranker {

    private final TurRagRerankStrategyFactory strategyFactory;
    private final TurGlobalSettingsService globalSettingsService;

    public TurRagReranker(TurRagRerankStrategyFactory strategyFactory,
            TurGlobalSettingsService globalSettingsService) {
        this.strategyFactory = strategyFactory;
        this.globalSettingsService = globalSettingsService;
    }

    /**
     * Re-orders {@code candidates} by query relevance and returns the top
     * {@code topK}. Returns the input's first {@code topK} (retrieval order)
     * unchanged when reranking can't run or adds no signal.
     *
     * @param chatModel  the model used by the LLM strategy; ignored by the HTTP
     *                   strategies (may be {@code null} for those)
     * @param query      the user query the chunks should answer
     * @param candidates the retrieved candidate chunks, in retrieval order
     * @param topK       how many chunks to keep for stuffing
     */
    public List<Document> rerank(ChatModel chatModel, String query,
            List<Document> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return candidates == null ? List.of() : candidates;
        }
        List<Document> firstK = candidates.size() > topK
                ? new ArrayList<>(candidates.subList(0, topK))
                : new ArrayList<>(candidates);
        // Nothing to re-order: fewer candidates than we'd keep anyway.
        if (candidates.size() <= topK || query == null || query.isBlank()) {
            return firstK;
        }
        TurRagRerankStrategyType type = globalSettingsService.getRagSnRerankStrategy();
        try {
            TurRagRerankStrategy strategy = strategyFactory.resolve(type);
            List<Document> reranked = strategy.rerank(
                    new TurRagRerankRequest(query, candidates, topK, chatModel));
            if (reranked == null || reranked.isEmpty()) {
                return firstK;
            }
            // Defensive truncation: the facade owns the top-k contract.
            return reranked.size() > topK
                    ? new ArrayList<>(reranked.subList(0, topK))
                    : reranked;
        } catch (RuntimeException e) {
            log.warn("[RAG] reranker strategy {} failed ({}); keeping retrieval order",
                    type, e.getMessage());
            return firstK;
        }
    }

    /**
     * T389 / §XX.9 — re-orders an <em>entire</em> candidate list by query
     * relevance, keeping every input document (no top-k truncation). This is the
     * variant the public Semantic Navigation pipeline needs: it reorders a result
     * page rather than selecting a few chunks for prompt stuffing, so dropping
     * documents is not an option.
     *
     * <p>Reuses the exact same strategy seam and Global-Settings selection as
     * {@link #rerank}, and inherits the same fail-open guarantee: on no chat model
     * for the LLM strategy, a strategy exception, or an empty/garbled response the
     * input list is returned <strong>unchanged</strong>. Documents the strategy
     * ranked are placed first in its order; any it omitted keep their original
     * relative order at the tail, so the result is always a permutation of the
     * input.
     *
     * @param chatModel  model for the LLM strategy; ignored (may be {@code null})
     *                   by the HTTP strategies (cross-encoder, Cohere)
     * @param query      the user query the documents should answer
     * @param candidates the documents to re-order (retrieval/fused order)
     * @return a re-ordered permutation of {@code candidates}, or {@code candidates}
     *         unchanged when reranking can't run or adds no signal
     */
    public List<Document> reorder(ChatModel chatModel, String query, List<Document> candidates) {
        if (candidates == null || candidates.size() < 2 || query == null || query.isBlank()) {
            return candidates == null ? List.of() : candidates;
        }
        TurRagRerankStrategyType type = globalSettingsService.getRagSnRerankStrategy();
        try {
            TurRagRerankStrategy strategy = strategyFactory.resolve(type);
            // topK == size: ask the strategy to rank the whole list, not a subset.
            List<Document> reranked = strategy.rerank(
                    new TurRagRerankRequest(query, candidates, candidates.size(), chatModel));
            if (reranked == null || reranked.isEmpty()) {
                return candidates;
            }
            // Re-attach any document the strategy dropped (off-topic omissions),
            // in original order, so the page never loses a result.
            List<Document> ordered = new ArrayList<>(candidates.size());
            Set<String> seen = new HashSet<>();
            for (Document d : reranked) {
                if (d != null && d.getId() != null && seen.add(d.getId())) {
                    ordered.add(d);
                }
            }
            for (Document d : candidates) {
                if (d != null && d.getId() != null && seen.add(d.getId())) {
                    ordered.add(d);
                }
            }
            return ordered.size() == candidates.size() ? ordered : candidates;
        } catch (RuntimeException e) {
            log.warn("[RAG] reranker strategy {} reorder failed ({}); keeping input order",
                    type, e.getMessage());
            return candidates;
        }
    }
}

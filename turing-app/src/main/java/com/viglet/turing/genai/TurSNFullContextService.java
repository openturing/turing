/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import java.util.List;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * T500 / §X.19 — full-context (retrieval-free) answering for small SN sites.
 *
 * <p>RAG assumes you must retrieve top-K passages because the corpus can't fit
 * the context window. For a <em>small</em> site on a 1–2 M-token model (Gemini),
 * that assumption inverts: stuff the <b>whole</b> corpus into context and let the
 * model ground over everything — recall is 100% by construction, no chunk is ever
 * missed by a retriever, and (paired with the T499 context cache) the corpus is
 * billed once and reused across turns.
 *
 * <p>The gate is purely a token budget. This service probes the site's vector
 * store for the entire collection (a high-{@code topK}, zero-threshold search —
 * the existing {@link VectorStore} API, no new "scan-all" plumbing), estimates
 * its size with the canonical {@code chars/4} heuristic
 * ({@link TurTokenBudgetService}'s {@code estimateTokens}), and engages full-context
 * only when it comfortably fits the per-site budget. Otherwise it declines and the
 * caller proceeds on the normal top-K retrieval path (and the T383 ranking mode)
 * unchanged.
 *
 * <p>Strictly opt-in ({@link TurGenAiContext}'s {@code isFullContextEnabled()}, default off)
 * and <b>fail-open</b>: a probe error, a cap hit, or an over-budget corpus all
 * yield {@link Optional#empty()} so a turn never fails because of this mode.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurSNFullContextService {

    /**
     * Upper bound on chunks pulled in one probe. Doubles as a guard: if the
     * collection returns this many, we can't be sure we have the whole corpus, so
     * full-context declines (the site is not "small"). 10 000 chunks ≈ comfortably
     * more than an 800 K-token corpus of typical chunk sizes.
     */
    static final int PROBE_TOPK = 10_000;

    /** No relevance filter — full-context wants every chunk, not the closest ones. */
    static final double NO_THRESHOLD = 0.0;

    /**
     * The full-context decision for a site/turn — exposed for logging and for a
     * head-to-head measurement against the T383 HYBRID_RRF retrieval path.
     *
     * @param fullContext    true when the whole corpus is used (retrieval-free)
     * @param docCount       chunks the probe returned
     * @param estimatedTokens {@code chars/4} estimate of the corpus text
     * @param budget         the per-site token budget that gated the decision
     * @param reason         a short machine-stable reason code
     */
    public record FullContextDecision(boolean fullContext, int docCount, int estimatedTokens,
            int budget, String reason) {
    }

    /**
     * Engage full-context when enabled and the corpus fits: returns every chunk
     * (caller grounds over all of them, skipping top-K + rerank), else empty.
     */
    public Optional<List<Document>> tryFullContext(TurGenAiContext context, String query) {
        Probe probe = probe(context, query);
        if (probe.decision().fullContext()) {
            log.info("[T500] full-context engaged: grounding over entire corpus "
                    + "(~{} tokens, {} chunks, budget {})", probe.decision().estimatedTokens(),
                    probe.decision().docCount(), probe.decision().budget());
            return Optional.of(probe.corpus());
        }
        return Optional.empty();
    }

    /**
     * The decision without serving the corpus — for observability and the
     * head-to-head measurement vs T383 (does this site qualify, and how big is it?).
     */
    public FullContextDecision decide(TurGenAiContext context, String query) {
        return probe(context, query).decision();
    }

    private Probe probe(TurGenAiContext context, String query) {
        if (context == null || !context.isFullContextEnabled()) {
            return new Probe(decision(false, 0, 0, 0, "disabled"), List.of());
        }
        VectorStore store = context.getVectorStore();
        if (store == null) {
            return new Probe(decision(false, 0, 0, context.getFullContextTokenBudget(),
                    "no-store"), List.of());
        }
        int budget = context.getFullContextTokenBudget();
        try {
            List<Document> corpus = store.similaritySearch(SearchRequest.builder()
                    .query(query == null ? "" : query)
                    .topK(PROBE_TOPK)
                    .similarityThreshold(NO_THRESHOLD)
                    .build());
            if (corpus == null) {
                corpus = List.of();
            }
            if (corpus.size() >= PROBE_TOPK) {
                // Hit the cap — likely a large corpus; we can't guarantee completeness.
                return new Probe(decision(false, corpus.size(), 0, budget, "probe-cap"), List.of());
            }
            int tokens = estimateCorpusTokens(corpus);
            if (tokens > budget) {
                return new Probe(decision(false, corpus.size(), tokens, budget, "over-budget"),
                        List.of());
            }
            return new Probe(decision(true, corpus.size(), tokens, budget, "fits"), corpus);
        } catch (RuntimeException e) {
            log.warn("[T500] full-context probe failed ({}) — falling back to top-K retrieval",
                    e.getMessage());
            return new Probe(decision(false, 0, 0, budget, "probe-error"), List.of());
        }
    }

    /** {@code chars/4} over every chunk's text — the canonical budget heuristic. */
    static int estimateCorpusTokens(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return 0;
        }
        long chars = 0;
        for (Document doc : docs) {
            String text = doc == null ? null : doc.getText();
            if (text != null) {
                chars += text.length();
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, (chars + 3L) / 4L);
    }

    private static FullContextDecision decision(boolean fullContext, int docCount,
            int estimatedTokens, int budget, String reason) {
        return new FullContextDecision(fullContext, docCount, estimatedTokens, budget, reason);
    }

    /** Internal pairing of a decision with the corpus it (optionally) fetched. */
    private record Probe(FullContextDecision decision, List<Document> corpus) {
    }
}

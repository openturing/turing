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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.document.Document;

import com.viglet.turing.genai.provider.store.lucene.TurLuceneVectorStore;

/**
 * T24b / §III.2 — Reciprocal Rank Fusion shared by both hybrid retrieval
 * paths (embedded Lucene single-index AND SE-backed per-locale cores).
 *
 * <p>Extracted out of {@code TurLuceneVectorStore.hybridSearch} so the
 * SE-routed RAG path can reuse the exact fusion semantics without
 * back-channeling through the vector store.
 *
 * <h2>Algorithm</h2>
 *
 * For each document {@code d} that appears in either {@code vectorHits}
 * or {@code bm25Hits}:
 * <pre>
 *   rrf_score(d) = Σ 1 / (K + rank_i(d))
 * </pre>
 * where {@code rank_i(d)} is {@code d}'s 1-based rank in the i-th list
 * and {@code K} is {@link #RRF_K} (60 — canonical default from Cormack
 * et al., shipped by Solr/ES native hybrid query implementations).
 *
 * <h2>Keyword-only tagging</h2>
 *
 * Documents that appear in {@code bm25Hits} but NOT in {@code vectorHits}
 * retain (or get tagged with) {@link TurLuceneVectorStore#METADATA_KEYWORD_ONLY_FALLBACK}{@code =true}.
 * Documents that appear in the vector pass have the tag REMOVED — vector
 * confirmed the match, so the prompt builder can trust it without the
 * keyword-only warning.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public final class TurRagRrf {

    /**
     * Canonical RRF constant. Smaller {@code K} sharpens the contribution
     * of top-ranked docs; larger flattens. 60 matches the original paper
     * and SE-native hybrid implementations.
     */
    public static final int RRF_K = 60;

    private TurRagRrf() {
        // Utility class — no instances.
    }

    /**
     * Fuses two ranked lists via Reciprocal Rank Fusion. See class
     * Javadoc for the algorithm and tagging semantics.
     *
     * @param vectorHits docs ranked by vector similarity (top first)
     * @param bm25Hits   docs ranked by BM25 score (top first); when a doc
     *                   appears here but NOT in {@code vectorHits} it
     *                   keeps the keyword-only tag in the fused output
     * @param topK       max docs to return after fusion
     * @return up to {@code topK} {@link Document}s sorted by descending
     *         RRF score, with corrected keyword-only metadata tags
     */
    public static List<Document> fuse(List<Document> vectorHits, List<Document> bm25Hits, int topK) {
        if (topK < 1) {
            return List.of();
        }
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, Document> sourceDocs = new LinkedHashMap<>();
        Set<String> vectorIds = HashSet.newHashSet(vectorHits == null ? 0 : vectorHits.size());

        if (vectorHits != null) {
            for (int i = 0; i < vectorHits.size(); i++) {
                Document d = vectorHits.get(i);
                if (d == null) {
                    continue;
                }
                vectorIds.add(d.getId());
                rrfScores.merge(d.getId(), 1.0 / (RRF_K + i + 1), (a, b) -> a + b);
                sourceDocs.putIfAbsent(d.getId(), d);
            }
        }
        if (bm25Hits != null) {
            for (int i = 0; i < bm25Hits.size(); i++) {
                Document d = bm25Hits.get(i);
                if (d == null) {
                    continue;
                }
                rrfScores.merge(d.getId(), 1.0 / (RRF_K + i + 1), (a, b) -> a + b);
                sourceDocs.putIfAbsent(d.getId(), d);
            }
        }
        if (rrfScores.isEmpty()) {
            return List.of();
        }
        List<Document> fused = new ArrayList<>(Math.min(topK, rrfScores.size()));
        rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .forEach(e -> fused.add(rebuild(sourceDocs.get(e.getKey()), e.getValue(),
                        vectorIds.contains(e.getKey()))));
        return fused;
    }

    /**
     * Copies {@code source} into a fresh {@link Document} carrying the
     * RRF score and the corrected keyword-only tag: removed when the
     * doc appeared in the vector pass (vector confirmed → trust), kept
     * otherwise.
     */
    private static Document rebuild(Document source, double rrfScore, boolean wasInVector) {
        if (source == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>(source.getMetadata());
        if (wasInVector) {
            metadata.remove(TurLuceneVectorStore.METADATA_KEYWORD_ONLY_FALLBACK);
        }
        return Document.builder()
                .id(source.getId())
                .text(source.getText())
                .metadata(metadata)
                .score(rrfScore)
                .build();
    }
}

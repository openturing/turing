/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.sn.genai;

import java.util.Locale;

/**
 * T383 / §XX.3 — selectable ranking mode for the <strong>public</strong>
 * Semantic Navigation search (the faceted catalog results), independent of the
 * RAG hybrid-retrieval flags that drive the chat {@code search_knowledge_base}
 * tool.
 *
 * <ul>
 *   <li>{@link #LEGACY} — the historical behaviour: results are ranked purely
 *       by the search engine's lexical (BM25) score / the site's configured
 *       sort. Default; preserves pre-T383 behaviour exactly, so existing sites
 *       need no revalidation.</li>
 *   <li>{@link #HYBRID_RRF} — opt-in. When the effective sort is relevance, the
 *       lexical (BM25) ranking and a vector (semantic) ranking of the same
 *       documents are fused via Reciprocal Rank Fusion ({@code k=60}, the same
 *       {@link com.viglet.turing.genai.rag.TurRagRrf} the RAG path uses) and the
 *       result page is reordered. Facet counts, pagination and explicit
 *       non-relevance sorts are left untouched — the conservative,
 *       objective-ranking-preserving choice for a public catalog.</li>
 *   <li>{@link #HYBRID_RRF_RERANK} — T389 / §XX.9. The full pluggable
 *       {@code retrieve → fuse → rerank} pipeline: everything {@link #HYBRID_RRF}
 *       does, then the fused page is re-ordered once more by the Block N
 *       {@link com.viglet.turing.genai.rag.TurRagReranker} strategy seam
 *       (LLM | CROSS_ENCODER | COHERE, selected in Global Settings). Like the
 *       rest of the pipeline it is fail-open: a missing/failing reranker leaves
 *       the RRF-fused order in place. Still objective-signal-only — no commercial
 *       boost ever enters the pipeline.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurSNRankingMode {
    LEGACY,
    HYBRID_RRF,
    HYBRID_RRF_RERANK;

    /** Whether this mode needs the per-site vector collection (any hybrid mode). */
    public boolean isHybrid() {
        return this == HYBRID_RRF || this == HYBRID_RRF_RERANK;
    }

    /** Whether this mode runs the optional rerank stage after RRF fusion (T389). */
    public boolean isRerank() {
        return this == HYBRID_RRF_RERANK;
    }

    /**
     * Lenient parse used wherever a stored/config string is turned into a mode.
     * Unknown, blank, or {@code null} values fall back to {@link #LEGACY} (the
     * legacy default), so a corrupt config row can never silently turn hybrid
     * ranking on or throw — it just behaves like the legacy path.
     */
    public static TurSNRankingMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return LEGACY;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return LEGACY;
        }
    }
}

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

import java.util.Locale;

/**
 * T337 / §XVII.1 — selectable reranker backend for the SN RAG path.
 *
 * <ul>
 *   <li>{@link #LLM} — the legacy LLM-as-reranker (one chat call ranks snippet
 *       numbers). Default; preserves T328 behavior exactly.</li>
 *   <li>{@link #CROSS_ENCODER} — a self-hosted cross-encoder over an HTTP
 *       {@code /rerank} endpoint (TEI / Infinity / Jina-compatible).</li>
 *   <li>{@link #COHERE} — the managed Cohere Rerank API.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurRagRerankStrategyType {
    LLM,
    CROSS_ENCODER,
    COHERE;

    /**
     * Lenient parse used everywhere a stored/config string is turned into a
     * type. Unknown, blank, or {@code null} values fall back to {@link #LLM}
     * (the legacy default), so a corrupt config row can never disable reranking
     * in a way that throws — it just behaves like the legacy path.
     */
    public static TurRagRerankStrategyType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return LLM;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return LLM;
        }
    }
}

/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.provider.llm;

import java.util.List;

/**
 * T512 / §XXVIII.8 — capability mix-in for embedding models that embed each
 * chunk <b>together with its surrounding document context</b> (Voyage
 * {@code voyage-context-3}). An implementation always also implements Spring
 * AI's {@link org.springframework.ai.embedding.EmbeddingModel}, so query-time
 * and single-chunk callers are unchanged; this interface adds the
 * "embed all chunks of one document in one call" entry point.
 *
 * <p>The classic RAG failure this attacks: a chunk that is meaningless without
 * the section it was cut from (a bare "It increased 12% year over year." with
 * no idea what "it" is). Contextualized models read the sibling chunks when
 * embedding each one, so the stored vector carries the document context — a
 * retrieval-quality lift with <b>no query-time change</b> (the query is still a
 * plain {@link org.springframework.ai.embedding.EmbeddingModel#embed(String)}).
 *
 * <p>Callers should feature-detect via {@link #of(Object)} before calling
 * {@link #embedDocumentChunks}, because the configured embedding model may be a
 * plain per-chunk model.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurContextualEmbeddingModel {

    /**
     * Embeds every chunk of <b>one document</b> in a single call so each chunk's
     * vector is computed with awareness of its siblings (index-time / document
     * semantics). The returned list is aligned by index with {@code chunks}.
     *
     * @param chunks the ordered chunk texts of a single source document
     * @return one context-aware embedding per chunk, same order as the input
     */
    List<float[]> embedDocumentChunks(List<String> chunks);

    /** Whether this model can currently produce contextualized chunk embeddings. */
    default boolean supportsContextualChunks() {
        return true;
    }

    /**
     * Null-safe feature-detection helper: returns the given object as a
     * {@link TurContextualEmbeddingModel} when it both implements this interface
     * and reports support, otherwise {@code null}. Use this rather than a bare
     * {@code instanceof} so a model behind the resilience wrapper is unwrapped
     * consistently.
     */
    static TurContextualEmbeddingModel of(Object embeddingModel) {
        if (embeddingModel instanceof TurContextualEmbeddingModel contextual
                && contextual.supportsContextualChunks()) {
            return contextual;
        }
        return null;
    }
}

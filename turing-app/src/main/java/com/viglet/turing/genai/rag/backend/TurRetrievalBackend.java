/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.backend;

import java.util.List;

import org.springframework.ai.document.Document;

/**
 * T520 / §XXVIII.16 — a pluggable retrieval backend: an <em>alternative</em> to
 * the built-in vector / hybrid index, so a tenant standardized on a cloud RAG
 * stack can point Turing's retrieval at a managed service (AWS Bedrock Knowledge
 * Bases today; Vertex AI Search / RAG Engine as future adapters) while keeping
 * the agent / chat / citations / eval layers above it unchanged.
 *
 * <p>It is deliberately a <strong>retriever</strong> contract — it returns Spring
 * AI {@link Document}s exactly like the built-in path, so the retrieved passages
 * flow through the existing reranking, prompt-stuffing, {@code sources[]} and
 * native-citation paths verbatim (a Bedrock-KB passage becomes a citation source
 * through the same {@code TurCitationDocument} mapping as a built-in hit). It is
 * NOT a rewrite of the retrieval core: when no backend is configured the built-in
 * path runs untouched (see {@link TurRetrievalBackendResolver}).
 *
 * <p>Each {@code Document} should carry {@code source_id} / {@code url} /
 * {@code title} metadata so {@code TurRagSource} resolves provenance identically
 * to the built-in path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurRetrievalBackend {

    /** The type this backend serves; used by the factory/resolver to route. */
    TurRetrievalBackendType getType();

    /** Whether this backend is configured + usable (e.g. a KB id is set). */
    boolean isAvailable();

    /**
     * Retrieves the top passages for {@code request} as Spring AI documents,
     * carrying provenance metadata. Implementations should be fail-safe: throwing
     * is allowed (the caller logs and degrades), but returning an empty list is
     * preferred when there are simply no hits.
     */
    List<Document> retrieve(TurRetrievalRequest request);
}

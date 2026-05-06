/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

import lombok.extern.slf4j.Slf4j;

/**
 * Pulls the top-K most similar Q/A pairs from a persona's dedicated
 * embedding store, so they can be inlined as few-shot examples in the
 * system prompt.
 *
 * <p>The retriever needs an {@link TurEmbeddingModel} to build the
 * vector-store client — typically the one already configured on the
 * agent (since the persona only carries the store, not the model). When
 * the embedding model is missing or the store is unconfigured, the
 * retriever returns an empty list and logs at debug; the chat path then
 * proceeds without few-shot examples.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Slf4j
@Component
public class TurPersonaFewShotRetriever {

    private static final double SIMILARITY_THRESHOLD = 0.5;
    private static final int DEFAULT_TOP_K = 3;

    private final TurRagContextBuilder ragContextBuilder;

    public TurPersonaFewShotRetriever(TurRagContextBuilder ragContextBuilder) {
        this.ragContextBuilder = ragContextBuilder;
    }

    public List<Document> retrieve(TurPersona persona, TurEmbeddingModel embeddingModel, String query) {
        return retrieve(persona, embeddingModel, query, DEFAULT_TOP_K);
    }

    public List<Document> retrieve(TurPersona persona, TurEmbeddingModel embeddingModel,
            String query, int topK) {
        if (persona == null || persona.getFewShotStore() == null) {
            return Collections.emptyList();
        }
        if (embeddingModel == null) {
            log.debug("[Persona] '{}' has a few-shot store but no embedding model is available.",
                    persona.getName());
            return Collections.emptyList();
        }
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }

        TurStoreInstance store = persona.getFewShotStore();
        Optional<TurRagContextBuilder.RagInfrastructure> infra =
                ragContextBuilder.build(embeddingModel, store, null);
        if (infra.isEmpty()) {
            log.debug("[Persona] '{}' could not build few-shot vector store '{}'.",
                    persona.getName(), store.getId());
            return Collections.emptyList();
        }

        VectorStore vectorStore = infra.get().vectorStore();
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(Math.max(1, topK))
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build();

        try {
            List<Document> docs = vectorStore.similaritySearch(request);
            log.debug("[Persona] '{}' few-shot retrieval: {} hit(s) for query length={}",
                    persona.getName(), docs == null ? 0 : docs.size(),
                    query.length());
            return docs == null ? Collections.emptyList() : docs;
        } catch (Exception e) {
            log.warn("[Persona] '{}' few-shot retrieval failed: {}",
                    persona.getName(), e.getMessage());
            return Collections.emptyList();
        }
    }
}

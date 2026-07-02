/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.multimodal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.genai.TurRagContextBuilder.RagInfrastructure;
import com.viglet.turing.genai.provider.llm.TurMultimodalEmbeddingModel;
import com.viglet.turing.genai.provider.store.TurStoreExportRecord;
import com.viglet.turing.genai.provider.store.TurStoreImportResult;

import lombok.extern.slf4j.Slf4j;

/**
 * T511 / §XXVIII.7 — the "search text → match an image" retrieval surface.
 *
 * <p>Bridges three existing pieces: a {@link TurMultimodalEmbeddingModel}
 * (Voyage {@code voyage-multimodal-3} today), the embedded Lucene KNN vector
 * store, and the indexer write path. At <b>index time</b> a PDF page-image,
 * diagram or screenshot is embedded with {@link TurMultimodalEmbeddingModel#embedImage}
 * and the precomputed vector is upserted into a vector-store collection, tagged
 * {@code _modality=image}. At <b>query time</b> a plain-text query is embedded
 * through the same model (so it lands in the shared text/image space) and KNN
 * matches those image vectors — text query, visual hit.
 *
 * <p>Everything is opt-in and <b>fail-soft</b>: when the configured embedding
 * model is text-only (not multimodal), {@link #isAvailable} returns {@code false}
 * and the index/search calls become no-ops, so legacy text-only RAG is untouched.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurMultimodalRetrievalService {

    /** Metadata key tagging a vector as an embedded image (vs. text chunk). */
    public static final String META_MODALITY = "_modality";
    public static final String MODALITY_IMAGE = "image";
    /** Metadata key carrying the source image's MIME type. */
    public static final String META_MIME_TYPE = "mimeType";

    private final TurRagContextBuilder ragContextBuilder;

    public TurMultimodalRetrievalService(TurRagContextBuilder ragContextBuilder) {
        this.ragContextBuilder = ragContextBuilder;
    }

    /**
     * Whether the given embedding model resolves to a real multimodal model
     * (image embedding supported). When {@code embeddingModelId} is blank, the
     * Global Settings default embedding model is checked.
     */
    public boolean isAvailable(String embeddingModelId, String storeInstanceId) {
        return infra(embeddingModelId, storeInstanceId, null)
                .map(infra -> TurMultimodalEmbeddingModel.of(infra.embeddingModel()) != null)
                .orElse(false);
    }

    /**
     * Embeds {@code imageBytes} into the shared text/image vector space and
     * upserts the precomputed vector into {@code collectionName}, tagged as an
     * image. Re-indexing the same {@code id} overwrites the prior vector.
     *
     * @return {@code true} when the image was embedded and stored; {@code false}
     *         when the model is text-only, the image is empty, or any failure
     *         occurred (logged at WARN — never throws to the caller)
     */
    public boolean indexImage(String embeddingModelId, String storeInstanceId, String collectionName,
            String id, byte[] imageBytes, String mimeType, Map<String, Object> metadata) {
        if (!StringUtils.hasText(id) || imageBytes == null || imageBytes.length == 0) {
            return false;
        }
        Optional<RagInfrastructure> maybeInfra = infra(embeddingModelId, storeInstanceId, collectionName);
        if (maybeInfra.isEmpty()) {
            return false;
        }
        RagInfrastructure infra = maybeInfra.get();
        TurMultimodalEmbeddingModel multimodal = TurMultimodalEmbeddingModel.of(infra.embeddingModel());
        if (multimodal == null) {
            log.debug("[MULTIMODAL] embedding model is text-only — image index of '{}' skipped", id);
            return false;
        }
        try {
            float[] vector = multimodal.embedImage(imageBytes, mimeType);
            if (vector.length == 0) {
                return false;
            }
            Map<String, Object> meta = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
            meta.put(META_MODALITY, MODALITY_IMAGE);
            if (StringUtils.hasText(mimeType)) {
                meta.put(META_MIME_TYPE, mimeType);
            }
            TurStoreExportRecord record = new TurStoreExportRecord(id,
                    asString(meta.get("label")), vector, meta);
            TurStoreImportResult result = infra.storeProvider().importChunks(
                    infra.storeInstance(), infra.storeCredential(), infra.collectionName(),
                    List.of(record), infra.embeddingModel());
            return result != null && result.imported() > 0;
        } catch (RuntimeException e) {
            log.warn("[MULTIMODAL] image index of '{}' into '{}' failed: {}", id, collectionName, e.getMessage());
            return false;
        }
    }

    /**
     * Embeds the plain-text {@code query} through the multimodal model and runs
     * a KNN search, returning the image documents whose visual embedding is
     * closest in the shared space. Results are restricted to image-modality
     * vectors. Fail-soft: returns an empty list on any failure.
     */
    public List<Document> searchImagesByText(String embeddingModelId, String storeInstanceId,
            String collectionName, String query, int topK) {
        if (!StringUtils.hasText(query) || topK < 1) {
            return List.of();
        }
        Optional<RagInfrastructure> maybeInfra = infra(embeddingModelId, storeInstanceId, collectionName);
        if (maybeInfra.isEmpty()) {
            return List.of();
        }
        RagInfrastructure infra = maybeInfra.get();
        if (TurMultimodalEmbeddingModel.of(infra.embeddingModel()) == null) {
            return List.of();
        }
        try {
            // Over-fetch so the image-modality post-filter still yields topK when
            // a collection happens to mix text chunks and image vectors.
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(Math.max(topK * 2, topK))
                    .similarityThreshold(0.0)
                    .build();
            List<Document> hits = infra.vectorStore().similaritySearch(request);
            if (hits == null || hits.isEmpty()) {
                return List.of();
            }
            List<Document> images = new ArrayList<>(Math.min(topK, hits.size()));
            for (Document doc : hits) {
                if (isImage(doc)) {
                    images.add(doc);
                    if (images.size() >= topK) {
                        break;
                    }
                }
            }
            return images;
        } catch (RuntimeException e) {
            log.warn("[MULTIMODAL] text→image search on '{}' failed: {}", collectionName, e.getMessage());
            return List.of();
        }
    }

    private static boolean isImage(Document doc) {
        Object modality = doc.getMetadata() == null ? null : doc.getMetadata().get(META_MODALITY);
        return MODALITY_IMAGE.equals(modality);
    }

    private Optional<RagInfrastructure> infra(String embeddingModelId, String storeInstanceId,
            String collectionName) {
        if (StringUtils.hasText(embeddingModelId) && StringUtils.hasText(storeInstanceId)) {
            return ragContextBuilder.build(embeddingModelId, storeInstanceId, collectionName);
        }
        return ragContextBuilder.buildFromGlobalSettings(collectionName);
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }
}

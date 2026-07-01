/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.provider.llm;

/**
 * T511 / §XXVIII.7 — capability mix-in for embedding models that embed
 * <b>images and text into one shared vector space</b> (Voyage
 * {@code voyage-multimodal-3}, Cohere {@code Embed v4}, Bedrock Titan
 * multimodal). An implementation always also implements Spring AI's
 * {@link org.springframework.ai.embedding.EmbeddingModel}, so the existing
 * text-only callers (index/query) keep working unchanged; this interface only
 * adds the image side.
 *
 * <p>The point of the shared space is the new retrieval surface: a plain-text
 * query embedded via {@link org.springframework.ai.embedding.EmbeddingModel#embed(String)}
 * lands close to a PDF page-image, diagram or screenshot whose pixels were
 * embedded via {@link #embedImage(byte[], String)} — so "search text → match an
 * image" works with the same KNN store the text path already uses.
 *
 * <p>Callers should feature-detect with an {@code instanceof} check (or
 * {@link #of(Object)}) before calling {@link #embedImage}, because the
 * configured embedding model may be text-only.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurMultimodalEmbeddingModel {

    /**
     * Embeds a single image into the shared text/image vector space. The image
     * is treated as a <i>document</i> (index-time semantics), mirroring the
     * asymmetric input-type convention the text path uses.
     *
     * @param imageBytes the raw image bytes (PNG, JPEG, GIF, WebP)
     * @param mimeType   the image MIME type, e.g. {@code image/png}; a blank
     *                   value falls back to {@code image/png}
     * @return the embedding vector, dimensionally compatible with the text
     *         vectors produced by the same model (so both live in one KNN index)
     */
    float[] embedImage(byte[] imageBytes, String mimeType);

    /** Whether this model can currently embed images (always {@code true} for a real multimodal model). */
    default boolean supportsImageEmbedding() {
        return true;
    }

    /**
     * Null-safe feature-detection helper: returns the given object as a
     * {@link TurMultimodalEmbeddingModel} when it both implements this interface
     * and reports image support, otherwise {@code null}. Use this rather than a
     * bare {@code instanceof} so a model that wraps a delegate (the resilience
     * wrapper) is unwrapped consistently.
     */
    static TurMultimodalEmbeddingModel of(Object embeddingModel) {
        if (embeddingModel instanceof TurMultimodalEmbeddingModel multimodal
                && multimodal.supportsImageEmbedding()) {
            return multimodal;
        }
        return null;
    }
}

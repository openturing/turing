/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.batch;

import java.util.List;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * F.7 / §X.8.c — the embeddings-batch seam, separate from the chat
 * {@link TurBatchProvider} because a vendor's chat-batch and embedding-batch
 * support are independent capabilities. OpenAI (Batch {@code /v1/embeddings}) and
 * Gemini ({@code batches.createEmbeddings}, T496) implement it; Anthropic has no
 * embedding API at all.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurBatchEmbeddingProvider {

    String getPluginType();

    /** Submit an embeddings batch and return the vendor batch id. */
    String submitEmbeddings(TurLLMInstance instance, List<TurBatchEmbeddingRequest> requests);

    /** Status of a previously-submitted embeddings batch (same lifecycle as chat). */
    TurBatchStatus status(TurLLMInstance instance, String vendorBatchId);

    /** Fetch all per-chunk embedding vectors of a completed embeddings batch. */
    List<TurBatchEmbeddingResult> embeddingResults(TurLLMInstance instance, String vendorBatchId);
}

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

/**
 * F.7 / §X.8.c — one chunk to (re-)embed in an embeddings batch.
 *
 * @param customId the chunk's stable store id, echoed back on the result so the
 *                 precomputed vector can be written back to the right chunk
 * @param text     the chunk content to embed (unchanged from the store)
 * @param model    the embedding model id (e.g. {@code text-embedding-3-large})
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurBatchEmbeddingRequest(String customId, String text, String model) {
}

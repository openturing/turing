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
 * F.7 / §X.8.c — the embedding vector produced for one {@link TurBatchEmbeddingRequest}.
 *
 * @param customId  echoes the request's chunk id
 * @param success   true when an embedding came back
 * @param embedding the vector when {@link #success()}, else {@code null}
 * @param error     a short error message when not {@link #success()}, else {@code null}
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurBatchEmbeddingResult(String customId, boolean success, float[] embedding, String error) {

    public static TurBatchEmbeddingResult ok(String customId, float[] embedding) {
        return new TurBatchEmbeddingResult(customId, true, embedding, null);
    }

    public static TurBatchEmbeddingResult failed(String customId, String error) {
        return new TurBatchEmbeddingResult(customId, false, null, error);
    }
}

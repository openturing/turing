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

import com.viglet.turing.persistence.model.batch.TurBatchJob;

/**
 * F.7 / §X.8.c — the embeddings analog of {@link TurBatchCompletionHandler}.
 * The poller invokes it once when an {@link TurBatchKind#EMBEDDING} job ends,
 * handing it the per-chunk vectors so it can write them back to the store.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurBatchEmbeddingCompletionHandler {

    /** The {@link TurBatchJob#purpose} value this handler consumes. */
    String purpose();

    void onBatchComplete(TurBatchJob job, List<TurBatchEmbeddingResult> results);
}

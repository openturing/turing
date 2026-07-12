/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.transcription;

/**
 * T715 / §XLII.7 — progress sink for a chunk-aware transcription. The
 * orchestrator ({@link TurTranscriptionService}) calls
 * {@link #onProgress(int, int)} once the chunk plan is known ({@code completed=0})
 * and again after each chunk transcription completes, so an async caller (the
 * persona-from-audio job) can surface a live per-chunk progress bar. A single
 * (passthrough) chunk reports {@code 0/1} then {@code 1/1}.
 *
 * <p>Callbacks fire from the bounded chunk-transcription pool and may arrive
 * out of submission order; {@code completed} is a monotonic count, not an index.
 * Implementations must be cheap and non-blocking.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@FunctionalInterface
public interface TurTranscriptionProgressListener {

    /** A no-op listener for the synchronous callers that don't track progress. */
    TurTranscriptionProgressListener NOOP = (completedChunks, totalChunks) -> {
        // intentionally empty
    };

    /**
     * @param completedChunks chunks transcribed so far (0 when the plan is first known)
     * @param totalChunks     total chunks in the plan (≥ 1)
     */
    void onProgress(int completedChunks, int totalChunks);
}

/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.authoring.persona;

/**
 * T715 / §XLII.7 — lifecycle state of an async persona-from-audio job.
 *
 * <p>{@link #QUEUED} → {@link #TRANSCRIBING} (reports per-chunk progress) →
 * {@link #ANALYZING} → terminal ({@link #SUCCEEDED} / {@link #FAILED}). The SSE
 * stream completes on the first terminal event; the draft rides the
 * {@link #SUCCEEDED} snapshot.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurPersonaAudioJobState {

    /** Accepted, waiting for a free worker. */
    QUEUED,

    /** Transcribing the recording (chunked); {@code completedChunks/totalChunks} advance. */
    TRANSCRIBING,

    /** The LLM is drafting the persona from the finished transcript. */
    ANALYZING,

    /** Finished with a draft persona ready for review. */
    SUCCEEDED,

    /** Finished with an error (fail-soft — the audio is discarded). */
    FAILED;

    /** True for {@link #SUCCEEDED} / {@link #FAILED} — no further transitions. */
    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}

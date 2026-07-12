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
 * T692 / §XLII.6 — lifecycle state of an async transcription job.
 *
 * <p>{@link #QUEUED} → {@link #RUNNING} → terminal ({@link #SUCCEEDED} /
 * {@link #FAILED}). A job never leaves a terminal state; the SSE stream completes
 * on the first terminal event and the job's result is retained for polling until
 * the retention sweep evicts it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurTranscriptionJobState {

    /** Accepted, waiting for a free worker. */
    QUEUED,

    /** A worker is transcribing the audio (possibly chunked). */
    RUNNING,

    /** Finished with a transcript. */
    SUCCEEDED,

    /** Finished with an error (fail-soft — the audio itself is discarded). */
    FAILED;

    /** True for {@link #SUCCEEDED} / {@link #FAILED} — no further transitions. */
    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}

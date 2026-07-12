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
 * T692 / §XLII.6 — an immutable snapshot of an async transcription job, returned
 * by the submit/poll endpoints and pushed over the SSE stream on every state
 * change. Carries the transcript only once {@link TurTranscriptionJobState#SUCCEEDED}
 * (never the audio bytes — those are discarded the moment the worker finishes).
 *
 * @param jobId       opaque id assigned at submit time
 * @param state       lifecycle state
 * @param transcript  stitched transcript when succeeded, else {@code null}
 * @param language    detected/aggregated language when succeeded, else {@code null}
 * @param error       failure message when failed, else {@code null}
 * @param submittedAt epoch millis the job was accepted
 * @param startedAt   epoch millis a worker picked it up, or 0 while queued
 * @param finishedAt  epoch millis it reached a terminal state, or 0 while pending
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurTranscriptionJobStatus(
        String jobId,
        TurTranscriptionJobState state,
        String transcript,
        String language,
        String error,
        long submittedAt,
        long startedAt,
        long finishedAt) {

    /** True once the job has reached a terminal state — the SSE stream ends here. */
    public boolean terminal() {
        return state != null && state.terminal();
    }
}

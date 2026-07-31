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

import java.util.List;

/**
 * T692 / §XLII.6 — an immutable snapshot of an async transcription job, returned
 * by the submit/poll endpoints and pushed over the SSE stream on every state
 * change. Carries the transcript only once {@link TurTranscriptionJobState#SUCCEEDED}
 * (never the audio bytes — those are discarded the moment the worker finishes).
 *
 * <p>T849 — {@link #partialTranscript()} carries the text produced <em>so far</em>
 * while the job is still {@code RUNNING}, for a streaming-capable backend. It is
 * a preview, never the result: the authoritative transcript is still
 * {@link #transcript()} on the terminal snapshot.
 *
 * <p>T851 — {@link #segments()} carries the diarized per-speaker turns when the
 * job asked for them and the backend produced them. Always empty otherwise
 * (including for a chunked recording, whose speaker labels could not be
 * reconciled across chunks); {@link #transcript()} is authoritative either way.
 *
 * @param jobId            opaque id assigned at submit time
 * @param state            lifecycle state
 * @param transcript       stitched transcript when succeeded, else {@code null}
 * @param partialTranscript T849 — transcript so far while running, else {@code null}
 * @param segments         T851 — per-speaker turns when diarized; never {@code null}
 * @param language         detected/aggregated language when succeeded, else {@code null}
 * @param error            failure message when failed, else {@code null}
 * @param submittedAt      epoch millis the job was accepted
 * @param startedAt        epoch millis a worker picked it up, or 0 while queued
 * @param finishedAt       epoch millis it reached a terminal state, or 0 while pending
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurTranscriptionJobStatus(
        String jobId,
        TurTranscriptionJobState state,
        String transcript,
        String partialTranscript,
        List<TurTranscriptionSegment> segments,
        String language,
        String error,
        long submittedAt,
        long startedAt,
        long finishedAt) {

    public TurTranscriptionJobStatus {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }

    /** True once the job has reached a terminal state — the SSE stream ends here. */
    public boolean terminal() {
        return state != null && state.terminal();
    }
}

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

import com.viglet.turing.persistence.dto.persona.TurPersonaDto;

/**
 * T715 / §XLII.7 — an immutable snapshot of an async persona-from-audio job,
 * returned by submit/poll and pushed over the SSE stream on every transition and
 * every transcription-chunk completion. The (never-saved) {@code draft} and the
 * {@code transcript} it came from ride only the {@link TurPersonaAudioJobState#SUCCEEDED}
 * snapshot; the audio bytes are never echoed back.
 *
 * @param jobId           opaque id assigned at submit time
 * @param state           lifecycle state
 * @param completedChunks transcription chunks finished so far (0 until known)
 * @param totalChunks     total transcription chunks (0 until the plan is known)
 * @param draft           the drafted persona when succeeded, else {@code null}
 * @param transcript      the transcript when succeeded, else {@code null}
 * @param error           failure message when failed, else {@code null}
 * @param submittedAt     epoch millis the job was accepted
 * @param startedAt       epoch millis a worker picked it up, or 0 while queued
 * @param finishedAt      epoch millis it reached a terminal state, or 0 while pending
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPersonaAudioJobStatus(
        String jobId,
        TurPersonaAudioJobState state,
        int completedChunks,
        int totalChunks,
        TurPersonaDto draft,
        String transcript,
        String error,
        long submittedAt,
        long startedAt,
        long finishedAt) {

    /** True once the job has reached a terminal state — the SSE stream ends here. */
    public boolean terminal() {
        return state != null && state.terminal();
    }
}

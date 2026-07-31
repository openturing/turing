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
 * Outcome of a speech-to-text transcription (Block AA / §XXVI.6). Fail-soft:
 * {@code success=false} carries an {@code error} rather than throwing, so
 * callers (e.g. persona-from-audio) can surface it cleanly.
 *
 * <p>{@code confidence} (T693) is an optional 0–1 score — higher is better —
 * populated by backends that expose one (e.g. an OpenAI-compatible
 * {@code verbose_json} response's segment {@code avg_logprob}). It is {@code null}
 * when the backend gives no confidence signal, and it drives the optional
 * local→cloud confidence fallback in {@link TurTranscriptionService}.
 *
 * <p>{@code segments} (T851) is the optional diarized breakdown — who spoke,
 * what they said, when. It <b>annotates</b> {@code text}, never replaces it: an
 * un-diarized transcription (every backend before this, and every request that
 * didn't ask) carries an empty list, so no existing caller changes. The flat
 * {@code text} is always the authoritative transcript.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurTranscriptionResult(boolean success, String text, String language,
        Double confidence, String error, List<TurTranscriptionSegment> segments) {

    public TurTranscriptionResult {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }

    public static TurTranscriptionResult ok(String text, String language) {
        return new TurTranscriptionResult(true, text, language, null, null, List.of());
    }

    public static TurTranscriptionResult ok(String text, String language, Double confidence) {
        return new TurTranscriptionResult(true, text, language, confidence, null, List.of());
    }

    /** T851 — a successful transcription that also carries per-speaker turns. */
    public static TurTranscriptionResult ok(String text, String language, Double confidence,
            List<TurTranscriptionSegment> segments) {
        return new TurTranscriptionResult(true, text, language, confidence, null, segments);
    }

    public static TurTranscriptionResult fail(String error) {
        return new TurTranscriptionResult(false, null, null, null, error, List.of());
    }

    /** T851 — true when this transcription carries an attributable breakdown. */
    public boolean diarized() {
        return !segments.isEmpty();
    }
}

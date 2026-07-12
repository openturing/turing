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
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurTranscriptionResult(boolean success, String text, String language,
        Double confidence, String error) {

    public static TurTranscriptionResult ok(String text, String language) {
        return new TurTranscriptionResult(true, text, language, null, null);
    }

    public static TurTranscriptionResult ok(String text, String language, Double confidence) {
        return new TurTranscriptionResult(true, text, language, confidence, null);
    }

    public static TurTranscriptionResult fail(String error) {
        return new TurTranscriptionResult(false, null, null, null, error);
    }
}

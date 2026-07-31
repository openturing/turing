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
 * Pluggable speech-to-text seam (Block AA / §XXVI.6) — the one new piece of
 * infrastructure the block needs (Spring AI 2.0.0-M2 has no transcription
 * module). The default implementation calls an OpenAI-compatible
 * {@code /audio/transcriptions} endpoint using an existing {@code TurLLMInstance}
 * credential; air-gapped installs plug a local STT by providing their own
 * {@code @Primary} bean — the same pluggable-seam discipline as the storage and
 * reranker abstractions. Also partially unblocks F.5 (voice agents).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurTranscriptionProvider {

    /**
     * Transcribe audio bytes to text.
     *
     * @param audio        the raw audio bytes
     * @param mimeType     the audio MIME type (e.g. {@code audio/mpeg}); may be null
     * @param languageHint optional ISO-639 hint (e.g. {@code pt}); may be null
     * @return a fail-soft result (never throws on a transcription error)
     */
    TurTranscriptionResult transcribe(byte[] audio, String mimeType, String languageHint);

    /**
     * T847 / §LXIII.2 — transcribe with optional per-request context hints layered
     * over the globally configured ones.
     *
     * <p>Defaults to the hint-less call, so a third-party {@code @Primary} provider
     * written against the original seam keeps working unchanged — hints a backend
     * cannot use are meant to be dropped silently, and "cannot use any" is a valid
     * case of that.
     *
     * @param hints per-request hints; {@code null}/empty means "global only"
     */
    default TurTranscriptionResult transcribe(byte[] audio, String mimeType, String languageHint,
            TurTranscriptionHints hints) {
        return transcribe(audio, mimeType, languageHint);
    }

    /**
     * T849 / §LXIII.4 — as above, but relaying transcript text to {@code partial}
     * as the backend produces it, for a backend that can stream a completed file.
     *
     * <p>Defaults to the non-streaming call for the same reason as the hints
     * overload: a provider that cannot stream should simply never call the
     * listener, and a third-party implementation written against the earlier seam
     * must keep working untouched.
     *
     * @param partial receives the transcript accumulated so far; never {@code null}
     *                — pass {@link TurTranscriptionPartialListener#NOOP} for none
     */
    default TurTranscriptionResult transcribe(byte[] audio, String mimeType, String languageHint,
            TurTranscriptionHints hints, TurTranscriptionPartialListener partial) {
        return transcribe(audio, mimeType, languageHint, hints);
    }

    /** True when a transcription backend is configured and usable. */
    boolean isAvailable();

    /**
     * T687 / §XLII.1 — the backend type this provider serves, used by
     * {@link TurTranscriptionProviderFactory} to route the config-selected
     * strategy to the right implementation.
     */
    TurTranscriptionProviderType getType();
}

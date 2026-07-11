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

    /** True when a transcription backend is configured and usable. */
    boolean isAvailable();

    /**
     * T687 / §XLII.1 — the backend type this provider serves, used by
     * {@link TurTranscriptionProviderFactory} to route the config-selected
     * strategy to the right implementation.
     */
    TurTranscriptionProviderType getType();
}

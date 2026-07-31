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

import org.apache.commons.lang3.StringUtils;

/**
 * T846 / §LXIII.1 — where a single {@link TurOpenAiTranscriptionClient} call
 * goes and in which dialect. Introduced so the shared client stops carrying one
 * hardcoded {@code DEFAULT_MODEL}: each provider supplies <b>its own</b> default
 * ({@code gpt-transcribe} for the cloud path, {@code whisper-1} for a self-hosted
 * OpenAI-compatible one), and the request shape is then resolved from the
 * effective model via {@link TurTranscriptionModelFamily}.
 *
 * @param baseUrl      OpenAI-compatible base URL (must be non-blank)
 * @param apiKey       bearer token; blank sends no {@code Authorization} header
 *                     (self-hosted servers often require none)
 * @param model        configured transcription model; blank → {@code defaultModel}
 * @param defaultModel the backend's default when {@code model} is blank
 * @param maxBytes     per-request upload limit (backstop; the orchestrator chunks
 *                     below this before calling)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurTranscriptionTarget(
        String baseUrl,
        String apiKey,
        String model,
        String defaultModel,
        long maxBytes) {

    /** The model actually put on the wire: the configured one, else the backend default. */
    public String effectiveModel() {
        return StringUtils.defaultIfBlank(StringUtils.trimToNull(model),
                StringUtils.defaultIfBlank(defaultModel,
                        TurTranscriptionProviderType.OPENAI_COMPATIBLE_DEFAULT_MODEL));
    }

    /** The wire dialect implied by {@link #effectiveModel()}. */
    public TurTranscriptionModelFamily family() {
        return TurTranscriptionModelFamily.of(effectiveModel());
    }
}

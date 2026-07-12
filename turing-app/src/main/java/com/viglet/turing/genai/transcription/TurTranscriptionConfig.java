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
 * T687 / §XLII.1 — the effective transcription configuration for the current
 * request, merged from {@code turing.transcription.*} env props (which win when
 * set) over the DB Global Settings row, produced by
 * {@link TurTranscriptionConfigResolver}.
 *
 * <p>{@code endpoint}, {@code model} and {@code apiKey} may be blank — an
 * {@code OPENAI}-strategy provider then falls back to the default
 * {@code TurLLMInstance} (back-compat), so a legacy install with no dedicated
 * transcription config keeps behaving exactly as before.
 *
 * @param type           the active backend
 * @param endpoint       dedicated OpenAI-compatible base URL, or {@code ""}
 * @param model          transcription model name, or {@code ""} (backend default)
 * @param apiKey         decrypted API key, or {@code ""} (fall back to LLM instance)
 * @param maxUploadBytes per-request upload limit for the active backend
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurTranscriptionConfig(
        TurTranscriptionProviderType type,
        String endpoint,
        String model,
        String apiKey,
        long maxUploadBytes) {
}

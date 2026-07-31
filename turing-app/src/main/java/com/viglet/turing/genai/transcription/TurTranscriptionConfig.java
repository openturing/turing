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
 * <p>T847 — {@code hints} carries the globally configured context ({@code prompt}
 * + {@code keywords}); a caller may layer per-request hints over it with
 * {@link TurTranscriptionHints#merge(TurTranscriptionHints)}. Never {@code null}
 * — an unset install gets {@link TurTranscriptionHints#none()}.
 *
 * @param type           the active backend
 * @param endpoint       dedicated OpenAI-compatible base URL, or {@code ""}
 * @param model          transcription model name, or {@code ""} (backend default)
 * @param apiKey         decrypted API key, or {@code ""} (fall back to LLM instance)
 * @param maxUploadBytes per-request upload limit for the active backend
 * @param hints          T847 global context hints; never {@code null}
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurTranscriptionConfig(
        TurTranscriptionProviderType type,
        String endpoint,
        String model,
        String apiKey,
        long maxUploadBytes,
        TurTranscriptionHints hints) {

    public TurTranscriptionConfig {
        hints = hints == null ? TurTranscriptionHints.none() : hints;
    }

    /**
     * T846 — the {@link TurTranscriptionTarget} for this config against
     * {@code baseUrl}/{@code apiKey}. The default model comes from the
     * <b>calling provider's</b> backend, not from {@link #type()}: when the T693
     * confidence fallback escalates from the local backend to the cloud one, the
     * config still describes the active (local) strategy while the request is
     * being built by the cloud provider.
     */
    public TurTranscriptionTarget targetFor(String baseUrl, String apiKey,
            TurTranscriptionProviderType backend) {
        TurTranscriptionProviderType resolved =
                backend == null ? TurTranscriptionProviderType.OPENAI : backend;
        return new TurTranscriptionTarget(baseUrl, apiKey, model, resolved.defaultModel(), maxUploadBytes);
    }
}

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

import java.util.Locale;

/**
 * T687 / §XLII.1 — selectable speech-to-text backend, resolved from config
 * (Global Settings or {@code turing.transcription.*}) rather than only a
 * {@code @Primary} bean override. Mirrors the {@code TurRagRerankStrategyType}
 * pattern (a lenient enum routed by a factory).
 *
 * <ul>
 *   <li>{@link #OPENAI} — the legacy default: a multipart POST to an
 *       OpenAI-compatible {@code /audio/transcriptions} endpoint whose
 *       {@code baseUrl} + credential fall back to the default
 *       {@code TurLLMInstance} when no dedicated transcription config is set.
 *       Empty/legacy config behaves byte-identically to today.</li>
 *   <li>{@link #OPENAI_COMPATIBLE} — the same REST contract but pointed at a
 *       dedicated (typically self-hosted, e.g. {@code faster-whisper-server})
 *       endpoint/model/key configured under transcription, decoupled from the
 *       default LLM instance (T690). This is the supported fully-local path.</li>
 *   <li>{@link #NONE} — transcription disabled; callers get a fail-soft result.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurTranscriptionProviderType {
    OPENAI,
    OPENAI_COMPATIBLE,
    NONE;

    /**
     * T846 / §LXIII.1 — the vendor's current recommendation for the cloud path:
     * cheaper than {@code whisper-1} and roughly half its word error rate.
     */
    public static final String OPENAI_DEFAULT_MODEL = "gpt-transcribe";

    /**
     * T846 / §LXIII.1 — a self-hosted OpenAI-compatible server (faster-whisper-server,
     * whisper.cpp, vLLM-whisper) serves <b>whisper</b>, not an OpenAI-hosted id, so the
     * local path keeps the legacy default. This is also why the default cannot be one
     * shared constant.
     */
    public static final String OPENAI_COMPATIBLE_DEFAULT_MODEL = "whisper-1";

    /**
     * T846 / §LXIII.1 — the model used when neither Global Settings nor
     * {@code turing.transcription.model} names one. Per-strategy on purpose: the
     * cloud backend follows the vendor's current recommendation, the self-hosted
     * one follows what such servers actually serve.
     */
    public String defaultModel() {
        return this == OPENAI_COMPATIBLE ? OPENAI_COMPATIBLE_DEFAULT_MODEL : OPENAI_DEFAULT_MODEL;
    }

    /**
     * Lenient parse used wherever a stored/config string is turned into a type.
     * Unknown, blank, or {@code null} values fall back to {@link #OPENAI} (the
     * legacy default), so a corrupt config row can never break transcription in a
     * way that throws — it just behaves like today's OpenAI-compatible path.
     */
    public static TurTranscriptionProviderType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return OPENAI;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return OPENAI;
        }
    }
}

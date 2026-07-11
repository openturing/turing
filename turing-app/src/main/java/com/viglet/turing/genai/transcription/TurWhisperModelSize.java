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
 * T691 / §XLII.5 — Whisper model size for the in-process {@code LOCAL_ONNX}
 * backend, trading accuracy for RAM/latency. Each maps to the canonical
 * HuggingFace repo id a future in-process runtime downloads (resiliently, via
 * {@code TurOnnxModelDownloader}) and caches. Lenient parse falls back to
 * {@link #BASE}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurWhisperModelSize {

    TINY("openai/whisper-tiny"),
    BASE("openai/whisper-base"),
    SMALL("openai/whisper-small");

    private final String modelRepo;

    TurWhisperModelSize(String modelRepo) {
        this.modelRepo = modelRepo;
    }

    /** Canonical HuggingFace repo id for this size (used by the in-process runtime). */
    public String modelRepo() {
        return modelRepo;
    }

    /** Lenient parse: unknown/blank/{@code null} → {@link #BASE}. */
    public static TurWhisperModelSize fromValue(String value) {
        if (value == null || value.isBlank()) {
            return BASE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BASE;
        }
    }
}

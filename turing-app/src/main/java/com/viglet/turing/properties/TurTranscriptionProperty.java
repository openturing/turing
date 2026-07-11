/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.properties;

import com.viglet.turing.genai.transcription.TurTranscriptionProviderType;

import lombok.Getter;
import lombok.Setter;

/**
 * T687 / §XLII.1 — boot-time speech-to-text configuration bound to
 * {@code turing.transcription.*}. Lets headless / Viglet Cloud deploys pin the
 * transcription backend per container without touching the Global Settings UI,
 * exactly the way {@code turing.storage.*} pins storage.
 *
 * <p>Any value left {@code null}/blank falls back to the DB Global Settings
 * value (then to the hardcoded default), so an unset namespace means "use the
 * UI-configured backend" — see {@code TurTranscriptionConfigResolver}. Non-blank
 * values here <b>win</b> over the DB row (the container pins the backend).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
public class TurTranscriptionProperty {

    /** Active backend. {@code null} = fall back to the UI-configured strategy. */
    private TurTranscriptionProviderType type;

    /** OpenAI-compatible base URL (e.g. {@code http://faster-whisper:8000/v1}). */
    private String endpoint;

    /** Transcription model name (blank = backend default, e.g. {@code whisper-1}). */
    private String model;

    /** Plaintext API key for the transcription endpoint (blank = fall back). */
    private String apiKey;

    /**
     * Per-request upload limit in bytes for the active backend. {@code null}/≤0 =
     * fall back to the UI value (OpenAI defaults to 26,214,400 = 25 MiB; a local
     * server can be much larger). The chunker (T688/T689) splits above this.
     */
    private Long maxUploadBytes;

    /** LOCAL_ONNX model size: {@code tiny} | {@code base} | {@code small} (T691). */
    private String onnxModelSize;
}

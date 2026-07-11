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

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * T691 / §XLII.5 — the {@link TurTranscriptionProviderType#LOCAL_ONNX} backend:
 * an air-gapped, no-sidecar path that runs Whisper <b>in-process</b>. This ships
 * the seam + config contract (model-size selection resolved to a HuggingFace
 * repo id, ready for the resilient {@code TurOnnxModelDownloader} acquisition
 * used by the T656 embedding path), mirroring the T193 Jlama scaffold discipline
 * — the heavy inference runtime (log-mel front-end + encoder/decoder ONNX
 * sessions, or a DJL Whisper translator) is deliberately <b>not bundled</b> and
 * lands in a follow-up (T694), so the platform doesn't prematurely commit to a
 * fragile Whisper dependency.
 *
 * <p><b>Why own the slot instead of leaving it unregistered?</b> If no
 * {@code LOCAL_ONNX} bean existed, {@link TurTranscriptionProviderFactory} would
 * fall back to {@code OPENAI} — silently routing audio to a cloud endpoint for an
 * operator who deliberately chose the air-gapped backend. That is a privacy
 * footgun. By owning the slot and failing <em>explicitly</em> (fail-soft, never
 * cloud), this provider guarantees a {@code LOCAL_ONNX} selection never leaks
 * audio off-box; the actionable message points to the supported local path
 * (a self-hosted {@code OPENAI_COMPATIBLE} server).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurLocalOnnxTranscriptionProvider implements TurTranscriptionProvider {

    private final TurTranscriptionConfigResolver configResolver;

    public TurLocalOnnxTranscriptionProvider(TurTranscriptionConfigResolver configResolver) {
        this.configResolver = configResolver;
    }

    @Override
    public TurTranscriptionProviderType getType() {
        return TurTranscriptionProviderType.LOCAL_ONNX;
    }

    /**
     * False until the in-process Whisper runtime is bundled (T694). The config
     * contract (model size) is nonetheless validated and exposed so the admin UI
     * and the future runtime resolve the same model.
     */
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public TurTranscriptionResult transcribe(byte[] audio, String mimeType, String languageHint) {
        TurWhisperModelSize size = TurWhisperModelSize.fromValue(configResolver.resolve().onnxModelSize());
        log.debug("[Transcription] LOCAL_ONNX selected (size={} → {}) but the in-process runtime "
                + "is not bundled in this build", size, size.modelRepo());
        return TurTranscriptionResult.fail(
                "In-process ONNX Whisper (LOCAL_ONNX, model size " + size.name().toLowerCase()
                        + ") is not bundled in this build. For a fully local, air-gapped backend use "
                        + "OPENAI_COMPATIBLE with a self-hosted faster-whisper-server "
                        + "(see docs/references/transcription-local-backend.md), or select the cloud "
                        + "OPENAI backend. Audio was NOT sent anywhere.");
    }
}

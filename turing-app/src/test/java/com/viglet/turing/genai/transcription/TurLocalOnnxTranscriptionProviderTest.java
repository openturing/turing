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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/**
 * T691 / §XLII.5 — the LOCAL_ONNX scaffold: reserves the slot (so an air-gapped
 * selection never falls back to a cloud backend), validates the model size, and
 * fails soft with an actionable message that never leaks audio off-box.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurLocalOnnxTranscriptionProviderTest {

    private TurLocalOnnxTranscriptionProvider provider(String size) {
        TurTranscriptionConfigResolver resolver = mock(TurTranscriptionConfigResolver.class);
        when(resolver.resolve()).thenReturn(new TurTranscriptionConfig(
                TurTranscriptionProviderType.LOCAL_ONNX, "", "", "", 26_214_400L, size));
        return new TurLocalOnnxTranscriptionProvider(resolver);
    }

    @Test
    void typeIsLocalOnnx() {
        assertThat(provider("base").getType()).isEqualTo(TurTranscriptionProviderType.LOCAL_ONNX);
    }

    @Test
    void notAvailableUntilRuntimeBundled() {
        assertThat(provider("base").isAvailable()).isFalse();
    }

    @Test
    void transcribeFailsSoftWithActionableMessageAndNeverLeaks() {
        TurTranscriptionResult result = provider("small").transcribe(new byte[] { 1, 2 }, "audio/mpeg", "en");
        assertThat(result.success()).isFalse();
        assertThat(result.error())
                .contains("small")
                .contains("OPENAI_COMPATIBLE")
                .contains("NOT sent");
    }

    @Test
    void modelSizeLenientParse() {
        assertThat(TurWhisperModelSize.fromValue("TINY")).isEqualTo(TurWhisperModelSize.TINY);
        assertThat(TurWhisperModelSize.fromValue("small")).isEqualTo(TurWhisperModelSize.SMALL);
        assertThat(TurWhisperModelSize.fromValue("garbage")).isEqualTo(TurWhisperModelSize.BASE);
        assertThat(TurWhisperModelSize.fromValue(null)).isEqualTo(TurWhisperModelSize.BASE);
        assertThat(TurWhisperModelSize.BASE.modelRepo()).isEqualTo("openai/whisper-base");
    }
}

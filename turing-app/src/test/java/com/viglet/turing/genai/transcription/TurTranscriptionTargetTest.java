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

import org.junit.jupiter.api.Test;

/**
 * T846 / §LXIII.1 — the per-backend default model and the dialect it implies.
 * The point of the record: the cloud path and the self-hosted path no longer
 * share one hardcoded {@code whisper-1}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurTranscriptionTargetTest {

    private static TurTranscriptionConfig config(String model, TurTranscriptionProviderType type) {
        return new TurTranscriptionConfig(type, "http://x/v1", model, "k", 26_214_400L,
                TurTranscriptionHints.none());
    }

    @Test
    void cloudBackendDefaultsToTheVendorsCurrentRecommendation() {
        TurTranscriptionTarget target = config("", TurTranscriptionProviderType.OPENAI)
                .targetFor("http://x/v1", "k", TurTranscriptionProviderType.OPENAI);

        assertThat(target.effectiveModel()).isEqualTo("gpt-transcribe");
        assertThat(target.family()).isEqualTo(TurTranscriptionModelFamily.GPT_TRANSCRIBE);
    }

    @Test
    void selfHostedBackendKeepsWhisper() {
        TurTranscriptionTarget target = config("  ", TurTranscriptionProviderType.OPENAI_COMPATIBLE)
                .targetFor("http://x/v1", "k", TurTranscriptionProviderType.OPENAI_COMPATIBLE);

        assertThat(target.effectiveModel()).isEqualTo("whisper-1");
        assertThat(target.family()).isEqualTo(TurTranscriptionModelFamily.WHISPER);
    }

    @Test
    void aConfiguredModelAlwaysWinsOverTheBackendDefault() {
        TurTranscriptionTarget target = config("whisper-1", TurTranscriptionProviderType.OPENAI)
                .targetFor("http://x/v1", "k", TurTranscriptionProviderType.OPENAI);

        assertThat(target.effectiveModel()).isEqualTo("whisper-1");
        assertThat(target.family()).isEqualTo(TurTranscriptionModelFamily.WHISPER);
    }

    /**
     * The T693 fallback escalates from the local backend to the cloud one while
     * the resolved config still describes the <em>active</em> (local) strategy,
     * so the default must come from the calling provider, not {@code config.type()}.
     */
    @Test
    void theDefaultFollowsTheCallingProviderNotTheActiveStrategy() {
        TurTranscriptionTarget target = config("", TurTranscriptionProviderType.OPENAI_COMPATIBLE)
                .targetFor("https://api.openai.com/v1", "k", TurTranscriptionProviderType.OPENAI);

        assertThat(target.effectiveModel()).isEqualTo("gpt-transcribe");
    }
}

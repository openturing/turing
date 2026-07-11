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
 * T690 / §XLII.4 — fail-soft guards of {@link TurOpenAiTranscriptionClient}
 * (empty audio, missing endpoint, oversize). The HTTP round-trip itself is
 * integration-only.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurOpenAiTranscriptionClientTest {

    private final TurOpenAiTranscriptionClient client = new TurOpenAiTranscriptionClient();

    @Test
    void emptyAudioFails() {
        assertThat(client.transcribe("http://x/v1", "k", "m", 100L, new byte[0], "audio/mpeg", "en")
                .success()).isFalse();
        assertThat(client.transcribe("http://x/v1", "k", "m", 100L, null, "audio/mpeg", "en")
                .success()).isFalse();
    }

    @Test
    void blankBaseUrlFails() {
        TurTranscriptionResult r = client.transcribe("  ", "k", "m", 100L,
                new byte[] { 1 }, "audio/mpeg", "en");
        assertThat(r.success()).isFalse();
        assertThat(r.error()).contains("endpoint");
    }

    @Test
    void oversizeFailsWithActionableMessage() {
        TurTranscriptionResult r = client.transcribe("http://x/v1", "k", "m", 10L,
                new byte[11], "audio/mpeg", "en");
        assertThat(r.success()).isFalse();
        assertThat(r.error()).contains("above the");
    }
}

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
 * (empty audio, missing endpoint, oversize), plus T846 / §LXIII.1: the
 * confidence a response may honestly yield now depends on the model family. The
 * HTTP round-trip itself is integration-only.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurOpenAiTranscriptionClientTest {

    private final TurOpenAiTranscriptionClient client = new TurOpenAiTranscriptionClient();

    private static TurTranscriptionTarget target(String baseUrl, long maxBytes) {
        return new TurTranscriptionTarget(baseUrl, "k", "m", "whisper-1", maxBytes);
    }

    @Test
    void emptyAudioFails() {
        assertThat(client.transcribe(target("http://x/v1", 100L), new byte[0], "audio/mpeg", "en",
                TurTranscriptionHints.none()).success()).isFalse();
        assertThat(client.transcribe(target("http://x/v1", 100L), null, "audio/mpeg", "en",
                TurTranscriptionHints.none()).success()).isFalse();
    }

    @Test
    void blankBaseUrlFails() {
        TurTranscriptionResult r = client.transcribe(target("  ", 100L), new byte[] { 1 },
                "audio/mpeg", "en", TurTranscriptionHints.none());
        assertThat(r.success()).isFalse();
        assertThat(r.error()).contains("endpoint");
    }

    @Test
    void nullTargetFails() {
        TurTranscriptionResult r = client.transcribe(null, new byte[] { 1 }, "audio/mpeg", "en",
                TurTranscriptionHints.none());
        assertThat(r.success()).isFalse();
        assertThat(r.error()).contains("endpoint");
    }

    @Test
    void oversizeFailsWithActionableMessage() {
        TurTranscriptionResult r = client.transcribe(target("http://x/v1", 10L), new byte[11],
                "audio/mpeg", "en", TurTranscriptionHints.none());
        assertThat(r.success()).isFalse();
        assertThat(r.error()).contains("above the");
    }

    // ---- T693 confidence extraction from verbose_json segments ----------

    @Test
    void confidenceFromSegmentsMeansLogprobsThroughExp() {
        // avg_logprob 0 → exp(0)=1.0; two segments averaged then exp.
        Double c = TurOpenAiTranscriptionClient.confidenceFromSegments(java.util.List.of(
                java.util.Map.of("avg_logprob", 0.0),
                java.util.Map.of("avg_logprob", 0.0)));
        assertThat(c).isEqualTo(1.0);
    }

    @Test
    void confidenceFromSegmentsNullWhenAbsentOrEmpty() {
        assertThat(TurOpenAiTranscriptionClient.confidenceFromSegments(null)).isNull();
        assertThat(TurOpenAiTranscriptionClient.confidenceFromSegments(java.util.List.of())).isNull();
        assertThat(TurOpenAiTranscriptionClient.confidenceFromSegments(
                java.util.List.of(java.util.Map.of("no_logprob", 1)))).isNull();
    }

    @Test
    void confidenceFromSegmentsClampsToUnitRange() {
        // A negative avg_logprob maps to a probability in (0,1).
        Double c = TurOpenAiTranscriptionClient.confidenceFromSegments(java.util.List.of(
                java.util.Map.of("avg_logprob", -0.7)));
        assertThat(c).isBetween(0.0, 1.0);
    }

    // ---- T846: confidence is a per-family capability -------------------

    /**
     * A {@code gpt-*transcribe} response returns no segment logprobs, so the
     * client reports {@code null} rather than a fabricated score — T693 already
     * reads null as "no signal", so the fallback degrades instead of lying.
     */
    @Test
    void gptTranscribeFamilyNeverReportsAConfidence() {
        Object segments = java.util.List.of(java.util.Map.of("avg_logprob", 0.0));
        assertThat(TurOpenAiTranscriptionClient.confidenceFor(
                TurTranscriptionModelFamily.GPT_TRANSCRIBE, segments)).isNull();
        assertThat(TurOpenAiTranscriptionClient.confidenceFor(
                TurTranscriptionModelFamily.GPT_4O_TRANSCRIBE, segments)).isNull();
    }

    @Test
    void whisperFamilyStillReportsAConfidence() {
        Object segments = java.util.List.of(java.util.Map.of("avg_logprob", 0.0));
        assertThat(TurOpenAiTranscriptionClient.confidenceFor(
                TurTranscriptionModelFamily.WHISPER, segments)).isEqualTo(1.0);
    }
}

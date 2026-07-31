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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T849 / §LXIII.4 — the {@code stream=true} event mapping: SSE frame parsing and
 * the delta→done fold, tested without an HTTP round-trip (the transport itself is
 * integration-only, like the rest of the client).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurTranscriptionStreamTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private JsonNode event(String json) {
        return objectMapper.readTree(json);
    }

    // ---- SSE frame extraction ------------------------------------------

    @Test
    void extractsTheJsonPayloadFromADataFrame() {
        assertThat(TurOpenAiTranscriptionClient.ssePayload("data: {\"type\":\"x\"}"))
                .isEqualTo("{\"type\":\"x\"}");
    }

    @Test
    void ignoresCommentsBlanksAndTheTerminator() {
        assertThat(TurOpenAiTranscriptionClient.ssePayload(": heartbeat")).isNull();
        assertThat(TurOpenAiTranscriptionClient.ssePayload("")).isNull();
        assertThat(TurOpenAiTranscriptionClient.ssePayload(null)).isNull();
        assertThat(TurOpenAiTranscriptionClient.ssePayload("event: delta")).isNull();
        assertThat(TurOpenAiTranscriptionClient.ssePayload("data: [DONE]")).isNull();
        assertThat(TurOpenAiTranscriptionClient.ssePayload("data:   ")).isNull();
    }

    // ---- delta → done fold ---------------------------------------------

    @Test
    void accumulatesDeltasAndNotifiesWithTheTextSoFar() {
        List<String> seen = new ArrayList<>();
        var accumulator = new TurOpenAiTranscriptionClient.StreamAccumulator("en");

        accumulator.accept(event("{\"type\":\"transcript.text.delta\",\"delta\":\"Hello\"}"), seen::add);
        accumulator.accept(event("{\"type\":\"transcript.text.delta\",\"delta\":\" there\"}"), seen::add);

        // Each notification carries the FULL text so far, not the delta.
        assertThat(seen).containsExactly("Hello", "Hello there");
        assertThat(accumulator.result().text()).isEqualTo("Hello there");
    }

    @Test
    void theDoneEventWinsOverTheAccumulatedDeltas() {
        var accumulator = new TurOpenAiTranscriptionClient.StreamAccumulator("en");
        accumulator.accept(event("{\"type\":\"transcript.text.delta\",\"delta\":\"Helo\"}"), t -> { });
        accumulator.accept(event("{\"type\":\"transcript.text.done\",\"text\":\"Hello.\"}"), t -> { });

        assertThat(accumulator.result().text()).isEqualTo("Hello.");
    }

    @Test
    void aDoneEventWithoutTextFallsBackToTheAccumulatedDeltas() {
        var accumulator = new TurOpenAiTranscriptionClient.StreamAccumulator("en");
        accumulator.accept(event("{\"type\":\"transcript.text.delta\",\"delta\":\"Hi\"}"), t -> { });
        accumulator.accept(event("{\"type\":\"transcript.text.done\"}"), t -> { });

        assertThat(accumulator.result().text()).isEqualTo("Hi");
    }

    @Test
    void aLanguageOnTheDoneEventOverridesTheHint() {
        var accumulator = new TurOpenAiTranscriptionClient.StreamAccumulator("en");
        accumulator.accept(event("{\"type\":\"transcript.text.delta\",\"delta\":\"Ola\"}"), t -> { });
        accumulator.accept(event("{\"type\":\"transcript.text.done\",\"language\":\"pt\"}"), t -> { });

        assertThat(accumulator.result().language()).isEqualTo("pt");
    }

    @Test
    void unknownEventsAndEmptyDeltasAreIgnored() {
        List<String> seen = new ArrayList<>();
        var accumulator = new TurOpenAiTranscriptionClient.StreamAccumulator("en");

        accumulator.accept(event("{\"type\":\"transcript.text.delta\",\"delta\":\"\"}"), seen::add);
        accumulator.accept(event("{\"type\":\"some.other.event\",\"delta\":\"nope\"}"), seen::add);

        assertThat(seen).isEmpty();
    }

    /**
     * A stream that produced nothing yields {@code null}, which is the client's
     * signal to retry on the blocking path rather than report an empty transcript.
     */
    @Test
    void anEmptyStreamYieldsNullSoTheCallerFallsBack() {
        assertThat(new TurOpenAiTranscriptionClient.StreamAccumulator("en").result()).isNull();
    }

    // ---- when streaming is even attempted -------------------------------

    @Test
    void streamingIsSkippedWithoutARealListener() {
        assertThat(TurOpenAiTranscriptionClient.streamingWanted(
                TurTranscriptionPartialListener.NOOP, TurTranscriptionModelFamily.GPT_TRANSCRIBE,
                TurTranscriptionHints.none()))
                .isFalse();
        assertThat(TurOpenAiTranscriptionClient.streamingWanted(
                null, TurTranscriptionModelFamily.GPT_TRANSCRIBE,
                TurTranscriptionHints.none())).isFalse();
    }

    /** whisper has no streaming transcription endpoint — never attempt it. */
    @Test
    void streamingIsSkippedForTheWhisperFamily() {
        assertThat(TurOpenAiTranscriptionClient.streamingWanted(
                text -> { }, TurTranscriptionModelFamily.WHISPER,
                TurTranscriptionHints.none())).isFalse();
    }

    @Test
    void streamingIsAttemptedForTheGptTranscribeFamilies() {
        assertThat(TurOpenAiTranscriptionClient.streamingWanted(
                text -> { }, TurTranscriptionModelFamily.GPT_TRANSCRIBE,
                TurTranscriptionHints.none())).isTrue();
        assertThat(TurOpenAiTranscriptionClient.streamingWanted(
                text -> { }, TurTranscriptionModelFamily.GPT_4O_TRANSCRIBE,
                TurTranscriptionHints.none())).isTrue();
    }
}

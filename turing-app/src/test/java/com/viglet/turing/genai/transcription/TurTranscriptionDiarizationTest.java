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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * T851 / §LXIII.6 — the diarization wire contract: when {@code diarized_json} is
 * asked for at all, and how its {@code segments[]} map to attributable turns.
 * The HTTP round-trip itself stays integration-only.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurTranscriptionDiarizationTest {

    private static Map<String, Object> segment(String speaker, String text, Double start, Double end) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("speaker", speaker);
        map.put("text", text);
        map.put("start", start);
        map.put("end", end);
        return map;
    }

    // ---- when diarization is even requested ----------------------------

    @Test
    void diarizationIsRequestedOnlyWhenAskedForAndSupported() {
        TurTranscriptionHints asked = TurTranscriptionHints.diarized();
        assertThat(TurOpenAiTranscriptionClient.diarizationWanted(
                asked, TurTranscriptionModelFamily.GPT_TRANSCRIBE)).isTrue();
        assertThat(TurOpenAiTranscriptionClient.diarizationWanted(
                TurTranscriptionHints.none(), TurTranscriptionModelFamily.GPT_TRANSCRIBE)).isFalse();
        assertThat(TurOpenAiTranscriptionClient.diarizationWanted(
                null, TurTranscriptionModelFamily.GPT_TRANSCRIBE)).isFalse();
    }

    /**
     * whisper's {@code verbose_json} segments are timing boundaries with no
     * speaker, so honouring a diarization request there would attribute turns to
     * nobody. It is ignored rather than failed.
     */
    @Test
    void whisperIgnoresADiarizationRequest() {
        assertThat(TurOpenAiTranscriptionClient.diarizationWanted(
                TurTranscriptionHints.diarized(), TurTranscriptionModelFamily.WHISPER)).isFalse();
        assertThat(TurOpenAiTranscriptionClient.responseFormatFor(
                TurTranscriptionModelFamily.WHISPER, TurTranscriptionHints.diarized()))
                .isEqualTo("verbose_json");
    }

    @Test
    void diarizationUpgradesTheResponseFormat() {
        assertThat(TurOpenAiTranscriptionClient.responseFormatFor(
                TurTranscriptionModelFamily.GPT_TRANSCRIBE, TurTranscriptionHints.diarized()))
                .isEqualTo("diarized_json");
        assertThat(TurOpenAiTranscriptionClient.responseFormatFor(
                TurTranscriptionModelFamily.GPT_TRANSCRIBE, TurTranscriptionHints.none()))
                .isEqualTo("json");
    }

    /**
     * Streaming is a progress affordance; diarization changes the shape of the
     * result. Losing live text is cosmetic, losing the turns would be data loss —
     * so diarization wins.
     */
    @Test
    void diarizationWinsOverStreaming() {
        assertThat(TurOpenAiTranscriptionClient.streamingWanted(text -> { },
                TurTranscriptionModelFamily.GPT_TRANSCRIBE, TurTranscriptionHints.diarized()))
                .isFalse();
        assertThat(TurOpenAiTranscriptionClient.streamingWanted(text -> { },
                TurTranscriptionModelFamily.GPT_TRANSCRIBE, TurTranscriptionHints.none()))
                .isTrue();
    }

    // ---- segment mapping -----------------------------------------------

    @Test
    void mapsSpeakerTextAndTimings() {
        List<TurTranscriptionSegment> turns = TurOpenAiTranscriptionClient.segmentsFrom(List.of(
                segment("A", "Tell me about your work.", 0.0, 2.5),
                segment("B", "I have sold cars for twenty years.", 2.5, 6.0)));

        assertThat(turns).hasSize(2);
        assertThat(turns.get(0).speaker()).isEqualTo("A");
        assertThat(turns.get(0).text()).isEqualTo("Tell me about your work.");
        assertThat(turns.get(0).startSeconds()).isEqualTo(0.0);
        assertThat(turns.get(1).speaker()).isEqualTo("B");
        assertThat(turns.get(1).endSeconds()).isEqualTo(6.0);
    }

    @Test
    void dropsTurnsThatSayNothing() {
        List<TurTranscriptionSegment> turns = TurOpenAiTranscriptionClient.segmentsFrom(List.of(
                segment("A", "   ", 0.0, 1.0),
                segment("B", "Real words.", 1.0, 2.0)));

        assertThat(turns).hasSize(1);
        assertThat(turns.get(0).text()).isEqualTo("Real words.");
    }

    @Test
    void toleratesMissingSpeakerAndTimings() {
        Map<String, Object> bare = new LinkedHashMap<>();
        bare.put("text", "Anonymous turn.");
        List<TurTranscriptionSegment> turns =
                TurOpenAiTranscriptionClient.segmentsFrom(List.of(bare));

        assertThat(turns).hasSize(1);
        assertThat(turns.get(0).speaker()).isNull();
        assertThat(turns.get(0).startSeconds()).isNull();
    }

    @Test
    void absentOrUnusableSegmentsYieldAnEmptyList() {
        assertThat(TurOpenAiTranscriptionClient.segmentsFrom(null)).isEmpty();
        assertThat(TurOpenAiTranscriptionClient.segmentsFrom(List.of())).isEmpty();
        assertThat(TurOpenAiTranscriptionClient.segmentsFrom("not a list")).isEmpty();
        assertThat(TurOpenAiTranscriptionClient.segmentsFrom(List.of("junk"))).isEmpty();
    }

    // ---- the flat transcript stays authoritative -------------------------

    @Test
    void aTopLevelTextWinsAsTheFlatTranscript() {
        Map<String, Object> response = Map.of("text", "The whole thing.");
        assertThat(TurOpenAiTranscriptionClient.transcriptFrom(response,
                List.of(new TurTranscriptionSegment("A", "Partial.", null, null))))
                .isEqualTo("The whole thing.");
    }

    /** A diarized_json body may carry no top-level text — join the turns in order. */
    @Test
    void withoutATopLevelTextTheTurnsAreJoinedInOrder() {
        assertThat(TurOpenAiTranscriptionClient.transcriptFrom(Map.of(), List.of(
                new TurTranscriptionSegment("A", "First.", null, null),
                new TurTranscriptionSegment("B", "Second.", null, null))))
                .isEqualTo("First. Second.");
    }

    // ---- the result shape ------------------------------------------------

    @Test
    void anUndiarizedResultCarriesAnEmptyListNotNull() {
        TurTranscriptionResult result = TurTranscriptionResult.ok("flat", "en");
        assertThat(result.segments()).isEmpty();
        assertThat(result.diarized()).isFalse();
        assertThat(TurTranscriptionResult.fail("x").segments()).isEmpty();
    }

    @Test
    void aDiarizedResultKeepsTheFlatTextAuthoritative() {
        TurTranscriptionResult result = TurTranscriptionResult.ok("A: hi B: hello", "en", null,
                List.of(new TurTranscriptionSegment("A", "hi", null, null),
                        new TurTranscriptionSegment("B", "hello", null, null)));

        assertThat(result.text()).isEqualTo("A: hi B: hello");
        assertThat(result.diarized()).isTrue();
        assertThat(result.segments()).hasSize(2);
    }

    // ---- per-chunk stripping --------------------------------------------

    /**
     * A chunk of a longer recording keeps its prompt/keywords (still valid) but
     * loses the diarization request, whose labels could not be reconciled anyway —
     * asking for a format we would discard only costs money.
     */
    @Test
    void withoutDiarizationKeepsTheOtherContext() {
        TurTranscriptionHints hints =
                new TurTranscriptionHints("context", List.of("Turing"), true);
        TurTranscriptionHints stripped = hints.withoutDiarization();

        assertThat(stripped.diarize()).isFalse();
        assertThat(stripped.prompt()).isEqualTo("context");
        assertThat(stripped.keywords()).containsExactly("Turing");
    }

    @Test
    void withoutDiarizationIsIdentityWhenAlreadyOff() {
        TurTranscriptionHints hints = TurTranscriptionHints.ofKeywords(List.of("Solr"));
        assertThat(hints.withoutDiarization()).isSameAs(hints);
    }

    /** A caller adding a keyword must never silently switch diarization off. */
    @Test
    void mergeOrsTheDiarizationFlag() {
        TurTranscriptionHints global = TurTranscriptionHints.diarized();
        TurTranscriptionHints merged = global.merge(TurTranscriptionHints.ofKeywords(List.of("Solr")));
        assertThat(merged.diarize()).isTrue();

        // …and a request can turn it on where the deployment left it off.
        TurTranscriptionHints off = TurTranscriptionHints.ofKeywords(List.of("Solr"));
        assertThat(off.merge(TurTranscriptionHints.diarized()).diarize()).isTrue();
    }

    @Test
    void diarizedHintsAreNotEmpty() {
        assertThat(TurTranscriptionHints.diarized().isEmpty()).isFalse();
    }
}

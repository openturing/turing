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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T846 / §LXIII.1 — the wire dialect resolved from a model id, and the
 * per-family request shape that follows from it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurTranscriptionModelFamilyTest {

    @ParameterizedTest
    @CsvSource({
            "whisper-1, WHISPER",
            "Systran/faster-whisper-large-v3, WHISPER",
            "openai/whisper-large-v3-turbo, WHISPER",
            "gpt-4o-transcribe, GPT_4O_TRANSCRIBE",
            "gpt-4o-mini-transcribe, GPT_4O_TRANSCRIBE",
            "gpt-4o-transcribe-diarize, GPT_4O_TRANSCRIBE",
            "gpt-transcribe, GPT_TRANSCRIBE",
            "gpt-live-transcribe, GPT_TRANSCRIBE",
            "gpt-transcribe-2026-07-01, GPT_TRANSCRIBE",
    })
    void resolvesTheDialectFromTheModelId(String model, TurTranscriptionModelFamily expected) {
        assertThat(TurTranscriptionModelFamily.of(model)).isEqualTo(expected);
    }

    /**
     * Whisper is the most permissive dialect (a server that ignores
     * {@code verbose_json} just returns no segments), so an unrecognised id can
     * never be made worse by the dialect choice.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "some-custom-local-model" })
    void unknownOrBlankIdsFallBackToWhisper(String model) {
        assertThat(TurTranscriptionModelFamily.of(model))
                .isEqualTo(TurTranscriptionModelFamily.WHISPER);
    }

    @Test
    void whisperKeepsVerboseJsonAndSingularLanguage() {
        TurTranscriptionModelFamily whisper = TurTranscriptionModelFamily.WHISPER;
        assertThat(whisper.responseFormat()).isEqualTo("verbose_json");
        assertThat(whisper.languageParameter()).isEqualTo("language");
        assertThat(whisper.supportsSegmentConfidence()).isTrue();
        assertThat(whisper.supportsKeywords()).isFalse();
    }

    /**
     * The current models take plain {@code json} and the <b>plural</b>
     * {@code languages} — sending the singular form would silently drop the hint.
     */
    @Test
    void gptTranscribeTakesJsonPluralLanguagesAndKeywords() {
        TurTranscriptionModelFamily family = TurTranscriptionModelFamily.GPT_TRANSCRIBE;
        assertThat(family.responseFormat()).isEqualTo("json");
        assertThat(family.languageParameter()).isEqualTo("languages");
        assertThat(family.supportsSegmentConfidence()).isFalse();
        assertThat(family.supportsKeywords()).isTrue();
    }

    /**
     * The superseded {@code gpt-4o-transcribe} ids sit in between: {@code json}
     * like the new models, but still the singular {@code language} and no
     * {@code keywords}. Lumping them with either neighbour would break a hint.
     */
    @Test
    void gpt4oTranscribeTakesJsonButStillSingularLanguage() {
        TurTranscriptionModelFamily family = TurTranscriptionModelFamily.GPT_4O_TRANSCRIBE;
        assertThat(family.responseFormat()).isEqualTo("json");
        assertThat(family.languageParameter()).isEqualTo("language");
        assertThat(family.supportsSegmentConfidence()).isFalse();
        assertThat(family.supportsKeywords()).isFalse();
    }
}

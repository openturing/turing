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

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * T847 / §LXIII.2 — normalization and per-request layering of the transcription
 * context hints. A keyword list is a hint, not a dictionary dump, so the record
 * itself enforces the bounds rather than trusting every caller to.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurTranscriptionHintsTest {

    @Test
    void noneIsEmpty() {
        assertThat(TurTranscriptionHints.none().isEmpty()).isTrue();
        assertThat(TurTranscriptionHints.none().keywords()).isEmpty();
        assertThat(TurTranscriptionHints.none().prompt()).isNull();
    }

    @Test
    void blankPromptAndKeywordsNormalizeAway() {
        TurTranscriptionHints hints = new TurTranscriptionHints("   ", Arrays.asList("", "  ", null));
        assertThat(hints.isEmpty()).isTrue();
    }

    @Test
    void keywordsAreTrimmedAndDeDuplicatedCaseInsensitively() {
        TurTranscriptionHints hints = TurTranscriptionHints.ofKeywords(
                List.of(" Turing ", "turing", "TURING", "Solr"));
        // First spelling wins; later casings are the same term.
        assertThat(hints.keywords()).containsExactly("Turing", "Solr");
    }

    @Test
    void keywordsAreCappedAtTheHardLimit() {
        List<String> many = IntStream.range(0, TurTranscriptionHints.MAX_KEYWORDS + 50)
                .mapToObj(i -> "term-" + i)
                .toList();
        assertThat(TurTranscriptionHints.ofKeywords(many).keywords())
                .hasSize(TurTranscriptionHints.MAX_KEYWORDS);
    }

    @Test
    void promptIsTruncatedAtTheHardLimit() {
        String huge = "x".repeat(TurTranscriptionHints.MAX_PROMPT_CHARS + 500);
        assertThat(new TurTranscriptionHints(huge, List.of()).prompt())
                .hasSize(TurTranscriptionHints.MAX_PROMPT_CHARS);
    }

    @Test
    void nullKeywordListBecomesEmptyNotNull() {
        assertThat(new TurTranscriptionHints("ctx", null).keywords()).isEmpty();
    }

    // ---- merge: per-request layered over global ------------------------

    @Test
    void mergingNullOrEmptyOverrideKeepsTheGlobalHints() {
        TurTranscriptionHints global = new TurTranscriptionHints("ctx", List.of("Turing"));
        assertThat(global.merge(null)).isEqualTo(global);
        assertThat(global.merge(TurTranscriptionHints.none())).isEqualTo(global);
    }

    /** A per-request vocabulary is a decision — it replaces, it does not append. */
    @Test
    void anOverrideKeywordListReplacesRatherThanAppends() {
        TurTranscriptionHints global = new TurTranscriptionHints("ctx", List.of("Turing", "Solr"));
        TurTranscriptionHints merged = global.merge(TurTranscriptionHints.ofKeywords(List.of("Lucene")));

        assertThat(merged.keywords()).containsExactly("Lucene");
        assertThat(merged.prompt()).isEqualTo("ctx");
    }

    @Test
    void anOverridePromptReplacesTheGlobalPrompt() {
        TurTranscriptionHints global = new TurTranscriptionHints("global", List.of("Turing"));
        TurTranscriptionHints merged =
                global.merge(new TurTranscriptionHints("per-request", List.of()));

        assertThat(merged.prompt()).isEqualTo("per-request");
        assertThat(merged.keywords()).containsExactly("Turing");
    }
}

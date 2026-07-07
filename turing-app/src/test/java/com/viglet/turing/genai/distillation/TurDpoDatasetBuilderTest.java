/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.distillation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.distillation.TurDpoDatasetBuilder.FeedbackEntry;
import com.viglet.turing.genai.distillation.TurDpoDatasetBuilder.PreferencePair;

/** F.9 / §X.10.d — T170 DPO preference pairing + JSONL shape. */
class TurDpoDatasetBuilderTest {

    private static FeedbackEntry up(String prompt, String answer) {
        return new FeedbackEntry(prompt, answer, true);
    }

    private static FeedbackEntry down(String prompt, String answer) {
        return new FeedbackEntry(prompt, answer, false);
    }

    @Test
    void pairsAnUpAndDownForTheSamePrompt() {
        List<PreferencePair> pairs = TurDpoDatasetBuilder.pair(List.of(
                up("How do I reset?", "Click the reset link in settings."),
                down("How do I reset?", "I cannot help with that.")));

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).chosen()).isEqualTo("Click the reset link in settings.");
        assertThat(pairs.get(0).rejected()).isEqualTo("I cannot help with that.");
    }

    @Test
    void promptWithOnlyOneSideContributesNoPair() {
        assertThat(TurDpoDatasetBuilder.pair(List.of(
                up("only ups", "good"),
                up("only ups", "also good")))).isEmpty();
        assertThat(TurDpoDatasetBuilder.pair(List.of(
                down("only downs", "bad")))).isEmpty();
    }

    @Test
    void firstUpAndFirstDownArePairedDeterministically() {
        List<PreferencePair> pairs = TurDpoDatasetBuilder.pair(List.of(
                up("q", "up1"),
                up("q", "up2"),
                down("q", "down1"),
                down("q", "down2")));

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).chosen()).isEqualTo("up1");
        assertThat(pairs.get(0).rejected()).isEqualTo("down1");
    }

    @Test
    void promptsAreNormalizedAndBlanksSkipped() {
        List<PreferencePair> pairs = TurDpoDatasetBuilder.pair(List.of(
                up("  same  ", "good"),
                down("same", "bad"),
                up("", "ignored"),
                down("x", "  ")));
        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).prompt()).isEqualTo("same");
    }

    @Test
    void identicalChosenAndRejectedIsNotAPair() {
        assertThat(TurDpoDatasetBuilder.pair(List.of(
                up("q", "same answer"),
                down("q", "same answer")))).isEmpty();
    }

    @Test
    void buildEmitsOpenAiPreferenceJsonl() {
        String jsonl = TurDpoDatasetBuilder.build(List.of(
                up("q1", "good"),
                down("q1", "bad"),
                up("q2-no-pair", "lonely")));

        String[] lines = jsonl.strip().split("\n");
        assertThat(lines).hasSize(1); // only q1 forms a pair
        assertThat(lines[0])
                .contains("\"input\"")
                .contains("\"messages\"")
                .contains("\"preferred_output\"")
                .contains("\"non_preferred_output\"")
                .contains("\"content\":\"good\"")
                .contains("\"content\":\"bad\"");
    }

    @Test
    void emptyOrNullYieldsEmpty() {
        assertThat(TurDpoDatasetBuilder.pair(null)).isEmpty();
        assertThat(TurDpoDatasetBuilder.build(List.of())).isEmpty();
    }
}

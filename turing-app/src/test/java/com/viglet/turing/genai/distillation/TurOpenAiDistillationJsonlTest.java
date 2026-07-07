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

import com.viglet.turing.genai.distillation.TurOpenAiDistillationJsonl.TrainingExample;
import com.viglet.turing.genai.distillation.TurOpenAiDistillationJsonl.TrainingMessage;

/** F.9 / §X.10.c — T169 fine-tuning training-file (JSONL) shape + usability rules. */
class TurOpenAiDistillationJsonlTest {

    private static TrainingExample ex(TrainingMessage... messages) {
        return new TrainingExample(List.of(messages));
    }

    private static TrainingMessage msg(String role, String content) {
        return new TrainingMessage(role, content);
    }

    @Test
    void usableRequiresAnInputTurnAndATrailingAssistantTurn() {
        assertThat(TurOpenAiDistillationJsonl.isUsable(
                ex(msg("user", "hi"), msg("assistant", "hello")))).isTrue();
        assertThat(TurOpenAiDistillationJsonl.isUsable(
                ex(msg("system", "be nice"), msg("user", "hi"), msg("assistant", "hello")))).isTrue();
    }

    @Test
    void notUsableWhenMissingAssistantOrInput() {
        // no assistant trailing turn
        assertThat(TurOpenAiDistillationJsonl.isUsable(ex(msg("user", "hi")))).isFalse();
        // assistant present but no input turn
        assertThat(TurOpenAiDistillationJsonl.isUsable(ex(msg("assistant", "hello")))).isFalse();
        // trailing turn isn't assistant
        assertThat(TurOpenAiDistillationJsonl.isUsable(
                ex(msg("assistant", "hello"), msg("user", "hi")))).isFalse();
        // blank assistant content
        assertThat(TurOpenAiDistillationJsonl.isUsable(
                ex(msg("user", "hi"), msg("assistant", "  ")))).isFalse();
        assertThat(TurOpenAiDistillationJsonl.isUsable(null)).isFalse();
    }

    @Test
    void buildEmitsOneJsonObjectPerUsableExample() {
        String jsonl = TurOpenAiDistillationJsonl.build(List.of(
                ex(msg("user", "hi"), msg("assistant", "hello")),
                ex(msg("user", "only user")),                       // skipped — not usable
                ex(msg("system", "sys"), msg("user", "q"), msg("assistant", "a"))));

        String[] lines = jsonl.strip().split("\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).contains("\"messages\"")
                .contains("\"role\":\"user\"").contains("\"content\":\"hi\"")
                .contains("\"role\":\"assistant\"");
        assertThat(lines[1]).contains("\"role\":\"system\"");
    }

    @Test
    void buildSkipsBlankContentMessagesWithinAnExample() {
        String jsonl = TurOpenAiDistillationJsonl.build(List.of(
                ex(msg("system", "  "), msg("user", "q"), msg("assistant", "a"))));
        // The blank system message is dropped; user + assistant remain.
        assertThat(jsonl).doesNotContain("\"role\":\"system\"")
                .contains("\"role\":\"user\"").contains("\"role\":\"assistant\"");
    }

    @Test
    void emptyAndAllUnusableYieldEmptyString() {
        assertThat(TurOpenAiDistillationJsonl.build(List.of())).isEmpty();
        assertThat(TurOpenAiDistillationJsonl.build(null)).isEmpty();
        assertThat(TurOpenAiDistillationJsonl.build(List.of(ex(msg("user", "hi"))))).isEmpty();
    }

    @Test
    void usableCountMatchesBuildLines() {
        List<TrainingExample> examples = List.of(
                ex(msg("user", "a"), msg("assistant", "b")),
                ex(msg("user", "c")),
                ex(msg("user", "d"), msg("assistant", "e")));
        assertThat(TurOpenAiDistillationJsonl.usableCount(examples)).isEqualTo(2);
    }
}

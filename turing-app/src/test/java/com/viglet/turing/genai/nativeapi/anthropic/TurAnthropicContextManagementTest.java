/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaContextManagementConfig;
import com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicContextManagement.Options;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;

class TurAnthropicContextManagementTest {

    private final TurAnthropicContextManagement cm =
            new TurAnthropicContextManagement(new TurProviderOptionsParser());

    @Test
    void resolveReadsContextEditingOption() {
        Options options = cm.resolve("{\"context-editing\":\"true\"}");
        assertThat(options.contextEditing()).isTrue();
        assertThat(options.compaction()).isFalse();
        assertThat(options.any()).isTrue();
    }

    @Test
    void resolveBlankOrAbsentIsNone() {
        assertThat(cm.resolve(null)).isEqualTo(Options.NONE);
        assertThat(cm.resolve("")).isEqualTo(Options.NONE);
        assertThat(cm.resolve("{\"citations\":\"true\"}").any()).isFalse();
    }

    @Test
    void noneProducesNoConfigOrBeta() {
        assertThat(cm.betaConfig(Options.NONE)).isEmpty();
        assertThat(cm.rawConfig(Options.NONE)).isEmpty();
        assertThat(cm.betas(Options.NONE)).isEmpty();
        assertThat(cm.rawBetaTokens(Options.NONE)).isEmpty();
    }

    @Test
    void contextEditingProducesClearToolUsesEditAndBeta() {
        Options options = new Options(true, false);
        assertThat(cm.betas(options)).contains(AnthropicBeta.CONTEXT_MANAGEMENT_2025_06_27);
        assertThat(cm.rawBetaTokens(options))
                .contains(TurAnthropicContextManagement.CONTEXT_MGMT_BETA_TOKEN);

        BetaContextManagementConfig config = cm.betaConfig(options).orElseThrow();
        assertThat(config.edits().orElseThrow()).hasSize(1);
        assertThat(config.edits().orElseThrow().get(0).isClearToolUses20250919()).isTrue();

        // Raw form mirrors the typed config for the non-beta Messages builder.
        assertThat(cm.rawConfig(options)).isPresent();
    }

    @Test
    void compactionAddsCompactEditAndBeta() {
        Options options = new Options(false, true);
        BetaContextManagementConfig config = cm.betaConfig(options).orElseThrow();
        assertThat(config.edits().orElseThrow()).hasSize(1);
        assertThat(config.edits().orElseThrow().get(0).isCompact20260112()).isTrue();

        // Compaction rides both betas (context-management + compact).
        assertThat(cm.betas(options)).contains(AnthropicBeta.CONTEXT_MANAGEMENT_2025_06_27).hasSize(2);
        assertThat(cm.rawBetaTokens(options))
                .containsExactlyInAnyOrder(TurAnthropicContextManagement.CONTEXT_MGMT_BETA_TOKEN,
                        TurAnthropicContextManagement.COMPACTION_BETA_TOKEN);
    }

    @Test
    void turingCompressionPrecedenceDisablesCompactionOnly() {
        String json = "{\"context-editing\":\"true\",\"compaction\":\"true\"}";
        // T115 compression off → both levers honored.
        Options without = cm.resolve(json, false);
        assertThat(without.contextEditing()).isTrue();
        assertThat(without.compaction()).isTrue();
        // T115 compression on → compaction forced off, context editing untouched.
        Options with = cm.resolve(json, true);
        assertThat(with.contextEditing()).isTrue();
        assertThat(with.compaction()).isFalse();
    }
}

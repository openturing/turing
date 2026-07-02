/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.rerank;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T337 — lenient parse of {@link TurRagRerankStrategyType#fromValue(String)}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurRagRerankStrategyTypeTest {

    @ParameterizedTest
    @CsvSource({
            "LLM,LLM",
            "llm,LLM",
            "  Cross_Encoder  ,CROSS_ENCODER",
            "COHERE,COHERE",
            "voyage,VOYAGE",
            "VOYAGE,VOYAGE",
    })
    void parsesKnownValuesCaseInsensitively(String input, TurRagRerankStrategyType expected) {
        assertThat(TurRagRerankStrategyType.fromValue(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bogus", "gpt", "rerank-v3.5"})
    void unknownValuesFallBackToLlm(String input) {
        assertThat(TurRagRerankStrategyType.fromValue(input)).isEqualTo(TurRagRerankStrategyType.LLM);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nullOrBlankFallsBackToLlm(String input) {
        assertThat(TurRagRerankStrategyType.fromValue(input)).isEqualTo(TurRagRerankStrategyType.LLM);
    }
}

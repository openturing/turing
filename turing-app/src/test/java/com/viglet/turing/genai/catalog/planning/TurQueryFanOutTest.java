/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.catalog.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * T842 / §LXII.11 — the deterministic multi-query fan-out decomposition.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurQueryFanOutTest {

    private final TurQueryFanOut fanOut = new TurQueryFanOut();

    @Test
    void splitsComparativeQuestionsKeepingTheWholeFirst() {
        List<String> queries = fanOut.decompose("Anthropic vs OpenAI models", 3);

        assertThat(queries.get(0)).isEqualTo("Anthropic vs OpenAI models");
        assertThat(queries).contains("Anthropic", "OpenAI models");
    }

    @Test
    void splitsOnConjunctionsAndCommas() {
        assertThat(fanOut.decompose("price and context window", 3))
                .contains("price and context window", "price", "context window");
        assertThat(fanOut.decompose("Claude, GPT, Gemini", 4))
                .contains("Claude", "GPT", "Gemini");
    }

    @Test
    void aQuestionWithNoMarkerStaysASingleQuery() {
        assertThat(fanOut.decompose("cheapest reasoning model", 3))
                .containsExactly("cheapest reasoning model");
    }

    @Test
    void capsAtMaxQueriesAndHonoursTheSinglePassBound() {
        // Whole + first fragment only, capped at 2 (multi-char terms survive the
        // noise filter that drops single-char fragments).
        assertThat(fanOut.decompose("Claude vs GPT vs Gemini vs Llama", 2)).hasSize(2);
        assertThat(fanOut.decompose("Claude vs GPT", 1)).containsExactly("Claude vs GPT");
    }

    @Test
    void blankQuestionYieldsNoQueries() {
        assertThat(fanOut.decompose("   ", 3)).isEmpty();
        assertThat(fanOut.decompose(null, 3)).isEmpty();
    }
}

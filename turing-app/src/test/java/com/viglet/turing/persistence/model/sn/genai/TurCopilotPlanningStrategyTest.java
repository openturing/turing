/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.sn.genai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T818 / §LIX.1 (Block BK) — the lenient parse must never let a corrupt config value
 * silently start spending LLM calls, and never throw.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurCopilotPlanningStrategyTest {

    @ParameterizedTest
    @CsvSource({
            "DETERMINISTIC, DETERMINISTIC",
            "llm_assisted, LLM_ASSISTED",
            "'  hybrid  ', HYBRID",
    })
    void parsesAStoredValueLeniently(String stored, TurCopilotPlanningStrategy expected) {
        assertThat(TurCopilotPlanningStrategy.fromValue(stored)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "MAGIC", "llm-assisted" })
    void fallsBackToDeterministicForAnUnusableValue(String stored) {
        assertThat(TurCopilotPlanningStrategy.fromValue(stored))
                .isEqualTo(TurCopilotPlanningStrategy.DETERMINISTIC);
    }

    @Test
    void onlyTheLlmStrategiesSpendPasses() {
        assertThat(TurCopilotPlanningStrategy.DETERMINISTIC.usesLlmPasses()).isFalse();
        assertThat(TurCopilotPlanningStrategy.LLM_ASSISTED.usesLlmPasses()).isTrue();
        assertThat(TurCopilotPlanningStrategy.HYBRID.usesLlmPasses()).isTrue();
    }
}

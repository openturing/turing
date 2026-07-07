/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.nativeapi.openai.TurReasoningEffortResolver.Stage;

class TurReasoningEffortResolverTest {

    private final TurReasoningEffortResolver resolver = new TurReasoningEffortResolver();

    @Test
    void perStageDefaultsFollowThePolicy() {
        // T179 / §X.13.b — live=low, background=high, judge=medium.
        assertThat(resolver.resolve(Stage.LIVE, null)).isEqualTo("low");
        assertThat(resolver.resolve(Stage.BACKGROUND, null)).isEqualTo("high");
        assertThat(resolver.resolve(Stage.JUDGE, null)).isEqualTo("medium");
    }

    @Test
    void explicitValueOverridesTheStageDefault() {
        assertThat(resolver.resolve(Stage.LIVE, "high")).isEqualTo("high");
        assertThat(resolver.resolve(Stage.BACKGROUND, "minimal")).isEqualTo("minimal");
        assertThat(resolver.resolve(Stage.JUDGE, "MEDIUM")).isEqualTo("medium");
    }

    @Test
    void blankOrUnknownExplicitFallsThroughToTheStageDefault() {
        assertThat(resolver.resolve(Stage.LIVE, "")).isEqualTo("low");
        assertThat(resolver.resolve(Stage.LIVE, "   ")).isEqualTo("low");
        assertThat(resolver.resolve(Stage.LIVE, "turbo")).isEqualTo("low");
        assertThat(resolver.resolve(Stage.BACKGROUND, "nonsense")).isEqualTo("high");
    }
}

/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.safety.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * T516 — lenient parse of {@link TurAnswerGuardrailType}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAnswerGuardrailTypeTest {

    @Test
    void parsesKnownValuesCaseInsensitively() {
        assertThat(TurAnswerGuardrailType.fromValue("bedrock")).isEqualTo(TurAnswerGuardrailType.BEDROCK);
        assertThat(TurAnswerGuardrailType.fromValue("  OPENAI_MODERATION "))
                .isEqualTo(TurAnswerGuardrailType.OPENAI_MODERATION);
        assertThat(TurAnswerGuardrailType.fromValue("Mistral_Moderation"))
                .isEqualTo(TurAnswerGuardrailType.MISTRAL_MODERATION);
    }

    @Test
    void fallsBackToNoneOnUnknownBlankOrNull() {
        assertThat(TurAnswerGuardrailType.fromValue("nope")).isEqualTo(TurAnswerGuardrailType.NONE);
        assertThat(TurAnswerGuardrailType.fromValue("")).isEqualTo(TurAnswerGuardrailType.NONE);
        assertThat(TurAnswerGuardrailType.fromValue(null)).isEqualTo(TurAnswerGuardrailType.NONE);
    }
}

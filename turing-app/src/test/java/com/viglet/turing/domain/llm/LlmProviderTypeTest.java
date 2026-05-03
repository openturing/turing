/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.domain.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LlmProviderTypeTest {

    @Test
    void of_normalisesCaseAndWhitespace() {
        LlmProviderType type = LlmProviderType.of("  OpenAI  ");
        assertThat(type.value()).isEqualTo("openai");
    }

    @Test
    void canonicalConstructor_rejectsBlank() {
        assertThatThrownBy(() -> new LlmProviderType("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void canonicalConstructor_rejectsMixedCase() {
        assertThatThrownBy(() -> new LlmProviderType("OpenAI"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lower-case");
    }

    @Test
    void of_rejectsNull() {
        assertThatThrownBy(() -> LlmProviderType.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isKnown_returnsTrueForBundledPlugins() {
        assertThat(LlmProviderType.of("openai").isKnown()).isTrue();
        assertThat(LlmProviderType.of("anthropic").isKnown()).isTrue();
        assertThat(LlmProviderType.of("ollama").isKnown()).isTrue();
        assertThat(LlmProviderType.of("gemini").isKnown()).isTrue();
        assertThat(LlmProviderType.of("gemini-openai").isKnown()).isTrue();
    }

    @Test
    void isKnown_returnsFalseForCustomPlugins() {
        assertThat(LlmProviderType.of("my-custom-plugin").isKnown()).isFalse();
    }

    @Test
    void toStringReturnsValue() {
        assertThat(LlmProviderType.of("openai")).hasToString("openai");
    }

    @Test
    void recordEqualityHonoursValue() {
        assertThat(LlmProviderType.of("openai")).isEqualTo(LlmProviderType.of("openai"));
        assertThat(LlmProviderType.of("openai")).isNotEqualTo(LlmProviderType.of("anthropic"));
    }
}

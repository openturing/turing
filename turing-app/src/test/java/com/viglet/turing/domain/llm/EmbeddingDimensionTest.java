/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.domain.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmbeddingDimensionTest {

    @Test
    void of_acceptsTypicalProductionDimensions() {
        assertThat(EmbeddingDimension.of(384).value()).isEqualTo(384);
        assertThat(EmbeddingDimension.of(1536).value()).isEqualTo(1536);
        assertThat(EmbeddingDimension.of(3072).value()).isEqualTo(3072);
    }

    @Test
    void canonicalConstructor_rejectsZero() {
        assertThatThrownBy(() -> new EmbeddingDimension(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void canonicalConstructor_rejectsNegative() {
        assertThatThrownBy(() -> new EmbeddingDimension(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void canonicalConstructor_rejectsAboveMax() {
        assertThatThrownBy(() -> new EmbeddingDimension(EmbeddingDimension.MAX + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum");
    }

    @Test
    void canonicalConstructor_acceptsExactlyMax() {
        assertThat(EmbeddingDimension.of(EmbeddingDimension.MAX).value())
                .isEqualTo(EmbeddingDimension.MAX);
    }
}

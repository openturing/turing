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

/**
 * T790 / §LIV.1 (Block BF) — the knowledge-base mode enum's lenient parse and its
 * two capability predicates (does it expose the vectorless copilot, does it need
 * the vector setup).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNKnowledgeBaseModeTest {

    @Test
    void fromValue_defaultsToVectorOnUnknownBlankOrNull() {
        assertThat(TurSNKnowledgeBaseMode.fromValue(null)).isEqualTo(TurSNKnowledgeBaseMode.VECTOR);
        assertThat(TurSNKnowledgeBaseMode.fromValue("")).isEqualTo(TurSNKnowledgeBaseMode.VECTOR);
        assertThat(TurSNKnowledgeBaseMode.fromValue("  ")).isEqualTo(TurSNKnowledgeBaseMode.VECTOR);
        assertThat(TurSNKnowledgeBaseMode.fromValue("nonsense")).isEqualTo(TurSNKnowledgeBaseMode.VECTOR);
    }

    @Test
    void fromValue_parsesKnownValuesCaseInsensitively() {
        assertThat(TurSNKnowledgeBaseMode.fromValue("vectorless_structured"))
                .isEqualTo(TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED);
        assertThat(TurSNKnowledgeBaseMode.fromValue(" Hybrid "))
                .isEqualTo(TurSNKnowledgeBaseMode.HYBRID);
        assertThat(TurSNKnowledgeBaseMode.fromValue("VECTOR"))
                .isEqualTo(TurSNKnowledgeBaseMode.VECTOR);
    }

    @Test
    void isVectorless_trueForVectorlessAndHybridOnly() {
        assertThat(TurSNKnowledgeBaseMode.VECTOR.isVectorless()).isFalse();
        assertThat(TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED.isVectorless()).isTrue();
        assertThat(TurSNKnowledgeBaseMode.HYBRID.isVectorless()).isTrue();
    }

    @Test
    void needsVectorSetup_falseForVectorlessOnly() {
        assertThat(TurSNKnowledgeBaseMode.VECTOR.needsVectorSetup()).isTrue();
        assertThat(TurSNKnowledgeBaseMode.HYBRID.needsVectorSetup()).isTrue();
        assertThat(TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED.needsVectorSetup()).isFalse();
    }
}

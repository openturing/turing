/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * T520 — lenient parse of {@link TurRetrievalBackendType}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurRetrievalBackendTypeTest {

    @Test
    void parsesKnownValuesCaseInsensitively() {
        assertThat(TurRetrievalBackendType.fromValue("bedrock_kb"))
                .isEqualTo(TurRetrievalBackendType.BEDROCK_KB);
        assertThat(TurRetrievalBackendType.fromValue(" BUILT_IN "))
                .isEqualTo(TurRetrievalBackendType.BUILT_IN);
    }

    @Test
    void fallsBackToBuiltInOnUnknownBlankOrNull() {
        assertThat(TurRetrievalBackendType.fromValue("nope")).isEqualTo(TurRetrievalBackendType.BUILT_IN);
        assertThat(TurRetrievalBackendType.fromValue("")).isEqualTo(TurRetrievalBackendType.BUILT_IN);
        assertThat(TurRetrievalBackendType.fromValue(null)).isEqualTo(TurRetrievalBackendType.BUILT_IN);
    }
}

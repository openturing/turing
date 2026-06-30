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
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * T520 / §XXVIII.16 — boots the full Spring context to assert the managed
 * retrieval seam is wired and <strong>inert by default</strong>: the Bedrock KB
 * backend bean registers, and with no {@code turing.retrieval.*} config the
 * resolver returns no override (retrieval runs on the built-in index, unchanged).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurRetrievalBackendIT extends AbstractTuringSpringIT {

    @Autowired
    private TurRetrievalBackendResolver retrievalBackendResolver;

    @Autowired
    private TurBedrockKnowledgeBaseBackend bedrockKnowledgeBaseBackend;

    @Test
    void backendBeanRegistersButSeamIsInertByDefault() {
        assertThat(bedrockKnowledgeBaseBackend.getType())
                .isEqualTo(TurRetrievalBackendType.BEDROCK_KB);
        // No knowledge-base id configured → backend not available.
        assertThat(bedrockKnowledgeBaseBackend.isAvailable()).isFalse();
        // Default backend is BUILT_IN → no override → built-in retrieval path.
        assertThat(retrievalBackendResolver.resolveOverride()).isEmpty();
    }
}

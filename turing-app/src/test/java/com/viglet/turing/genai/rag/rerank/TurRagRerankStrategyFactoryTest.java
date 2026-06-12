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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/**
 * T337 — resolution + fallback semantics of {@link TurRagRerankStrategyFactory}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurRagRerankStrategyFactoryTest {

    private static TurRagRerankStrategy stub(TurRagRerankStrategyType type) {
        return new TurRagRerankStrategy() {
            @Override
            public TurRagRerankStrategyType getType() {
                return type;
            }

            @Override
            public List<Document> rerank(TurRagRerankRequest request) {
                return List.of();
            }
        };
    }

    @Test
    void resolvesEachRegisteredType() {
        TurRagRerankStrategy llm = stub(TurRagRerankStrategyType.LLM);
        TurRagRerankStrategy crossEncoder = stub(TurRagRerankStrategyType.CROSS_ENCODER);
        TurRagRerankStrategyFactory factory = new TurRagRerankStrategyFactory(List.of(llm, crossEncoder));

        assertThat(factory.resolve(TurRagRerankStrategyType.LLM)).isSameAs(llm);
        assertThat(factory.resolve(TurRagRerankStrategyType.CROSS_ENCODER)).isSameAs(crossEncoder);
    }

    @Test
    void fallsBackToLlmWhenTypeHasNoImplementation() {
        TurRagRerankStrategy llm = stub(TurRagRerankStrategyType.LLM);
        // COHERE is not registered.
        TurRagRerankStrategyFactory factory = new TurRagRerankStrategyFactory(List.of(llm));

        assertThat(factory.resolve(TurRagRerankStrategyType.COHERE)).isSameAs(llm);
    }
}

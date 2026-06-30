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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import com.viglet.turing.properties.TurRetrievalProperty;

/**
 * T520 — the resolver returns an override only for an explicitly configured AND
 * available managed backend; otherwise retrieval runs on the built-in index.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurRetrievalBackendResolverTest {

    private static TurRetrievalBackend stub(TurRetrievalBackendType type, boolean available) {
        return new TurRetrievalBackend() {
            @Override
            public TurRetrievalBackendType getType() {
                return type;
            }

            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public List<Document> retrieve(TurRetrievalRequest request) {
                return List.of();
            }
        };
    }

    private static TurRetrievalProperty property(String backend) {
        TurRetrievalProperty p = new TurRetrievalProperty();
        p.setBackend(backend);
        return p;
    }

    @Test
    void builtInResolvesToNoOverride() {
        var resolver = new TurRetrievalBackendResolver(property("BUILT_IN"),
                List.of(stub(TurRetrievalBackendType.BEDROCK_KB, true)));
        assertThat(resolver.resolveOverride()).isEmpty();
    }

    @Test
    void unknownBackendResolvesToNoOverride() {
        var resolver = new TurRetrievalBackendResolver(property("nonsense"), List.of());
        assertThat(resolver.resolveOverride()).isEmpty();
    }

    @Test
    void configuredAndAvailableBackendIsOverride() {
        TurRetrievalBackend kb = stub(TurRetrievalBackendType.BEDROCK_KB, true);
        var resolver = new TurRetrievalBackendResolver(property("BEDROCK_KB"), List.of(kb));
        assertThat(resolver.resolveOverride()).containsSame(kb);
    }

    @Test
    void configuredButUnavailableBackendFallsBackToBuiltIn() {
        var resolver = new TurRetrievalBackendResolver(property("BEDROCK_KB"),
                List.of(stub(TurRetrievalBackendType.BEDROCK_KB, false)));
        assertThat(resolver.resolveOverride()).isEmpty();
    }
}

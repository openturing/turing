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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.turing.properties.TurRetrievalProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * T520 / §XXVIII.16 — resolves the configured managed retrieval backend, or
 * {@link Optional#empty()} when retrieval should use the <em>built-in</em> index.
 *
 * <p>This is the single seam the retrieval core consults: when it returns empty
 * (the default {@code BUILT_IN}, or a configured-but-unavailable backend), the
 * built-in vector/hybrid path runs byte-for-byte unchanged. Only an explicitly
 * configured <em>and</em> available managed backend overrides it. Mirrors the
 * fail-safe, opt-in posture of the Block N reranker / Block AD seams.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurRetrievalBackendResolver {

    private final TurRetrievalProperty retrievalProperty;
    private final Map<TurRetrievalBackendType, TurRetrievalBackend> byType =
            new EnumMap<>(TurRetrievalBackendType.class);

    public TurRetrievalBackendResolver(TurRetrievalProperty retrievalProperty,
            List<TurRetrievalBackend> backends) {
        this.retrievalProperty = retrievalProperty;
        for (TurRetrievalBackend backend : backends) {
            byType.put(backend.getType(), backend);
        }
        log.info("[Retrieval] managed retrieval backends registered: {}", byType.keySet());
    }

    /**
     * @return the active override backend, or {@link Optional#empty()} when
     *         retrieval should run on the built-in index (default, unknown type,
     *         or the configured backend is not available).
     */
    public Optional<TurRetrievalBackend> resolveOverride() {
        TurRetrievalBackendType type =
                TurRetrievalBackendType.fromValue(retrievalProperty.getBackend());
        if (type == TurRetrievalBackendType.BUILT_IN) {
            return Optional.empty();
        }
        TurRetrievalBackend backend = byType.get(type);
        if (backend == null || !backend.isAvailable()) {
            log.warn("[Retrieval] backend {} configured but unavailable — using built-in index", type);
            return Optional.empty();
        }
        return Optional.of(backend);
    }
}

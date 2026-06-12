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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * T337 / §XVII.1 — resolves the configured {@link TurRagRerankStrategy},
 * mirroring the {@code TurGenAiLlmProviderFactory} / {@code
 * TurSearchEnginePluginFactory} pattern (collect implementations by their
 * declared type, route by an enum).
 *
 * <p>Falls back to {@link TurRagRerankStrategyType#LLM} when the requested type
 * has no registered implementation — defensive parity with the lenient enum
 * parse, so a config pointing at a strategy whose bean is absent degrades to the
 * always-present legacy ranker instead of throwing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurRagRerankStrategyFactory {

    private final Map<TurRagRerankStrategyType, TurRagRerankStrategy> byType =
            new EnumMap<>(TurRagRerankStrategyType.class);

    public TurRagRerankStrategyFactory(List<TurRagRerankStrategy> strategies) {
        for (TurRagRerankStrategy strategy : strategies) {
            byType.put(strategy.getType(), strategy);
        }
        log.info("[RAG] reranker strategies registered: {}", byType.keySet());
    }

    /**
     * @return the strategy for {@code type}, or the {@code LLM} strategy when
     *         {@code type} has no registered implementation.
     */
    public TurRagRerankStrategy resolve(TurRagRerankStrategyType type) {
        TurRagRerankStrategy strategy = byType.get(type);
        if (strategy == null) {
            log.warn("[RAG] no reranker strategy for {}; falling back to LLM", type);
            strategy = byType.get(TurRagRerankStrategyType.LLM);
        }
        return strategy;
    }
}

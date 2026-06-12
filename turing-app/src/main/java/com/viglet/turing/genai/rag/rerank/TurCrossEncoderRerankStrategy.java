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

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * T338 / §XVII.2 — reranks via a self-hosted cross-encoder behind an HTTP
 * {@code /rerank} endpoint (HuggingFace TEI, Infinity, or Jina-compatible).
 *
 * <p>Purpose-built relevance scorers (e.g. {@code BGE-reranker-v2-m3}) are
 * cheaper, faster (sub-100ms), and more precise than borrowing a generative
 * model. The endpoint + model come from Global Settings
 * ({@code GLOBAL_RAG_SN_RERANK_ENDPOINT} / {@code _MODEL}); a blank endpoint
 * means "not configured" → returns empty so the facade keeps retrieval order.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurCrossEncoderRerankStrategy implements TurRagRerankStrategy {

    private final TurHttpRerankClient httpRerankClient;
    private final TurGlobalSettingsService globalSettingsService;

    public TurCrossEncoderRerankStrategy(TurHttpRerankClient httpRerankClient,
            TurGlobalSettingsService globalSettingsService) {
        this.httpRerankClient = httpRerankClient;
        this.globalSettingsService = globalSettingsService;
    }

    @Override
    public TurRagRerankStrategyType getType() {
        return TurRagRerankStrategyType.CROSS_ENCODER;
    }

    @Override
    public List<Document> rerank(TurRagRerankRequest request) {
        String endpoint = globalSettingsService.getRagSnRerankEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            log.debug("[RAG] cross-encoder reranker: no endpoint configured; keeping retrieval order");
            return List.of();
        }
        String model = globalSettingsService.getRagSnRerankModel();
        List<Document> candidates = request.candidates();
        List<String> texts = new ArrayList<>(candidates.size());
        for (Document doc : candidates) {
            texts.add(doc.getText() == null ? "" : doc.getText());
        }
        List<Integer> order = httpRerankClient.rankIndices(
                endpoint.trim(), null, model, request.query(), texts, request.topK());
        return TurRerankIndexMapper.map(candidates, order, request.topK());
    }
}

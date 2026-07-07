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
 * T507 / §XXVIII.3 — reranks via the managed Voyage AI Rerank API, the retrieval
 * specialist Anthropic itself recommends.
 *
 * <p>The endpoint is fixed ({@code https://api.voyageai.com/v1/rerank}); the
 * model defaults to {@code rerank-2.5} but can be overridden via the shared
 * {@code GLOBAL_RAG_SN_RERANK_MODEL}. The API key is read decrypted from Global
 * Settings ({@code GLOBAL_RAG_SN_RERANK_API_KEY}); a blank key means "not
 * configured" → returns empty so the facade keeps retrieval order.
 *
 * <p>Voyage differs from the Cohere/TEI contract in two ways the shared
 * {@link TurHttpRerankClient} now handles: the result-count parameter is
 * {@code top_k} (not {@code top_n}) and the response envelope is {@code data}
 * (not {@code results}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurVoyageRerankStrategy implements TurRagRerankStrategy {

    private static final String VOYAGE_RERANK_ENDPOINT = "https://api.voyageai.com/v1/rerank";
    private static final String DEFAULT_MODEL = "rerank-2.5";

    private final TurHttpRerankClient httpRerankClient;
    private final TurGlobalSettingsService globalSettingsService;

    public TurVoyageRerankStrategy(TurHttpRerankClient httpRerankClient,
            TurGlobalSettingsService globalSettingsService) {
        this.httpRerankClient = httpRerankClient;
        this.globalSettingsService = globalSettingsService;
    }

    @Override
    public TurRagRerankStrategyType getType() {
        return TurRagRerankStrategyType.VOYAGE;
    }

    @Override
    public List<Document> rerank(TurRagRerankRequest request) {
        String apiKey = globalSettingsService.getRagSnRerankApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("[RAG] Voyage reranker: no API key configured; keeping retrieval order");
            return List.of();
        }
        String configuredModel = globalSettingsService.getRagSnRerankModel();
        String model = (configuredModel == null || configuredModel.isBlank())
                ? DEFAULT_MODEL
                : configuredModel.trim();
        List<Document> candidates = request.candidates();
        List<String> texts = new ArrayList<>(candidates.size());
        for (Document doc : candidates) {
            texts.add(doc.getText() == null ? "" : doc.getText());
        }
        List<Integer> order = httpRerankClient.rankIndices(
                VOYAGE_RERANK_ENDPOINT, apiKey, model, request.query(), texts, request.topK(), "top_k");
        return TurRerankIndexMapper.map(candidates, order, request.topK());
    }
}

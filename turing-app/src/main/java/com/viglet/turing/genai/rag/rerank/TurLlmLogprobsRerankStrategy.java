/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.rerank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import com.openai.client.OpenAIClient;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiResponsesService;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * T180 / §X.13.c — OpenAI-only confidence reranker that folds the model's
 * token-level certainty into the retrieval (BM25 / vector) order.
 *
 * <p>For each candidate it runs a one-shot yes/no relevance classification
 * through the native OpenAI Responses path with {@code top_logprobs} on
 * ({@link TurOpenAiResponsesService#classifyRelevanceProbability}); the
 * log-probability of {@code yes} vs {@code no} is the model's calibrated
 * P(relevant). {@link TurLogprobConfidence#blend} mixes that with the retrieval
 * rank, so a hedged top hit (the model wasn't sure) is demoted and a confident
 * lower hit is promoted — the "the LLM hedged but we still ranked it #1" failure
 * mode the LLM-list reranker (T337) can't see.
 *
 * <p>Resolves its client from the Global-Settings default LLM (the same instance
 * the reranker cache keys on). Fail-open per the {@link
 * com.viglet.turing.genai.rag.TurRagReranker} contract: when the default LLM is
 * not OpenAI / has no key / returns no logprobs it returns an empty list and the
 * facade keeps retrieval order. Opt-in — selected only when Global Settings set
 * the rerank strategy to {@code LLM_LOGPROBS}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurLlmLogprobsRerankStrategy implements TurRagRerankStrategy {

    /**
     * Blend weight on the retrieval signal vs the LLM confidence (0..1). 0.5
     * gives them equal say — the model can override retrieval order only when it
     * is clearly more/less confident than the rank prior suggests.
     */
    private static final double RETRIEVAL_WEIGHT = 0.5;

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurNativeProviderClient nativeProviderClient;
    private final TurOpenAiResponsesService responsesService;

    public TurLlmLogprobsRerankStrategy(TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurNativeProviderClient nativeProviderClient,
            TurOpenAiResponsesService responsesService) {
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.nativeProviderClient = nativeProviderClient;
        this.responsesService = responsesService;
    }

    @Override
    public TurRagRerankStrategyType getType() {
        return TurRagRerankStrategyType.LLM_LOGPROBS;
    }

    @Override
    public List<Document> rerank(TurRagRerankRequest request) {
        List<Document> candidates = request.candidates();
        int topK = request.topK();
        Optional<OpenAIClient> client = resolveOpenAiClient();
        if (client.isEmpty()) {
            log.debug("[RAG] logprobs reranker: default LLM is not an OpenAI instance with a key; "
                    + "keeping retrieval order");
            return List.of();
        }
        TurLLMInstance instance = currentInstance().orElse(null);
        List<Scored> scored = new ArrayList<>(candidates.size());
        for (int rank = 0; rank < candidates.size(); rank++) {
            Document candidate = candidates.get(rank);
            double retrieval = TurLogprobConfidence.reciprocalRankScore(rank);
            double confidence = responsesService
                    .classifyRelevanceProbability(client.get(), instance, request.query(),
                            candidate.getText())
                    .orElse(0.5);
            double blended = TurLogprobConfidence.blend(retrieval, confidence, RETRIEVAL_WEIGHT);
            scored.add(new Scored(candidate, blended));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<Document> reranked = new ArrayList<>(Math.min(topK, scored.size()));
        for (Scored s : scored) {
            reranked.add(s.document());
            if (reranked.size() == topK) {
                break;
            }
        }
        log.debug("[RAG] logprobs reranked {} candidate(s) → top {}", candidates.size(),
                reranked.size());
        return reranked;
    }

    private Optional<OpenAIClient> resolveOpenAiClient() {
        return currentInstance().flatMap(nativeProviderClient::openAi);
    }

    private Optional<TurLLMInstance> currentInstance() {
        String defaultLlmId = globalSettingsService.getDefaultLlmId();
        if (defaultLlmId == null || defaultLlmId.isBlank()) {
            return Optional.empty();
        }
        return llmInstanceRepository.findById(defaultLlmId);
    }

    private record Scored(Document document, double score) {
    }
}

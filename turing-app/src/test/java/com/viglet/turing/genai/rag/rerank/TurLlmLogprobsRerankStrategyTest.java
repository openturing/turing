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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import com.openai.client.OpenAIClient;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiResponsesService;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

@ExtendWith(MockitoExtension.class)
class TurLlmLogprobsRerankStrategyTest {

    @Mock private TurGlobalSettingsService globalSettingsService;
    @Mock private TurLLMInstanceRepository llmInstanceRepository;
    @Mock private TurNativeProviderClient nativeProviderClient;
    @Mock private TurOpenAiResponsesService responsesService;
    @Mock private OpenAIClient openAiClient;

    private TurLlmLogprobsRerankStrategy strategy;

    private final TurLLMInstance instance = new TurLLMInstance();

    @BeforeEach
    void setUp() {
        instance.setId("llm-1");
        strategy = new TurLlmLogprobsRerankStrategy(globalSettingsService, llmInstanceRepository,
                nativeProviderClient, responsesService);
    }

    private static Document doc(String id, String text) {
        return new Document(id, text, java.util.Map.of());
    }

    private TurRagRerankRequest request(List<Document> candidates, int topK) {
        return new TurRagRerankRequest("what is the refund window?", candidates, topK, null);
    }

    @Test
    void promotesTheConfidentCandidateOverAHedgedTopHit() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));
        when(nativeProviderClient.openAi(instance)).thenReturn(Optional.of(openAiClient));

        Document a = doc("a", "Totally unrelated shipping note.");   // rank 0, model hedges/denies
        Document b = doc("b", "Refunds are available within 30 days."); // rank 1, confident yes
        // a: P(relevant)=0.05 (rank 1.0) → blend 0.525; b: P=0.98 (rank 0.5) → blend 0.74.
        when(responsesService.classifyRelevanceProbability(eq(openAiClient), eq(instance), any(),
                eq("Totally unrelated shipping note."))).thenReturn(Optional.of(0.05));
        when(responsesService.classifyRelevanceProbability(eq(openAiClient), eq(instance), any(),
                eq("Refunds are available within 30 days."))).thenReturn(Optional.of(0.98));

        List<Document> reranked = strategy.rerank(request(List.of(a, b), 2));

        assertThat(reranked).extracting(Document::getId).containsExactly("b", "a");
    }

    @Test
    void returnsEmptyWhenDefaultLlmIsNotAnUsableOpenAiInstance() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));
        when(nativeProviderClient.openAi(instance)).thenReturn(Optional.empty());

        List<Document> reranked = strategy.rerank(
                request(List.of(doc("a", "x"), doc("b", "y")), 2));

        // Fail-open: the facade keeps retrieval order when the strategy returns empty.
        assertThat(reranked).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoDefaultLlmConfigured() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        lenient().when(llmInstanceRepository.findById(any())).thenReturn(Optional.empty());

        assertThat(strategy.rerank(request(List.of(doc("a", "x"), doc("b", "y")), 2))).isEmpty();
    }

    @Test
    void honoursTopKTruncation() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));
        when(nativeProviderClient.openAi(instance)).thenReturn(Optional.of(openAiClient));
        when(responsesService.classifyRelevanceProbability(any(), any(), any(), any()))
                .thenReturn(Optional.of(0.5));

        List<Document> reranked = strategy.rerank(
                request(List.of(doc("a", "1"), doc("b", "2"), doc("c", "3")), 2));

        assertThat(reranked).hasSize(2);
    }
}

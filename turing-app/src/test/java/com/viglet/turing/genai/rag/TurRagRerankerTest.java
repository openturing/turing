/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import com.viglet.turing.genai.rag.rerank.TurLlmRerankStrategy;
import com.viglet.turing.genai.rag.rerank.TurRagRerankCache;
import com.viglet.turing.genai.rag.rerank.TurRagRerankStrategyFactory;
import com.viglet.turing.genai.rag.rerank.TurRagRerankStrategyType;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T328 / T337 — unit coverage for {@link TurRagReranker}, the fail-open facade,
 * driven through the real {@link TurLlmRerankStrategy} via the factory. These
 * cases pin the legacy LLM behavior (preserved after the T337 seam refactor).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurRagRerankerTest {

    private final TurGlobalSettingsService settings = mock(TurGlobalSettingsService.class);
    private final TurRagReranker reranker = new TurRagReranker(
            new TurRagRerankStrategyFactory(List.of(new TurLlmRerankStrategy())), settings,
            new TurRagRerankCache());

    TurRagRerankerTest() {
        when(settings.getRagSnRerankStrategy()).thenReturn(TurRagRerankStrategyType.LLM);
        // Cache off for the legacy-behavior cases; the cache path is exercised
        // separately in TurRagRerankCacheTest (the @Cacheable proxy needs a
        // Spring CacheManager, which a plain unit test can't supply).
        when(settings.isRagSnRerankCacheEnabled()).thenReturn(false);
    }

    private static List<Document> docs(int n) {
        List<Document> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(Document.builder().id("d" + i).text("chunk " + i).score(1.0 - i * 0.01).build());
        }
        return list;
    }

    private static ChatModel modelReturning(String text) {
        ChatModel model = mock(ChatModel.class);
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        when(model.call(any(Prompt.class))).thenReturn(response);
        return model;
    }

    @Test
    void reordersAndNarrowsByModelRanking() {
        List<Document> candidates = docs(5);
        // Model says snippets 4 and 2 (1-based) are most relevant.
        ChatModel model = modelReturning("4, 2");

        List<Document> result = reranker.rerank(model, "question", candidates, 2);

        assertThat(result).extracting(Document::getId).containsExactly("d3", "d1");
    }

    @Test
    void parsesIndicesFromNoisyResponse() {
        List<Document> candidates = docs(4);
        ChatModel model = modelReturning("The most relevant are snippet [3] then [1]. Done.");

        List<Document> result = reranker.rerank(model, "question", candidates, 2);

        assertThat(result).extracting(Document::getId).containsExactly("d2", "d0");
    }

    @Test
    void failsOpenToRetrievalOrderOnGarbledResponse() {
        List<Document> candidates = docs(3);
        ChatModel model = modelReturning("no numbers here");

        List<Document> result = reranker.rerank(model, "question", candidates, 2);

        assertThat(result).extracting(Document::getId).containsExactly("d0", "d1");
    }

    @Test
    void failsOpenWhenModelThrows() {
        List<Document> candidates = docs(3);
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));

        List<Document> result = reranker.rerank(model, "question", candidates, 2);

        assertThat(result).extracting(Document::getId).containsExactly("d0", "d1");
    }

    @Test
    void skipsModelCallWhenPoolNotLargerThanK() {
        List<Document> candidates = docs(2);
        ChatModel model = mock(ChatModel.class);

        List<Document> result = reranker.rerank(model, "question", candidates, 5);

        assertThat(result).extracting(Document::getId).containsExactly("d0", "d1");
        // model.call must never have been invoked (no stubbing needed)
    }

    @Test
    void ignoresOutOfRangeIndices() {
        List<Document> candidates = docs(3);
        ChatModel model = modelReturning("9, 2, 99");

        List<Document> result = reranker.rerank(model, "question", candidates, 2);

        assertThat(result).extracting(Document::getId).containsExactly("d1");
    }

    // ---- reorder (T389): full-list permutation, no truncation ------------

    @Test
    void reorderReturnsFullPermutationModelDocsFirstThenTheRest() {
        List<Document> candidates = docs(4);
        // Model ranks snippets 4 and 2 (1-based); the other two must survive at the tail.
        ChatModel model = modelReturning("4, 2");

        List<Document> result = reranker.reorder(model, "question", candidates);

        assertThat(result).extracting(Document::getId).containsExactly("d3", "d1", "d0", "d2");
    }

    @Test
    void reorderFailsOpenToInputOrderOnGarbledResponse() {
        List<Document> candidates = docs(3);
        ChatModel model = modelReturning("no numbers here");

        List<Document> result = reranker.reorder(model, "question", candidates);

        assertThat(result).extracting(Document::getId).containsExactly("d0", "d1", "d2");
    }

    @Test
    void reorderFailsOpenWhenModelThrows() {
        List<Document> candidates = docs(3);
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));

        List<Document> result = reranker.reorder(model, "question", candidates);

        assertThat(result).extracting(Document::getId).containsExactly("d0", "d1", "d2");
    }

    @Test
    void reorderKeepsInputWhenBlankQuery() {
        List<Document> candidates = docs(3);

        List<Document> result = reranker.reorder(mock(ChatModel.class), "  ", candidates);

        assertThat(result).isSameAs(candidates);
    }

    // ---- cache wiring (T341) ---------------------------------------------

    @Test
    void whenCacheEnabledRerankRoutesThroughCacheAndMapsIdsBack() {
        // A cache that returns a fixed ordering without ever touching a strategy
        // — proves the facade consults the cache and re-maps ids → live docs.
        TurRagRerankCache cache = mock(TurRagRerankCache.class);
        when(cache.orderedIds(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(List.of("d3", "d1"));
        TurGlobalSettingsService cachingSettings = mock(TurGlobalSettingsService.class);
        when(cachingSettings.getRagSnRerankStrategy()).thenReturn(TurRagRerankStrategyType.LLM);
        when(cachingSettings.isRagSnRerankCacheEnabled()).thenReturn(true);
        TurRagReranker cachingReranker = new TurRagReranker(
                new TurRagRerankStrategyFactory(List.of(new TurLlmRerankStrategy())), cachingSettings, cache);

        // Model would throw if it were called — it must not be, the cache short-circuits.
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("strategy must not run on cache hit"));

        List<Document> result = cachingReranker.rerank(model, "question", docs(5), 2);

        assertThat(result).extracting(Document::getId).containsExactly("d3", "d1");
    }

    @Test
    void whenCacheReturnsEmptyRerankFailsOpenToRetrievalOrder() {
        TurRagRerankCache cache = mock(TurRagRerankCache.class);
        when(cache.orderedIds(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(List.of());
        TurGlobalSettingsService cachingSettings = mock(TurGlobalSettingsService.class);
        when(cachingSettings.getRagSnRerankStrategy()).thenReturn(TurRagRerankStrategyType.LLM);
        when(cachingSettings.isRagSnRerankCacheEnabled()).thenReturn(true);
        TurRagReranker cachingReranker = new TurRagReranker(
                new TurRagRerankStrategyFactory(List.of(new TurLlmRerankStrategy())), cachingSettings, cache);

        List<Document> result = cachingReranker.rerank(mock(ChatModel.class), "question", docs(4), 2);

        assertThat(result).extracting(Document::getId).containsExactly("d0", "d1");
    }
}

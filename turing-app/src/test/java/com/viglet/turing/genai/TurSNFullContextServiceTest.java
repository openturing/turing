/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import com.viglet.turing.genai.TurSNFullContextService.FullContextDecision;

/**
 * T500 / §X.19 — unit coverage for the full-context (retrieval-free) answering
 * gate: opt-in, token-budget decision, probe-cap guard, and fail-open. Pure
 * logic over a mocked {@link VectorStore} — no live store, no LLM (the T385
 * deterministic-in-CI pattern).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNFullContextServiceTest {

    @Mock
    private VectorStore vectorStore;

    private final TurSNFullContextService service = new TurSNFullContextService();

    private TurGenAiContext context(boolean enabled, int budget) {
        return TurGenAiContext.builder()
                .enabled(true)
                .vectorStore(vectorStore)
                .fullContextEnabled(enabled)
                .fullContextTokenBudget(budget)
                .build();
    }

    private static List<Document> docs(int count, int charsEach) {
        List<Document> list = new ArrayList<>();
        String text = "x".repeat(charsEach);
        for (int i = 0; i < count; i++) {
            list.add(Document.builder().id("d" + i).text(text).build());
        }
        return list;
    }

    @Test
    void disabledNeverEngagesAndDoesNotTouchTheStore() {
        FullContextDecision decision = service.decide(context(false, 800_000), "q");
        assertThat(decision.fullContext()).isFalse();
        assertThat(decision.reason()).isEqualTo("disabled");
        assertThat(service.tryFullContext(context(false, 800_000), "q")).isEmpty();
    }

    @Test
    void engagesWhenCorpusFitsBudgetAndReturnsWholeCorpus() {
        // 100 chunks x 400 chars = 40 000 chars ≈ 10 000 tokens, well under budget.
        List<Document> corpus = docs(100, 400);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(corpus);

        Optional<List<Document>> result = service.tryFullContext(context(true, 800_000), "q");
        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(100);

        FullContextDecision decision = service.decide(context(true, 800_000), "q");
        assertThat(decision.fullContext()).isTrue();
        assertThat(decision.reason()).isEqualTo("fits");
        assertThat(decision.docCount()).isEqualTo(100);
        assertThat(decision.estimatedTokens()).isEqualTo(10_000);
    }

    @Test
    void declinesWhenCorpusExceedsBudget() {
        // 100 chunks x 400 chars ≈ 10 000 tokens; budget 5 000 → over budget.
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(docs(100, 400));

        assertThat(service.tryFullContext(context(true, 5_000), "q")).isEmpty();
        FullContextDecision decision = service.decide(context(true, 5_000), "q");
        assertThat(decision.fullContext()).isFalse();
        assertThat(decision.reason()).isEqualTo("over-budget");
        assertThat(decision.estimatedTokens()).isEqualTo(10_000);
    }

    @Test
    void declinesWhenProbeHitsTheCap() {
        // Returning PROBE_TOPK docs means we can't be sure we have the whole corpus.
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(docs(TurSNFullContextService.PROBE_TOPK, 1));

        assertThat(service.tryFullContext(context(true, Integer.MAX_VALUE), "q")).isEmpty();
        assertThat(service.decide(context(true, Integer.MAX_VALUE), "q").reason())
                .isEqualTo("probe-cap");
    }

    @Test
    void failsOpenWhenProbeThrows() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("store down"));

        assertThat(service.tryFullContext(context(true, 800_000), "q")).isEmpty();
        assertThat(service.decide(context(true, 800_000), "q").reason()).isEqualTo("probe-error");
    }

    @Test
    void probeRequestsEveryChunkWithNoRelevanceFloor() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(docs(3, 10));
        service.tryFullContext(context(true, 800_000), "the user question");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        org.mockito.Mockito.verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getTopK()).isEqualTo(TurSNFullContextService.PROBE_TOPK);
        assertThat(captor.getValue().getSimilarityThreshold()).isEqualTo(0.0);
        assertThat(captor.getValue().getQuery()).isEqualTo("the user question");
    }

    @Test
    void estimateCorpusTokensSumsCharsOverFour() {
        assertThat(TurSNFullContextService.estimateCorpusTokens(List.of())).isZero();
        // 2 docs x 8 chars = 16 chars → (16+3)/4 = 4 tokens.
        assertThat(TurSNFullContextService.estimateCorpusTokens(docs(2, 8))).isEqualTo(4);
    }
}

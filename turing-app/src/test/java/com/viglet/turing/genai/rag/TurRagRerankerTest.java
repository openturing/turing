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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

/**
 * T328 — unit coverage for {@link TurRagReranker}, the opt-in LLM-as-reranker.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurRagRerankerTest {

    private final TurRagReranker reranker = new TurRagReranker();

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
}

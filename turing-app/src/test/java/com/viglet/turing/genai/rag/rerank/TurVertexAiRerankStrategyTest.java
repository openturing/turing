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
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import com.viglet.turing.system.TurGlobalSettingsService;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T521 — the Vertex AI Ranking strategy maps the {@code records[]} response (ids
 * are request indices) into a reranked order and stays fail-open when
 * unconfigured.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurVertexAiRerankStrategyTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Mock
    private TurGlobalSettingsService globalSettingsService;

    @Test
    void getTypeIsVertexAi() {
        assertThat(new TurVertexAiRerankStrategy(globalSettingsService).getType())
                .isEqualTo(TurRagRerankStrategyType.VERTEX_AI);
    }

    @Test
    void emptyWhenNoProjectConfigured() {
        when(globalSettingsService.getRagSnRerankVertexProject()).thenReturn("");
        TurVertexAiRerankStrategy strategy = new TurVertexAiRerankStrategy(globalSettingsService);
        List<Document> result = strategy.rerank(new TurRagRerankRequest("q",
                List.of(Document.builder().id("a").text("x").build()), 5, null));
        assertThat(result).isEmpty();
    }

    @Test
    void orderFromMapsRecordIdsToIndices() {
        TurVertexAiRerankStrategy strategy = new TurVertexAiRerankStrategy(globalSettingsService);
        String json = """
                {"records": [
                    {"id": "2", "score": 0.91},
                    {"id": "0", "score": 0.42},
                    {"id": "7", "score": 0.10}
                ]}
                """;
        assertThat(strategy.orderFrom(MAPPER.readTree(json), 3)).containsExactly(2, 0);
    }

    @Test
    void orderFromToleratesMissingRecords() {
        TurVertexAiRerankStrategy strategy = new TurVertexAiRerankStrategy(globalSettingsService);
        assertThat(strategy.orderFrom(MAPPER.readTree("{}"), 3)).isEmpty();
        assertThat(strategy.orderFrom(null, 3)).isEmpty();
    }
}

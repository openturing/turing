/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.catalog.TurCatalogCopilotService.Retrieval;

@ExtendWith(MockitoExtension.class)
class TurRankingExplainToolServiceTest {

    @Mock
    private TurCatalogCopilotService copilotService;

    @InjectMocks
    private TurRankingExplainToolService service;

    private static TurCatalogCitation citation(int rank, String id, Map<String, Object> explain) {
        return new TurCatalogCitation(rank, id, "Title " + id, null, 1.0, explain);
    }

    private static Retrieval retrieval(List<TurCatalogCitation> citations) {
        return new Retrieval(citations, List.of(), citations.size(), null, true);
    }

    private static Map<String, Object> hybridExplain() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pipeline", "HYBRID_RRF_RERANK");
        m.put("lexicalRank", 5);
        m.put("semanticRank", 2);
        m.put("fusedRank", 3);
        m.put("rrfScore", 0.0333);
        m.put("finalRank", 1);
        m.put("rerankStrategy", "LLM");
        m.put("rerankerDelta", 2);
        return m;
    }

    @Test
    void siteNotFound() {
        when(copilotService.retrieve(any(), any(), any())).thenReturn(Retrieval.notFound());
        assertThat(service.explainRanking("s", "en", "q", "d1")).contains("Site not found");
    }

    @Test
    void requiresIndexAndQuery() {
        assertThat(service.explainRanking("", "en", "q", "d1")).contains("'index'");
        assertThat(service.explainRanking("s", "en", " ", "d1")).contains("'query'");
    }

    @Test
    void noResultsToExplain() {
        when(copilotService.retrieve(any(), any(), any())).thenReturn(retrieval(List.of()));
        assertThat(service.explainRanking("s", "en", "q", "d1")).contains("nothing to explain");
    }

    @Test
    void narratesHybridBreakdownForRequestedDoc() {
        when(copilotService.retrieve(any(), any(), any()))
                .thenReturn(retrieval(List.of(citation(1, "d1", hybridExplain()))));
        String out = service.explainRanking("s", "en", "q", "d1");
        assertThat(out)
                .contains("Keyword (BM25) rank: #5")
                .contains("Semantic (vector) rank: #2")
                .contains("Fused (RRF) rank: #3")
                .contains("RRF fused score: 0.0333")
                .contains("Reranker: LLM")
                .contains("UP 2");
    }

    @Test
    void blankDocumentIdExplainsTopResult() {
        when(copilotService.retrieve(any(), any(), any())).thenReturn(retrieval(List.of(
                citation(1, "top", hybridExplain()),
                citation(2, "second", hybridExplain()))));
        assertThat(service.explainRanking("s", "en", "q", "")).contains("id top");
    }

    @Test
    void documentNotInTopResults() {
        when(copilotService.retrieve(any(), any(), any()))
                .thenReturn(retrieval(List.of(citation(1, "d1", hybridExplain()))));
        String out = service.explainRanking("s", "en", "q", "missing");
        assertThat(out).contains("not in the top results").contains("#1 d1");
    }

    @Test
    void legacyLexicalRankingHasNoBreakdown() {
        when(copilotService.retrieve(any(), any(), any()))
                .thenReturn(retrieval(List.of(citation(2, "d1", null))));
        assertThat(service.explainRanking("s", "en", "q", "d1"))
                .contains("keyword-only").contains("#2");
    }
}

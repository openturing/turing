/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.provider.llm.TurLlmModelCatalog;
import com.viglet.turing.genai.provider.llm.TurLlmModelKind;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata.TurLlmModelBenchmarks;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata.TurLlmModelModalities;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata.TurLlmModelPricing;
import com.viglet.turing.genai.provider.llm.TurLlmModelOption;
import com.viglet.turing.service.llm.advisor.TurLLMModelAdvisorService.AdvisorQuery;
import com.viglet.turing.service.llm.advisor.TurLLMModelAdvisorService.Recommendation;

/**
 * T785 / §LIII.3 — unit tests for the constraint-driven model advisor.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurLLMModelAdvisorServiceTest {

    @Mock
    private TurLlmModelCatalog modelCatalog;

    private TurLLMModelAdvisorService service() {
        return new TurLLMModelAdvisorService(modelCatalog);
    }

    private static TurLlmModelMetadata meta(Integer context, Double price, Double intelligence, String tier,
            List<String> capabilities, List<String> inputModalities) {
        return new TurLlmModelMetadata(
                context, null, null, capabilities,
                inputModalities == null ? null : new TurLlmModelModalities(inputModalities, null),
                price == null ? null : new TurLlmModelPricing(price, price, "USD", true, "litellm", null),
                intelligence == null ? null : new TurLlmModelBenchmarks(intelligence, null),
                null, null, null, null, null, tier, null);
    }

    private static TurLlmModelOption chat(String id, TurLlmModelMetadata meta) {
        return new TurLlmModelOption(id, id, TurLlmModelKind.CHAT, meta);
    }

    private static AdvisorQuery query(List<String> caps, List<String> modalities, Integer minContext,
            Double maxPrice, Double minIntelligence, String minTier) {
        return new AdvisorQuery(caps, modalities, minContext, maxPrice, minIntelligence, minTier, null, null);
    }

    @Test
    void filtersByAllConstraintsAndRanksByIntelligence() {
        TurLlmModelOption vision = chat("vision-big",
                meta(200000, 4.0, 82.0, "Frontier", List.of("tools", "vision"), List.of("text", "image")));
        TurLlmModelOption cheapWeak = chat("cheap-weak",
                meta(128000, 1.0, 55.0, "High", List.of("tools", "vision"), List.of("text", "image")));
        TurLlmModelOption noVision = chat("no-vision",
                meta(200000, 2.0, 90.0, "Frontier", List.of("tools"), List.of("text")));
        TurLlmModelOption smallContext = chat("small-ctx",
                meta(8000, 0.5, 70.0, "High", List.of("tools", "vision"), List.of("text", "image")));
        when(modelCatalog.allModels()).thenReturn(Map.of(
                "openai", List.of(vision, cheapWeak, noVision, smallContext)));

        // Need vision + image input, ≥128k context, ≤ $5/1M, ≥ High tier.
        List<Recommendation> recs = service().recommend(
                query(List.of("vision"), List.of("image"), 128000, 5.0, null, "High"));

        // no-vision (no vision cap), small-ctx (< High? High passes tier but 8k < 128k) excluded.
        assertThat(recs).extracting(Recommendation::modelId).containsExactly("vision-big", "cheap-weak");
        // vision-big first: higher intelligence (82 > 55).
        assertThat(recs.get(0).modelId()).isEqualTo("vision-big");
    }

    @Test
    void enforcesBudgetCeiling() {
        TurLlmModelOption pricey = chat("pricey", meta(128000, 10.0, 90.0, "Frontier", List.of("tools"), null));
        TurLlmModelOption cheap = chat("cheap", meta(128000, 2.0, 60.0, "High", List.of("tools"), null));
        when(modelCatalog.allModels()).thenReturn(Map.of("openai", List.of(pricey, cheap)));

        List<Recommendation> recs = service().recommend(query(null, null, null, 5.0, null, null));

        assertThat(recs).extracting(Recommendation::modelId).containsExactly("cheap");
    }

    @Test
    void enforcesMinIntelligenceAndTier() {
        TurLlmModelOption frontier = chat("frontier", meta(128000, 3.0, 85.0, "Frontier", List.of(), null));
        TurLlmModelOption light = chat("light", meta(128000, 0.2, 30.0, "Light", List.of(), null));
        when(modelCatalog.allModels()).thenReturn(Map.of("openai", List.of(frontier, light)));

        assertThat(service().recommend(query(null, null, null, null, 80.0, null)))
                .extracting(Recommendation::modelId).containsExactly("frontier");
        assertThat(service().recommend(query(null, null, null, null, null, "High")))
                .extracting(Recommendation::modelId).containsExactly("frontier");
    }

    @Test
    void honoursTheKindFilter() {
        TurLlmModelOption chatModel = chat("chat", meta(128000, 2.0, 70.0, "High", List.of(), null));
        TurLlmModelOption embed = new TurLlmModelOption("embed", "embed", TurLlmModelKind.EMBEDDING,
                meta(8000, 0.1, null, "Light", List.of(), null));
        when(modelCatalog.allModels()).thenReturn(Map.of("openai", List.of(chatModel, embed)));

        // Default kind is CHAT → the embedding model is never recommended.
        assertThat(service().recommend(query(null, null, null, null, null, null)))
                .extracting(Recommendation::modelId).containsExactly("chat");
    }

    @Test
    void respectsTheLimit() {
        TurLlmModelOption a = chat("a", meta(128000, 1.0, 90.0, "Frontier", List.of(), null));
        TurLlmModelOption b = chat("b", meta(128000, 1.0, 80.0, "Frontier", List.of(), null));
        TurLlmModelOption c = chat("c", meta(128000, 1.0, 70.0, "Frontier", List.of(), null));
        when(modelCatalog.allModels()).thenReturn(Map.of("openai", List.of(a, b, c)));

        AdvisorQuery limited = new AdvisorQuery(null, null, null, null, null, null, null, 2);
        assertThat(service().recommend(limited)).extracting(Recommendation::modelId).containsExactly("a", "b");
    }
}

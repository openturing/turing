/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.rightsizing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.provider.llm.TurLlmModelCatalog;
import com.viglet.turing.genai.provider.llm.TurLlmModelKind;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata;
import com.viglet.turing.genai.provider.llm.TurLlmModelOption;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;
import com.viglet.turing.service.llm.price.TurLLMPriceService;
import com.viglet.turing.service.llm.rightsizing.TurLLMRightsizingService.RightsizingSuggestion;

/**
 * T786 / §LIII.3 — unit tests for the right-sizing savings advisor.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurLLMRightsizingServiceTest {

    @Mock
    private TurLLMTokenUsageRepository usageRepository;
    @Mock
    private TurLLMPriceService priceService;
    @Mock
    private TurLlmModelCatalog modelCatalog;

    private TurLLMRightsizingService service() {
        return new TurLLMRightsizingService(usageRepository, priceService, modelCatalog);
    }

    private static TurLlmModelMetadata meta(Integer context, List<String> capabilities) {
        return new TurLlmModelMetadata(context, null, null, capabilities, null, null, null, null,
                null, null, null, null, null, null);
    }

    private static TurLlmModelOption chat(String id, TurLlmModelMetadata meta) {
        return new TurLlmModelOption(id, id, TurLlmModelKind.CHAT, meta);
    }

    /** usage row: agentId, vendorId, modelName, costUsd, inputTokens, outputTokens, count. */
    private static Object[] usageRow(String agentId, String vendor, String model,
            double cost, long in, long out, long count) {
        return new Object[] {agentId, vendor, model, cost, in, out, count};
    }

    @Test
    void suggestsCheaperFittingModelWithProjectedSavings() {
        // Agent on 'big-pricey' at $0.02/turn over 1000 turns in a 30d window = $20.
        when(usageRepository.findUsageByAgentAndModel(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.<Object[]>of(usageRow("agent-1", "openai", "big-pricey", 20.0, 1_000_000, 200_000, 1000)));
        // avgIn=1000, avgOut=200.
        when(priceService.computeCost(eq("openai"), eq("big-pricey"), anyLong(), anyLong())).thenReturn(0.02);
        when(priceService.computeCost(eq("openai"), eq("cheap-fit"), anyLong(), anyLong())).thenReturn(0.005);

        TurLlmModelOption current = chat("big-pricey", meta(128000, List.of("tools")));
        TurLlmModelOption cheapFit = chat("cheap-fit", meta(128000, List.of("tools", "vision")));
        when(modelCatalog.allModels()).thenReturn(Map.of("openai", List.of(current, cheapFit)));

        List<RightsizingSuggestion> out = service().suggest(30);

        assertThat(out).singleElement().satisfies(s -> {
            assertThat(s.agentId()).isEqualTo("agent-1");
            assertThat(s.currentModelName()).isEqualTo("big-pricey");
            assertThat(s.suggestedModelId()).isEqualTo("cheap-fit");
            assertThat(s.avgInputTokens()).isEqualTo(1000);
            assertThat(s.avgOutputTokens()).isEqualTo(200);
            assertThat(s.currentMonthlyCostUsd()).isEqualTo(20.0);
            // $0.005 vs $0.02 → 75% cheaper → $15 saved, $5 projected.
            assertThat(s.projectedMonthlySavingsUsd()).isEqualTo(15.0);
            assertThat(s.projectedMonthlyCostUsd()).isEqualTo(5.0);
            assertThat(s.savingsPercent()).isEqualTo(75.0);
        });
    }

    @Test
    void skipsWhenNoCheaperModelCoversCapabilities() {
        when(usageRepository.findUsageByAgentAndModel(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.<Object[]>of(usageRow("agent-1", "openai", "vision-model", 20.0, 1_000_000, 0, 1000)));
        when(priceService.computeCost(eq("openai"), eq("vision-model"), anyLong(), anyLong())).thenReturn(0.02);
        lenient().when(priceService.computeCost(eq("openai"), eq("cheap-text"), anyLong(), anyLong())).thenReturn(0.001);

        TurLlmModelOption current = chat("vision-model", meta(128000, List.of("tools", "vision")));
        // Cheaper but lacks 'vision' → must not be suggested.
        TurLlmModelOption cheapText = chat("cheap-text", meta(128000, List.of("tools")));
        when(modelCatalog.allModels()).thenReturn(Map.of("openai", List.of(current, cheapText)));

        assertThat(service().suggest(30)).isEmpty();
    }

    @Test
    void skipsWhenCandidateContextTooSmall() {
        when(usageRepository.findUsageByAgentAndModel(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.<Object[]>of(usageRow("agent-1", "openai", "big-ctx", 20.0, 100_000_000, 0, 1000)));
        // avgIn=100000 → minContext = 150000; candidate has only 32k.
        when(priceService.computeCost(eq("openai"), eq("big-ctx"), anyLong(), anyLong())).thenReturn(0.02);
        lenient().when(priceService.computeCost(eq("openai"), eq("small-ctx"), anyLong(), anyLong())).thenReturn(0.001);

        TurLlmModelOption current = chat("big-ctx", meta(200000, List.of()));
        TurLlmModelOption smallCtx = chat("small-ctx", meta(32000, List.of()));
        when(modelCatalog.allModels()).thenReturn(Map.of("openai", List.of(current, smallCtx)));

        assertThat(service().suggest(30)).isEmpty();
    }

    @Test
    void skipsUnpricedOrLocalModels() {
        when(usageRepository.findUsageByAgentAndModel(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.<Object[]>of(usageRow("agent-1", "ollama", "llama3", 0.0, 1_000_000, 0, 1000)));
        // windowCost is 0 → skipped before pricing.
        assertThat(service().suggest(30)).isEmpty();
    }
}

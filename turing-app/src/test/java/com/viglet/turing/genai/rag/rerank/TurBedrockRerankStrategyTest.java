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

import software.amazon.awssdk.services.bedrockagentruntime.model.RerankResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankResponse;

/**
 * T521 — the Bedrock rerank strategy reorders by the response's result indices
 * and stays fail-open when unconfigured.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurBedrockRerankStrategyTest {

    @Mock
    private TurGlobalSettingsService globalSettingsService;

    @Test
    void getTypeIsBedrock() {
        assertThat(new TurBedrockRerankStrategy(globalSettingsService).getType())
                .isEqualTo(TurRagRerankStrategyType.BEDROCK);
    }

    @Test
    void emptyWhenNoRegionConfigured() {
        when(globalSettingsService.getRagSnRerankRegion()).thenReturn("");
        TurBedrockRerankStrategy strategy = new TurBedrockRerankStrategy(globalSettingsService);
        List<Document> result = strategy.rerank(new TurRagRerankRequest("q",
                List.of(Document.builder().id("a").text("x").build()), 5, null));
        assertThat(result).isEmpty();
    }

    @Test
    void orderFromExtractsInBoundsIndicesInResponseOrder() {
        TurBedrockRerankStrategy strategy = new TurBedrockRerankStrategy(globalSettingsService);
        RerankResponse response = RerankResponse.builder()
                .results(
                        RerankResult.builder().index(2).relevanceScore(0.9f).build(),
                        RerankResult.builder().index(0).relevanceScore(0.5f).build(),
                        RerankResult.builder().index(9).relevanceScore(0.1f).build()) // out of bounds
                .build();

        assertThat(strategy.orderFrom(response, 3)).containsExactly(2, 0);
    }

    @Test
    void orderFromToleratesEmptyResponse() {
        TurBedrockRerankStrategy strategy = new TurBedrockRerankStrategy(globalSettingsService);
        assertThat(strategy.orderFrom(RerankResponse.builder().build(), 3)).isEmpty();
        assertThat(strategy.orderFrom(null, 3)).isEmpty();
    }
}

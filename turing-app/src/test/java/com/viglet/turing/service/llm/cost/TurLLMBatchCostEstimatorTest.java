/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.service.llm.cost.TurLLMBatchCostEstimator.BatchCostEstimate;
import com.viglet.turing.service.llm.price.TurLLMPriceService;
import com.viglet.turing.service.llm.price.TurLLMPriceService.Rates;

/**
 * T783 / §LIII.3 — unit tests for the batch pre-flight cost projection.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurLLMBatchCostEstimatorTest {

    @Mock
    private TurLLMPriceService priceService;

    private TurLLMBatchCostEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new TurLLMBatchCostEstimator(priceService);
    }

    @Test
    void projectsTotalFromPerItemCostTimesCount() {
        // $0.0075 per item (from the price service) × 1000 items = $7.50.
        when(priceService.computeCost(eq("openai"), eq("gpt-4o"), anyLong(), anyLong())).thenReturn(0.0075);
        when(priceService.rates("openai", "gpt-4o")).thenReturn(new Rates(2.5, 10.0));

        BatchCostEstimate e = estimator.estimate("openai", "gpt-4o", 1000, 1000, 500, null);

        assertThat(e.itemCount()).isEqualTo(1000);
        assertThat(e.perItemCostUsd()).isEqualTo(0.0075);
        assertThat(e.totalCostUsd()).isEqualTo(7.5, within(1e-9));
        assertThat(e.priced()).isTrue();
        assertThat(e.overBudget()).isFalse();
    }

    @Test
    void flagsOverBudget() {
        when(priceService.computeCost(eq("openai"), eq("gpt-4o"), anyLong(), anyLong())).thenReturn(0.01);
        when(priceService.rates("openai", "gpt-4o")).thenReturn(new Rates(2.5, 10.0));

        BatchCostEstimate e = estimator.estimate("openai", "gpt-4o", 1000, 1000, 500, 5.0);

        assertThat(e.totalCostUsd()).isEqualTo(10.0, within(1e-9));
        assertThat(e.budgetUsd()).isEqualTo(5.0);
        assertThat(e.overBudget()).isTrue();
    }

    @Test
    void reportsUnpricedForUnknownOrLocalModel() {
        when(priceService.computeCost(eq("ollama"), eq("llama3"), anyLong(), anyLong())).thenReturn(0.0);
        when(priceService.rates("ollama", "llama3")).thenReturn(Rates.ZERO);

        BatchCostEstimate e = estimator.estimate("ollama", "llama3", 500, 2000, 800, null);

        assertThat(e.totalCostUsd()).isZero();
        assertThat(e.priced()).isFalse();
    }

    @Test
    void clampsNegativeInputsToZero() {
        lenient().when(priceService.computeCost(eq("openai"), eq("gpt-4o"), anyLong(), anyLong())).thenReturn(0.0);
        lenient().when(priceService.rates("openai", "gpt-4o")).thenReturn(new Rates(2.5, 10.0));

        BatchCostEstimate e = estimator.estimate("openai", "gpt-4o", -5, -10, -20, null);

        assertThat(e.itemCount()).isZero();
        assertThat(e.inputTokensPerItem()).isZero();
        assertThat(e.outputTokensPerItem()).isZero();
        assertThat(e.totalCostUsd()).isZero();
    }
}

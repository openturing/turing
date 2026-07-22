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

import org.springframework.stereotype.Service;

import com.viglet.turing.service.llm.price.TurLLMPriceService;
import com.viglet.turing.service.llm.price.TurLLMPriceService.Rates;

/**
 * T783 / §LIII.3 (Block BE) — pre-flight cost estimator for a <em>batch</em> of
 * LLM/embedding calls (an eval dataset run, a research study, a batch
 * re-embedding job). Given a per-item token budget and the item count, it prices
 * the whole run against the catalog-backed {@link TurLLMPriceService} rates (auto-
 * seeded in T777) so a UI can show "this run will cost ~$X.XX — proceed?" before
 * spending, and flag when a projected total blows a supplied budget.
 *
 * <p>Pure projection: it never runs anything or persists. The per-turn chat quote
 * (T162) and the runtime soft caps ({@code TurChatCostBudgetGate}) cover the
 * interactive path; this covers the offline-batch path they don't.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurLLMBatchCostEstimator {

    private final TurLLMPriceService priceService;

    public TurLLMBatchCostEstimator(TurLLMPriceService priceService) {
        this.priceService = priceService;
    }

    /**
     * Projected cost of a batch run.
     *
     * @param itemCount           number of items/calls in the batch
     * @param inputTokensPerItem  estimated input tokens per item
     * @param outputTokensPerItem estimated output tokens per item (0 for embeddings)
     * @param perItemCostUsd      priced cost of a single item
     * @param totalCostUsd        {@code perItemCostUsd * itemCount}
     * @param priced              whether a (non-zero) catalog/manual rate was found
     *                            ({@code false} = unpriced or a $0 local model, so
     *                            the total is $0 and not a real bill)
     * @param budgetUsd           the budget the caller wants checked, or {@code null}
     * @param overBudget          {@code true} when a positive budget is exceeded
     */
    public record BatchCostEstimate(
            long itemCount,
            long inputTokensPerItem,
            long outputTokensPerItem,
            double perItemCostUsd,
            double totalCostUsd,
            boolean priced,
            Double budgetUsd,
            boolean overBudget) {
    }

    /**
     * Estimate the projected USD cost of a batch. Negative inputs are clamped to
     * {@code 0}. Returns a $0, {@code priced=false} estimate for an unknown/local
     * model rather than failing, so a caller always gets a usable number.
     */
    public BatchCostEstimate estimate(String vendorId, String modelName, long itemCount,
            long inputTokensPerItem, long outputTokensPerItem, Double budgetUsd) {
        long items = Math.max(0L, itemCount);
        long inTokens = Math.max(0L, inputTokensPerItem);
        long outTokens = Math.max(0L, outputTokensPerItem);

        double perItem = priceService.computeCost(vendorId, modelName, inTokens, outTokens);
        double total = perItem * items;
        boolean priced = !Rates.ZERO.equals(priceService.rates(vendorId, modelName));
        boolean overBudget = budgetUsd != null && budgetUsd > 0 && total > budgetUsd;

        return new BatchCostEstimate(items, inTokens, outTokens, perItem, total, priced, budgetUsd, overBudget);
    }
}

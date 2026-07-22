/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.llm.cost;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.service.llm.cost.TurLLMBatchCostEstimator;
import com.viglet.turing.service.llm.cost.TurLLMBatchCostEstimator.BatchCostEstimate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T783 / §LIII.3 (Block BE) — projected-cost quote for a batch run (eval dataset,
 * research study, batch re-embed) before it spends. Prices {@code itemCount}
 * items at the given per-item token budget against the catalog-backed rate table
 * so a UI can gate an expensive run behind a "this will cost ~$X — proceed?"
 * confirm. Read-only projection.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/v2/llm/cost")
@Tag(name = "LLM Batch Pre-flight Cost", description = "Block BE — projected batch-run cost")
public class TurLLMPreflightBatchCostAPI {

    private final TurLLMBatchCostEstimator estimator;

    public TurLLMPreflightBatchCostAPI(TurLLMBatchCostEstimator estimator) {
        this.estimator = estimator;
    }

    /** Batch estimate request; {@code budgetUsd} is optional (null = no budget check). */
    public record BatchEstimateRequest(
            String vendorId,
            String modelName,
            long itemCount,
            long inputTokensPerItem,
            long outputTokensPerItem,
            Double budgetUsd) {
    }

    @Operation(summary = "Quote the projected cost of a batch run before it spends")
    @PostMapping("/preflight-batch")
    public BatchCostEstimate quoteBatch(@RequestBody BatchEstimateRequest request) {
        return estimator.estimate(
                request.vendorId(),
                request.modelName(),
                request.itemCount(),
                request.inputTokensPerItem(),
                request.outputTokensPerItem(),
                request.budgetUsd());
    }
}

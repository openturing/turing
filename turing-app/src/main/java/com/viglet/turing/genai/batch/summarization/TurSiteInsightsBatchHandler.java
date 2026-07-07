/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.batch.summarization;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.batch.TurBatchChatResult;
import com.viglet.turing.genai.batch.TurBatchCompletionHandler;
import com.viglet.turing.persistence.model.batch.TurBatchJob;
import com.viglet.turing.system.TurLlmCacheService;

import lombok.extern.slf4j.Slf4j;

/**
 * F.7 / §X.8.b — consumes a completed per-site AI Insights batch.
 *
 * <p>The nightly job ({@link TurNightlyBatchSummarizationJob}) submits one
 * request per site with {@code customId == siteId}; each successful result is
 * written into {@link TurLlmCacheService} under that same key — the exact key
 * {@code TurLlmSummaryService.generate} reads — so the next operator who opens a
 * site's insights gets a warm cache hit instead of a synchronous (full-price)
 * LLM call.
 *
 * <p>Caveat: {@link TurLlmCacheService} is an in-process cache and the poller is
 * cluster-wide-once, so the warm entry lands on the node that ran the poller;
 * other nodes simply miss and fall back to synchronous generation. That is a
 * cost optimization, not a correctness contract.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurSiteInsightsBatchHandler implements TurBatchCompletionHandler {

    /** Shared purpose tag — must match what {@link TurNightlyBatchSummarizationJob} submits. */
    public static final String PURPOSE = "site-insights";

    private final TurLlmCacheService llmCacheService;

    public TurSiteInsightsBatchHandler(TurLlmCacheService llmCacheService) {
        this.llmCacheService = llmCacheService;
    }

    @Override
    public String purpose() {
        return PURPOSE;
    }

    @Override
    public void onBatchComplete(TurBatchJob job, List<TurBatchChatResult> results) {
        int warmed = 0;
        for (TurBatchChatResult result : results) {
            if (result.success() && StringUtils.hasText(result.content())
                    && StringUtils.hasText(result.customId())) {
                llmCacheService.put(result.customId(), result.content());
                warmed++;
            }
        }
        log.info("[Batch][Insights] job {} warmed {}/{} site insight cache entries",
                job.getId(), warmed, results.size());
    }
}

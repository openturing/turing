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

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.batch.TurBatchChatRequest;
import com.viglet.turing.genai.batch.TurBatchInferenceService;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.sn.TurSNSiteInsightsPromptBuilder;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * F.7 / §X.8.b — nightly pre-warm of every site's AI Insights through the Batch
 * tier (50% off) instead of synchronous, full-price LLM calls.
 *
 * <p>Each night it builds the same insight prompt the on-demand endpoint uses
 * ({@link TurSNSiteInsightsPromptBuilder}) for every SN site and submits them as
 * a single Batch job (chunked at the configured cap). When the batch ends —
 * potentially hours later — {@link TurSiteInsightsBatchHandler} writes each
 * result into the LLM cache under the site id, so the operator's next visit is a
 * warm hit rather than a synchronous generation.
 *
 * <p>Triple-gated and fail-safe: requires {@code turing.batch.enabled} +
 * {@code turing.batch.summarization.enabled}, a configured + enabled default LLM
 * whose vendor exposes a Batch API. Any gate unmet → the job is a no-op and the
 * existing on-demand synchronous path is untouched.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurNightlyBatchSummarizationJob {

    private final TurConfigProperties configProperties;
    private final TurBatchInferenceService batchInferenceService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurSNSiteRepository snSiteRepository;
    private final TurSNSiteInsightsPromptBuilder insightsPromptBuilder;
    private final com.viglet.turing.system.lane.TurModelLaneService modelLaneService;
    private final TransactionTemplate readOnlyTransactionTemplate;

    public TurNightlyBatchSummarizationJob(TurConfigProperties configProperties,
            TurBatchInferenceService batchInferenceService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurSNSiteRepository snSiteRepository,
            TurSNSiteInsightsPromptBuilder insightsPromptBuilder,
            com.viglet.turing.system.lane.TurModelLaneService modelLaneService,
            PlatformTransactionManager transactionManager) {
        this.configProperties = configProperties;
        this.batchInferenceService = batchInferenceService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.snSiteRepository = snSiteRepository;
        this.insightsPromptBuilder = insightsPromptBuilder;
        this.modelLaneService = modelLaneService;
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
    }

    @Scheduled(cron = "${turing.batch.summarization.cron:0 30 3 * * *}")
    @SchedulerLock(name = "nightlyBatchSummarization",
            lockAtMostFor = "PT30M", lockAtLeastFor = "PT1S")
    public void run() {
        if (!configProperties.getBatch().isEnabled()
                || !configProperties.getBatch().getSummarization().isEnabled()) {
            return;
        }
        TurLLMInstance instance = resolveDefaultInstance();
        if (instance == null) {
            log.debug("[Batch][Insights] no enabled default LLM — skipping nightly summarization");
            return;
        }
        if (!batchInferenceService.isSupported(instance)) {
            log.info("[Batch][Insights] default LLM '{}' has no Batch API — skipping nightly summarization",
                    instance.getId());
            return;
        }

        List<String> siteIds = snSiteRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream().map(TurSNSite::getId).toList();
        if (siteIds.isEmpty()) {
            return;
        }

        List<TurBatchChatRequest> requests = new ArrayList<>(siteIds.size());
        for (String siteId : siteIds) {
            // T488 / §XXVIII.3 — build inside a read-only transaction so the prompt
            // builder can dereference the site's lazy associations (GenAI graph,
            // fieldExts, locales) through the open session; repositories are uncached.
            TurSNSiteInsightsPromptBuilder.InsightsPrompt prompt = readOnlyTransactionTemplate.execute(status ->
                    snSiteRepository.findByIdWithGenAi(siteId)
                            .map(insightsPromptBuilder::build)
                            .orElse(null));
            if (prompt != null && StringUtils.hasText(prompt.userData())) {
                requests.add(new TurBatchChatRequest(prompt.cacheKey(), prompt.systemPrompt(),
                        prompt.userData(), null, null, null));
            }
        }

        int max = Math.max(1, configProperties.getBatch().getMaxRequestsPerBatch());
        int submitted = 0;
        for (int from = 0; from < requests.size(); from += max) {
            List<TurBatchChatRequest> chunk = requests.subList(from, Math.min(from + max, requests.size()));
            if (batchInferenceService.submit(instance, TurSiteInsightsBatchHandler.PURPOSE,
                    new ArrayList<>(chunk), null).isPresent()) {
                submitted += chunk.size();
            }
        }
        log.info("[Batch][Insights] submitted {} site insight request(s) across batch job(s)", submitted);
    }

    private TurLLMInstance resolveDefaultInstance() {
        // T517 — nightly insights are background work: ride the CHEAP model lane
        // (resolves to the default LLM when the lane is unbound).
        String llmId = modelLaneService.resolveInstanceId(
                com.viglet.turing.system.lane.TurModelStage.BACKGROUND_SUMMARIZATION);
        if (!StringUtils.hasText(llmId)) {
            return null;
        }
        return llmInstanceRepository.findById(llmId)
                .filter(instance -> instance.getEnabled() == 1)
                .orElse(null);
    }
}

/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.batch;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.batch.TurBatchJob;
import com.viglet.turing.persistence.repository.batch.TurBatchJobRepository;
import com.viglet.turing.properties.TurConfigProperties;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * F.7 / §X.8.a — the cluster-wide-once poller that drives in-flight
 * {@link TurBatchJob}s to completion.
 *
 * <p>On each tick (cron {@code turing.batch.poll-cron}, ShedLock so only one node
 * runs it) it refreshes every uncollected job's status from its vendor. When a
 * job ends it fetches the results, routes them to the
 * {@link TurBatchCompletionHandler} registered for the job's purpose, and marks
 * the job collected so it is never reprocessed. A failed / expired / cancelled
 * batch is marked collected too (no results to dispatch) with its error recorded.
 *
 * <p>Handler exceptions are logged and isolated per job — one bad handler never
 * blocks the rest of the queue.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurBatchJobPoller {

    private final TurBatchInferenceService batchInferenceService;
    private final TurBatchJobRepository batchJobRepository;
    private final TurConfigProperties configProperties;
    private final Map<String, TurBatchCompletionHandler> handlersByPurpose;
    private final Map<String, TurBatchEmbeddingCompletionHandler> embeddingHandlersByPurpose;

    public TurBatchJobPoller(TurBatchInferenceService batchInferenceService,
            TurBatchJobRepository batchJobRepository,
            TurConfigProperties configProperties,
            List<TurBatchCompletionHandler> handlers,
            List<TurBatchEmbeddingCompletionHandler> embeddingHandlers) {
        this.batchInferenceService = batchInferenceService;
        this.batchJobRepository = batchJobRepository;
        this.configProperties = configProperties;
        this.handlersByPurpose = handlers.stream().collect(Collectors.toMap(
                handler -> handler.purpose().toLowerCase(Locale.ROOT),
                Function.identity()));
        this.embeddingHandlersByPurpose = embeddingHandlers.stream().collect(Collectors.toMap(
                handler -> handler.purpose().toLowerCase(Locale.ROOT),
                Function.identity()));
    }

    @Scheduled(cron = "${turing.batch.poll-cron:0 */5 * * * *}")
    @SchedulerLock(name = "batchJobPoller", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1S")
    public void poll() {
        if (!configProperties.getBatch().isEnabled()) {
            return;
        }
        List<TurBatchJob> open = batchJobRepository.findByResultsCollectedFalseOrderByCreatedAtAsc();
        if (open.isEmpty()) {
            return;
        }
        log.debug("[Batch] polling {} open job(s)", open.size());
        for (TurBatchJob job : open) {
            try {
                pollOne(job);
            } catch (Exception e) {
                log.warn("[Batch] failed to poll job {} ({}/{})", job.getId(),
                        job.getPluginType(), job.getVendorBatchId(), e);
            }
        }
    }

    private void pollOne(TurBatchJob job) {
        TurBatchStatus status = batchInferenceService.refresh(job);
        if (!status.state().isTerminal()) {
            return;
        }
        if (status.state() == TurBatchState.COMPLETED) {
            if (job.getJobKind() == TurBatchKind.EMBEDDING) {
                dispatchEmbedding(job);
            } else {
                dispatchChat(job);
            }
        } else {
            job.setErrorMessage("Batch ended in state " + status.state());
            finish(job);
            log.info("[Batch] job {} ({}) ended without results: {}",
                    job.getId(), job.getPurpose(), status.state());
        }
    }

    private void dispatchChat(TurBatchJob job) {
        List<TurBatchChatResult> results = batchInferenceService.fetchResults(job);
        TurBatchCompletionHandler handler =
                handlersByPurpose.get(job.getPurpose().toLowerCase(Locale.ROOT));
        if (handler == null) {
            log.warn("[Batch] no chat completion handler for purpose '{}' (job {}) — results dropped",
                    job.getPurpose(), job.getId());
        } else {
            try {
                handler.onBatchComplete(job, results);
            } catch (Exception e) {
                log.error("[Batch] handler '{}' failed for job {}", job.getPurpose(), job.getId(), e);
                job.setErrorMessage("Handler error: " + e.getMessage());
            }
        }
        finish(job);
        log.info("[Batch] chat job {} ({}) completed: {} ok, {} failed",
                job.getId(), job.getPurpose(), job.getCompletedRequests(), job.getFailedRequests());
    }

    private void dispatchEmbedding(TurBatchJob job) {
        List<TurBatchEmbeddingResult> results = batchInferenceService.fetchEmbeddingResults(job);
        TurBatchEmbeddingCompletionHandler handler =
                embeddingHandlersByPurpose.get(job.getPurpose().toLowerCase(Locale.ROOT));
        if (handler == null) {
            log.warn("[Batch] no embedding completion handler for purpose '{}' (job {}) — results dropped",
                    job.getPurpose(), job.getId());
        } else {
            try {
                handler.onBatchComplete(job, results);
            } catch (Exception e) {
                log.error("[Batch] embedding handler '{}' failed for job {}",
                        job.getPurpose(), job.getId(), e);
                job.setErrorMessage("Handler error: " + e.getMessage());
            }
        }
        finish(job);
        log.info("[Batch] embedding job {} ({}) completed: {} ok, {} failed",
                job.getId(), job.getPurpose(), job.getCompletedRequests(), job.getFailedRequests());
    }

    private void finish(TurBatchJob job) {
        job.setResultsCollected(true);
        job.setCompletedAt(Instant.now());
        batchJobRepository.save(job);
    }
}

/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.system.batch;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.batch.TurBatchJob;
import com.viglet.turing.persistence.repository.batch.TurBatchJobRepository;
import com.viglet.turing.properties.TurConfigProperties;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * F.7 / §X.8.a — read-only operator window into the Batch tier.
 *
 * <p>Surfaces whether the tier is enabled, how many jobs are still in flight,
 * and a recent-jobs list for the admin batch console. Deliberately omits the
 * job's {@code contextJson} (handler-private payload) from the response.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/system/batch")
@Tag(name = "Batch Inference",
        description = "Read API over the Batch inference job registry (F.7 §X.8).")
public class TurBatchJobAPI {

    private final TurBatchJobRepository batchJobRepository;
    private final TurConfigProperties configProperties;

    public TurBatchJobAPI(TurBatchJobRepository batchJobRepository,
            TurConfigProperties configProperties) {
        this.batchJobRepository = batchJobRepository;
        this.configProperties = configProperties;
    }

    public record BatchStatusView(boolean enabled, long openJobs) {
    }

    public record BatchJobView(
            String id,
            String purpose,
            String pluginType,
            String state,
            long totalRequests,
            long completedRequests,
            long failedRequests,
            boolean resultsCollected,
            String errorMessage,
            Instant createdAt,
            Instant completedAt) {

        static BatchJobView of(TurBatchJob job) {
            return new BatchJobView(job.getId(), job.getPurpose(), job.getPluginType(),
                    job.getState() == null ? null : job.getState().name(),
                    job.getTotalRequests(), job.getCompletedRequests(), job.getFailedRequests(),
                    job.isResultsCollected(), job.getErrorMessage(),
                    job.getCreatedAt(), job.getCompletedAt());
        }
    }

    @GetMapping("/status")
    public BatchStatusView status() {
        return new BatchStatusView(configProperties.getBatch().isEnabled(),
                batchJobRepository.countByResultsCollectedFalse());
    }

    @GetMapping("/jobs")
    public List<BatchJobView> jobs(@RequestParam(defaultValue = "50") int limit) {
        int capped = Math.min(Math.max(limit, 1), 200);
        return batchJobRepository.findByOrderByCreatedAtDesc(PageRequest.of(0, capped))
                .stream().map(BatchJobView::of).toList();
    }
}

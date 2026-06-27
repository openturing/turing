/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.batch;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.batch.TurBatchJob;

/**
 * F.7 / §X.8.a — tracked Batch inference jobs.
 *
 * <p>No {@code @Cacheable}: the only hot reader is the poller, which by
 * definition wants fresh rows, and the operator surface (batch console) is
 * cold. Mirrors {@code TurChatCitationRecordRepository}'s rationale.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurBatchJobRepository extends JpaRepository<TurBatchJob, String> {

    /** Open jobs the poller must still refresh / collect, oldest first. */
    List<TurBatchJob> findByResultsCollectedFalseOrderByCreatedAtAsc();

    /** Recent jobs for a workload, newest first (operator console / consumers). */
    List<TurBatchJob> findByPurposeOrderByCreatedAtDesc(String purpose, Pageable pageable);

    /** Most recent jobs across all workloads (operator console). */
    List<TurBatchJob> findByOrderByCreatedAtDesc(Pageable pageable);

    long countByResultsCollectedFalse();
}

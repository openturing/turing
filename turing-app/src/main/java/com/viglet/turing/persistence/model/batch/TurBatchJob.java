/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.batch;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.viglet.turing.genai.batch.TurBatchKind;
import com.viglet.turing.genai.batch.TurBatchState;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * F.7 / §X.8.a — one tracked Batch inference job.
 *
 * <p>{@link com.viglet.turing.genai.batch.TurBatchInferenceService#submit} writes
 * a row here the moment a batch is accepted by a vendor; the cluster-wide-once
 * {@code TurBatchJobPoller} (ShedLock) refreshes its {@link #state} and per-request
 * counts on a schedule, and when the batch ends it fetches the results and hands
 * them to the {@code TurBatchCompletionHandler} registered for the job's
 * {@link #purpose}. The row is therefore the durable hand-off between the
 * fire-and-forget submission and the deferred (up to 24h) completion — without
 * it a process restart would orphan an in-flight batch.
 *
 * <p>{@link #contextJson} is handler-private state (e.g. the list of site ids a
 * summarization batch covers) so the handler can reattach results to its domain
 * objects when the batch finally completes.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "batch_job",
        indexes = {
                @Index(name = "ix_batch_job_open", columnList = "resultsCollected,state"),
                @Index(name = "ix_batch_job_purpose", columnList = "purpose")
        })
public class TurBatchJob implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** The LLM instance the batch was submitted against. */
    @Column(name = "instanceId", nullable = false, length = 36)
    private String instanceId;

    /** Vendor plugin type ({@code openai} / {@code anthropic}). */
    @Column(name = "pluginType", nullable = false, length = 32)
    private String pluginType;

    /** The vendor's batch id (OpenAI {@code batch_...} / Anthropic {@code msgbatch_...}). */
    @Column(name = "vendorBatchId", nullable = false, length = 128)
    private String vendorBatchId;

    /** Logical workload key, routes completion to a {@code TurBatchCompletionHandler}. */
    @Column(name = "purpose", nullable = false, length = 64)
    private String purpose;

    /** Whether this batch carries chat or embedding work — decides the result fetch on completion. */
    @Enumerated(EnumType.STRING)
    @Column(name = "jobKind", nullable = false, length = 16)
    private TurBatchKind jobKind = TurBatchKind.CHAT;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private TurBatchState state = TurBatchState.VALIDATING;

    @Column(name = "totalRequests")
    private long totalRequests;

    @Column(name = "completedRequests")
    private long completedRequests;

    @Column(name = "failedRequests")
    private long failedRequests;

    /** Handler-private context to reattach results to domain objects on completion. */
    @Lob
    @Column(name = "contextJson", columnDefinition = "longtext")
    private String contextJson;

    /** True once the poller has fetched results (or recorded a terminal failure). */
    @Column(name = "resultsCollected", nullable = false)
    private boolean resultsCollected = false;

    @Column(name = "errorMessage", length = 1024)
    private String errorMessage;

    @Column(name = "createdAt", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "lastPolledAt")
    private Instant lastPolledAt;

    @Column(name = "completedAt")
    private Instant completedAt;
}

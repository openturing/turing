/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.distillation;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.viglet.turing.genai.distillation.TurDistillationState;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * F.9 / §X.10.c — one tracked distillation job (T169).
 *
 * <p>{@code TurOpenAiDistillationService.distill} writes a row here the moment a
 * fine-tuning job is accepted by OpenAI; the cluster-wide-once
 * {@code TurDistillationJobPoller} (ShedLock) refreshes its {@link #state} on a
 * schedule and, when the fine-tune succeeds, runs the agent eval gate against
 * the fine-tuned candidate and — only if it passes — swaps the LLM instance's
 * model to {@link #fineTunedModel}, recording {@link #originalModel} so a failed
 * eval reverts cleanly. The row is the durable hand-off between the
 * fire-and-forget submission and the deferred (hours-later) completion.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "distillation_job",
        indexes = {
                @Index(name = "ix_distillation_job_open", columnList = "state"),
                @Index(name = "ix_distillation_job_agent", columnList = "agentId")
        })
public class TurDistillationJob implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** The agent whose Stored Completions were distilled. */
    @Column(name = "agentId", nullable = false, length = 36)
    private String agentId;

    /** The LLM instance whose model is swapped when the eval passes. */
    @Column(name = "instanceId", nullable = false, length = 36)
    private String instanceId;

    /** Base model the fine-tune trains from (e.g. {@code gpt-4o-mini}). */
    @Column(name = "baseModel", nullable = false, length = 128)
    private String baseModel;

    /** OpenAI training file id ({@code file-...}). */
    @Column(name = "trainingFileId", length = 128)
    private String trainingFileId;

    /** OpenAI fine-tuning job id ({@code ftjob-...}). */
    @Column(name = "fineTuneJobId", length = 128)
    private String fineTuneJobId;

    /** The produced fine-tuned model id ({@code ft:gpt-4o-mini:...}), once succeeded. */
    @Column(name = "fineTunedModel", length = 128)
    private String fineTunedModel;

    /** The instance model before the swap, so a failed eval can revert it. */
    @Column(name = "originalModel", length = 128)
    private String originalModel;

    /** Number of training examples exported. */
    @Column(name = "exampleCount")
    private Integer exampleCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 24)
    private TurDistillationState state = TurDistillationState.RUNNING;

    @Column(name = "errorMessage", length = 1024)
    private String errorMessage;

    /**
     * T191 / §X.15.e — when true this is an overnight "propose-only" run: on a
     * passing eval gate the candidate is surfaced as a {@code TurAgentSuggestion}
     * for human approval and the model is NOT applied. Default false = the legacy
     * T169 auto-swap behaviour.
     */
    @Column(name = "proposeOnly", nullable = false)
    private boolean proposeOnly = false;

    @Column(name = "createdAt", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "lastPolledAt")
    private Instant lastPolledAt;

    @Column(name = "completedAt")
    private Instant completedAt;
}

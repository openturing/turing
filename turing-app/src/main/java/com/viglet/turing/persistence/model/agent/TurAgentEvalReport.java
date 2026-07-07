/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.agent;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T286 / §XV.2 — an immutable record of one eval-set run. Persisted so the
 * pre-publish gate (T287) can compare the newest run against the last
 * <b>green baseline</b> and surface regressions. The per-case breakdown lives
 * in {@code resultsJson} (a serialized list of
 * {@code TurAgentEvalCaseResultDto}) to keep the table narrow.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@Entity
@Table(name = "tur_agent_eval_report")
public class TurAgentEvalReport implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** T261 / §XIV.2.5 — multi-tenancy discriminator (see TurSNSite pilot). Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @jakarta.persistence.Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    /** True when every case passed (the run is "green"). */
    @Column(name = "passed", nullable = false)
    private boolean passed;

    /** Aggregate score in [0,1] — mean of the per-case scores. */
    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "caseCount", nullable = false)
    private int caseCount;

    @Column(name = "passedCount", nullable = false)
    private int passedCount;

    /**
     * True when this report is the agent's current green baseline — the
     * yardstick the gate compares newer runs against. Exactly zero or one
     * baseline per agent at a time.
     */
    @Column(name = "baseline", nullable = false)
    private boolean baseline;

    /**
     * True when this run regressed against the baseline (a case that was
     * green in the baseline flipped to red). Drives the gate's ERROR rows.
     */
    @Column(name = "regressed", nullable = false)
    private boolean regressed;

    /**
     * T592 — true when any case in this run deferred to a HUMAN grader, so the
     * run is amber (awaiting review) rather than green/red until a reviewer
     * decides. A pending run is never the green baseline.
     */
    @Column(name = "pendingReview", nullable = false)
    private boolean pendingReview;

    /**
     * T598 — the bound dataset (if any) + the dataset version this run scored
     * against, so a regression comparison knows whether the dataset itself
     * drifted between runs. {@code datasetVersion} is 0 for inline-case runs.
     */
    @Column(name = "datasetId", length = 36)
    private String datasetId;

    @Column(name = "datasetVersion", nullable = false)
    private int datasetVersion;

    /** Serialized {@code List<TurAgentEvalCaseResultDto>}. */
    @Column(name = "resultsJson", columnDefinition = "longtext")
    private String resultsJson;

    /**
     * Owning AI agent. Hidden from JSON — the client knows the agent from
     * the URL.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    @JsonIgnore
    private TurAIAgent turAIAgent;
}

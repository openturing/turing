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

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T592 / §XXXIII.7 — a human-in-the-loop review task parked by the {@code
 * human-review} grader when it <b>defers</b> a case-result. Holds a snapshot of
 * what the reviewer needs (the replayed transcript + a short expected summary)
 * and, once reviewed (T593 inbox), the human verdict that merges back into the
 * report.
 *
 * <p>{@code status}: {@code PENDING} until a reviewer submits, then
 * {@code REVIEWED}. The reviewer-verdict columns are nullable and populated by
 * the T593 review inbox.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "tur_eval_review_task")
public class TurEvalReviewTask implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** {@code status} value for a task awaiting a human verdict. */
    public static final String PENDING = "PENDING";
    /** {@code status} value for a task a reviewer has decided. */
    public static final String REVIEWED = "REVIEWED";

    /** {@code taskType} — a case deferred by the HUMAN grader (T592). */
    public static final String TYPE_DEFERRED = "DEFERRED";
    /** {@code taskType} — a MODEL-graded case sampled for a calibration audit (T594). */
    public static final String TYPE_AUDIT = "AUDIT";
    /** Synthetic {@code graderId} carried by an audit task. */
    public static final String AUDIT_GRADER = "model-audit";

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** T261 — multi-tenancy discriminator. Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    /** Owning agent id (plain column; the review inbox queries by it). */
    @Column(name = "agent_id", length = 36)
    private String agentId;

    /** The deferred case. */
    @Column(name = "caseId", length = 36)
    private String caseId;

    @Column(name = "caseName", length = 200)
    private String caseName;

    /** The grader that deferred (usually {@code human-review}). */
    @Column(name = "graderId", length = 100)
    private String graderId;

    /** Replayed transcript snapshot the reviewer reads (user turns + answers). */
    @Column(name = "transcript", columnDefinition = "longtext")
    private String transcript;

    /** Short expected-vs-actual summary (outcome / node), nullable. */
    @Column(name = "expectedSummary", columnDefinition = "longtext")
    private String expectedSummary;

    @Column(name = "status", nullable = false, length = 16)
    private String status = PENDING;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    // ── T594 calibration & inter-annotator agreement ──

    /** {@code DEFERRED} (HUMAN grader) or {@code AUDIT} (sampled MODEL-graded case). */
    @Column(name = "taskType", nullable = false, length = 16)
    private String taskType = TYPE_DEFERRED;

    /**
     * How many independent reviewers must decide before the consensus is
     * computed and merged. Default 1 = the legacy single-reviewer behaviour.
     */
    @Column(name = "requiredReviewers", nullable = false)
    private int requiredReviewers = 1;

    /** For an AUDIT task: the model grader's original pass/fail (null otherwise). */
    @Column(name = "modelPass")
    private Boolean modelPass;

    /** For an AUDIT task: the model grader's original score (null otherwise). */
    @Column(name = "modelScore")
    private Double modelScore;

    // ── reviewer verdict (consensus; individual verdicts live in TurEvalReviewVerdict) ──

    /** Reviewer pass/fail (null until reviewed). */
    @Column(name = "reviewedPass")
    private Boolean reviewedPass;

    /** Reviewer score in [0,1] (null until reviewed). */
    @Column(name = "reviewedScore")
    private Double reviewedScore;

    @Column(name = "reviewerNotes", columnDefinition = "longtext")
    private String reviewerNotes;

    @Column(name = "reviewedBy", length = 200)
    private String reviewedBy;

    @Column(name = "reviewedAt")
    private LocalDateTime reviewedAt;
}

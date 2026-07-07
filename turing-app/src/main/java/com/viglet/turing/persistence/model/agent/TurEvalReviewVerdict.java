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
 * T594 / §XXXIII.9 — one reviewer's verdict on a {@link TurEvalReviewTask}. A
 * task with {@code requiredReviewers > 1} collects several of these before its
 * consensus is computed and merged back into the report; the set of verdicts per
 * item feeds the inter-annotator agreement (Fleiss' kappa) metric.
 *
 * <p>Legacy single-reviewer tasks ({@code requiredReviewers = 1}) still record a
 * verdict here — the consensus of one verdict is that verdict, so behaviour is
 * unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "tur_eval_review_verdict")
public class TurEvalReviewVerdict implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** T261 — multi-tenancy discriminator. Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    /** The task this verdict decides. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewTaskId")
    @JsonIgnore
    private TurEvalReviewTask reviewTask;

    /** The reviewer who cast this verdict (principal name; nullable). */
    @Column(name = "reviewer", length = 200)
    private String reviewer;

    /** This reviewer's pass/fail decision. */
    @Column(name = "reviewedPass", nullable = false)
    private boolean reviewedPass;

    /** This reviewer's score in [0,1]. */
    @Column(name = "reviewedScore", nullable = false)
    private double reviewedScore;

    @Column(name = "notes", columnDefinition = "longtext")
    private String notes;

    @Column(name = "reviewedAt", nullable = false)
    private LocalDateTime reviewedAt;
}

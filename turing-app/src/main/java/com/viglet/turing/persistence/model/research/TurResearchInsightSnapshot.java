/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.research;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A point-in-time deterministic snapshot of a study's synthesized sufficiency
 * (Block AW / §XLVI.4, T729 — Continuous Insight). One row is captured after every
 * study run (manual or scheduled); the ordered series is the <em>insight drift</em>
 * surface — how the cohort's theme coverage and saturation move over time.
 *
 * <p>Deliberately LLM-free (T385/T466 discipline): it stores the counts the
 * deterministic T723 saturation scorer already produces
 * ({@code totalUniqueThemes}, {@code adequateAtN}, {@code saturated}) plus the
 * completed-interview count, so drift is free and reproducible — no synthesis call
 * on the hot path. Richer drift (per-theme deltas, T87 sentiment trajectory) can
 * layer on later without changing this snapshot's contract.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "ai_research_insight_snapshot",
        indexes = @Index(name = "idx_research_snapshot_study", columnList = "study_id"))
public class TurResearchInsightSnapshot implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id")
    @JsonIgnore
    private TurResearchStudy study;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    /** Completed interviews contributing to this snapshot. */
    @Column(name = "interview_count", nullable = false)
    private int interviewCount;

    /** Distinct theme tokens mined across the cohort (T723). */
    @Column(name = "total_unique_themes", nullable = false)
    private int totalUniqueThemes;

    /** "Sample adequate at N" (-1 when the cohort never saturated). */
    @Column(name = "adequate_at_n", nullable = false)
    private int adequateAtN;

    @Column(name = "saturated", nullable = false)
    private boolean saturated;
}

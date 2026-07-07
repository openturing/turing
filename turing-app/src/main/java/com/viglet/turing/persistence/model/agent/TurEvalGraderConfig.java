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
 * T587 / §XXXIII.2 — one entry of a {@link TurAgentEvalSet}'s grader stack:
 * which grader ({@code graderId}, resolved against the {@code
 * TurEvalGraderRegistry}) runs, in what {@code sortOrder}, with what
 * per-instance {@code configJson}, and how its result folds into the aggregate
 * ({@code weight} / {@code threshold} / {@code blocking} — consumed by the
 * weighted stack policy, T600).
 *
 * <p>A row with a null/blank {@code caseId} is a <b>set-level</b> config; a row
 * carrying a case id is a <b>per-case override</b> that replaces the set-level
 * config for the same {@code graderId} on that case only.
 *
 * <p>With <b>no</b> enabled rows on a set the registry falls back to the legacy
 * default stack, so existing sets score exactly as before (T586).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "tur_eval_grader_config")
public class TurEvalGraderConfig implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** Human label for the report / Eval Studio (nullable, defaults to graderId). */
    @Column(length = 200)
    private String name;

    /**
     * The grader kind bucket ({@code MODEL} / {@code CODE} / {@code HUMAN}),
     * stored as the enum name. Informational / for filtering — the authoritative
     * kind is the resolved grader bean's {@code kind()}.
     */
    @Column(name = "kind", length = 16)
    private String kind;

    /** Stable id resolved against the grader registry (e.g. {@code slot-match}). */
    @Column(name = "graderId", nullable = false, length = 100)
    private String graderId;

    /** Optional per-instance grader configuration (regex, JSON-path, judge prompt, ...). */
    @Column(name = "configJson", columnDefinition = "longtext")
    private String configJson;

    /** Relative weight in the aggregate (T600); default 1.0. */
    @Column(name = "weight", nullable = false)
    private double weight = 1.0d;

    /** Minimum score for this grader to count as a pass (T600); default 0.0. */
    @Column(name = "threshold", nullable = false)
    private double threshold = 0.0d;

    /** 1 = a failing result blocks the stack; 0 = advisory (T600). Default 1. */
    @Column(name = "blocking", nullable = false)
    private int blocking = 1;

    /** 1 = active in the stack; 0 = parked. Default 1. */
    @Column(name = "enabled", nullable = false)
    private int enabled = 1;

    @Column(name = "sortOrder", nullable = false)
    private int sortOrder = 0;

    /**
     * When set, this row is a per-case override targeting the {@link
     * TurAgentEvalCase} with this id; when null/blank it is a set-level config.
     * Stored as a plain column (not an FK) so an override survives independent
     * of case-row lifecycle churn during authoring.
     */
    @Column(name = "caseId", length = 36)
    private String caseId;

    /** Owning eval set (set-level config). Null when this row belongs to a stack. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eval_set_id")
    @JsonIgnore
    private TurAgentEvalSet turAgentEvalSet;

    /** T600 — owning reusable stack (null when this row belongs to a set). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stack_id")
    @JsonIgnore
    private TurEvalGraderStack turEvalGraderStack;
}

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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T595 / §XXXIII.10 — one row of a {@link TurEvalDataset}: a scripted
 * conversation plus what the agent is expected to do with it. Mirrors the
 * inline {@code TurAgentEvalCase} fields, adds a golden {@code referenceAnswer}
 * (T591) and {@code tags} / {@code metadataJson} for filtering and provenance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "tur_eval_dataset_row")
public class TurEvalDatasetRow implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(length = 200)
    private String name;

    /** JSON array of scripted user turns, replayed in order. */
    @Column(name = "seedTurnsJson", columnDefinition = "longtext")
    private String seedTurnsJson;

    /** JSON map {@code slotName -> expected value}. */
    @Column(name = "expectedSlotsJson", columnDefinition = "longtext")
    private String expectedSlotsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "expectedOutcome", nullable = false, length = 16)
    private TurAgentEvalExpectedOutcome expectedOutcome = TurAgentEvalExpectedOutcome.ANY;

    @Column(name = "expectedNodeId", length = 200)
    private String expectedNodeId;

    /** Optional natural-language rubric for a MODEL grader. */
    @Column(name = "rubric", columnDefinition = "longtext")
    private String rubric;

    /** T591 golden reference answer for reference-answer grading. */
    @Column(name = "referenceAnswer", columnDefinition = "longtext")
    private String referenceAnswer;

    /** Free-form comma/JSON tags for filtering. */
    @Column(name = "tags", length = 500)
    private String tags;

    /** Arbitrary provenance / metadata as JSON. */
    @Column(name = "metadataJson", columnDefinition = "longtext")
    private String metadataJson;

    @Column(name = "sortOrder", nullable = false)
    private int sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id")
    @JsonIgnore
    private TurEvalDataset turEvalDataset;
}

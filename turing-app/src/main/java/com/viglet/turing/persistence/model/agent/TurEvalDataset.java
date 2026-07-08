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
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T595 / §XXXIII.10 — a reusable, versioned, <b>agent-decoupled</b> collection
 * of eval {@link TurEvalDatasetRow rows} (scripted turns + expectations +
 * golden reference). Unlike inline {@code TurAgentEvalCase}s (owned by one
 * agent's set), a dataset can be provided/imported once and bound by many
 * agents' eval sets ({@code TurAgentEvalSet.datasetId}).
 *
 * <p>{@code version} is a simple counter here; immutable snapshots + drift diff
 * arrive with T598. Rows are LAZY (a dataset may be large); the eval runner
 * loads them through the row repository, never a lazy collection.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "tur_eval_dataset")
public class TurEvalDataset implements Serializable {
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

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    /**
     * T601 — arbitrary dataset-level metadata as JSON. Rows already carry their
     * own {@code metadataJson}; this holds shared, dataset-wide config — e.g. an
     * NL→facet dataset stores {@code {"kind":"nl-facet","index":..,"locale":..,
     * "schema":[..]}} here so it round-trips back into a {@code TurNLFacetEvalPack}.
     */
    @Column(name = "metadataJson", columnDefinition = "longtext")
    private String metadataJson;

    /** Version counter (immutable snapshots + drift diff land with T598). */
    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    /**
     * Rows of this dataset. LAZY + cascade for lifecycle only — the runner
     * fetches rows via {@code TurEvalDatasetRowRepository} so the
     * non-transactional replay never touches a lazy collection.
     */
    @OneToMany(mappedBy = "turEvalDataset", orphanRemoval = true,
            fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @OrderBy("sortOrder ASC")
    @JsonIgnore
    private Set<TurEvalDatasetRow> rows = new LinkedHashSet<>();
}

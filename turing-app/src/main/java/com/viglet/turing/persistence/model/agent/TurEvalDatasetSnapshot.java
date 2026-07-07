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
 * T598 / §XXXIII.13 — an <b>immutable</b> frozen copy of a {@link TurEvalDataset}
 * at a given {@code version}. Taking a snapshot freezes the current rows into
 * {@code rowsJson} and bumps the dataset's live version, so a run can pin the
 * version it scored against and a diff can show what changed between versions
 * (honest regression comparisons).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "tur_eval_dataset_snapshot")
public class TurEvalDatasetSnapshot implements Serializable {
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

    @Column(name = "dataset_id", length = 36, nullable = false)
    private String datasetId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    /** Serialized {@code List<TurEvalDatasetRowDto>} frozen at this version. */
    @Column(name = "rowsJson", columnDefinition = "longtext")
    private String rowsJson;
}

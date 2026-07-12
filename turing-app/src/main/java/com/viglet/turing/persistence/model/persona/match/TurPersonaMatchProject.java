/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.persona.match;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A Persona Match analysis project (Block AT / §XLIII) — the reusable
 * "group/project" that reframes content-fit from Block AA's per-persona notebook
 * ({@code 1 persona × N contents}) into a many-to-many workspace
 * ({@code N contents × N personas}). A project owns its project-scoped
 * {@link TurPersonaMatchSource contents} and references a set of existing
 * personas through {@link TurPersonaMatchPersona}; the Cartesian product of the
 * two is persisted as {@link TurPersonaMatchCell matrix cells}.
 *
 * <p>Sits at the {@code /bento/persona/} level, beside Persona Dialogue — not
 * inside any one persona. Empty (no personas or no contents) is a valid, inert
 * state. This is an admin-CRUD aggregate, so it uses plain JPA repositories (no
 * domain records/ports, per the Block AF stop-criteria).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "ai_persona_match_project")
public class TurPersonaMatchProject implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    /** T261 / §XIV.2.5 — multi-tenancy discriminator. Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Which LLM instance drives the grounded fit judge; {@code null} = default. */
    @Column(name = "llmInstanceId", length = 40)
    private String llmInstanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule", nullable = false, length = 16)
    private TurPersonaMatchSchedule schedule = TurPersonaMatchSchedule.MANUAL;

    /** When the last N×N analysis completed (null until first run). */
    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "creationDate")
    private Instant creationDate;

    @Column(name = "modificationDate")
    private Instant modificationDate;
}

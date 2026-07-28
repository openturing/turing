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

    /**
     * T826 / §LXI.2 (Block BN) — the score at/above which a
     * {@code (content, persona)} pair counts as a <em>good fit</em> for this
     * project: what drives the heatmap bands, the drift "crossed below" flag and
     * (once T827/T828 land) the alert and the CI gate. {@code null} = inherit the
     * global default, so a portfolio with a genuinely different bar (expert notes
     * at 60, first-contact onboarding at 85) sets its own without the platform
     * imposing one.
     */
    @Column(name = "fitThreshold")
    private Double fitThreshold;

    /**
     * T827 / §LXI.3 (Block BN) — where to POST the drift report after a
     * <em>scheduled</em> re-analysis that pushed a pair below
     * {@link #fitThreshold} (or dropped the project average past
     * {@link #driftWebhookAvgDrop}). Blank = the alert is off, which is the
     * default: a 03:30 regression is only worth an interrupt if the operator
     * asked for one. Webhook-only on purpose — {@code turing-app} carries no mail
     * dependency.
     */
    @Column(name = "driftWebhookUrl", length = 1000)
    private String driftWebhookUrl;

    /**
     * T827 — {@code Authorization} header value for the drift webhook,
     * <strong>encrypted at rest</strong> via {@code TurSecretCryptoService} (the
     * T62 webhook rule). Write-only from the admin's point of view: never
     * returned by the API.
     */
    @Column(name = "driftWebhookAuthHeader", length = 500)
    private String driftWebhookAuthHeader;

    /**
     * T827 — also alert when the project average fell by more than this many fit
     * points between the last two runs, even if no single pair crossed the bar
     * (a broad, shallow slide is exactly what a per-pair flag misses).
     * {@code null} = crossings only.
     */
    @Column(name = "driftWebhookAvgDrop")
    private Double driftWebhookAvgDrop;

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

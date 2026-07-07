/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.tenant;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * T257 / §XIV.2.1 — the multi-tenancy aggregate root: one self-registered
 * organization that owns its sites, agents, chat flows, indexes, uploads and
 * analytics while sharing a single JVM with every other tenant.
 *
 * <p>Turing's data isolation is a <strong>discriminator column</strong> (one
 * DB, one schema): Hibernate 6's native {@code @TenantId} adds {@code WHERE
 * tenantId = ?} to every read and stamps inserts, driven by a
 * {@code CurrentTenantIdentifierResolver} reading the request-scoped
 * {@code TurTenantContext} (wired in T258/T260). This entity is therefore the
 * <em>registry</em> of tenants — it is itself NOT under {@code @TenantId}.
 *
 * <p>The whole feature hides behind {@code turing.tenancy.enabled} (default
 * {@code false}). When off, every request resolves the immutable
 * {@link #DEFAULT_TENANT_ID DEFAULT} tenant seeded by Liquibase, so the
 * discriminator collapses to a constant equality and single-tenant installs
 * are byte-for-byte unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@Entity
@Table(name = "tenant", uniqueConstraints = @UniqueConstraint(
        name = "uq_tenant_slug", columnNames = { "slug" }))
public class TurTenant implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Stable id of the immutable tenant every request resolves to when
     * {@code turing.tenancy.enabled=false}. A fixed string (not a generated
     * UUID) so the discriminator value is a known constant; seeded by the
     * T257 Liquibase changelog.
     */
    public static final String DEFAULT_TENANT_ID = "DEFAULT";

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /**
     * URL-safe identifier doubling as the subdomain
     * ({@code https://<slug>.turing.cloud}). Unique — the tenant's public
     * identity; T264's signup guards uniqueness + reserved words.
     */
    @Column(name = "slug", nullable = false, length = 63)
    private String slug;

    /** Human-readable organization name. */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Lifecycle state; only {@link TurTenantStatus#ACTIVE} tenants serve traffic. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TurTenantStatus status = TurTenantStatus.ACTIVE;

    /**
     * Billing/plan identifier (e.g. {@code FREE}, {@code PRO}). Free-form rather
     * than an enum so plans can evolve without a migration; T277 maps it to
     * quotas.
     */
    @Column(name = "plan", nullable = false, length = 50)
    private String plan = "FREE";

    /**
     * T284 / §XIV.7.5 — physical isolation seam. {@link TurTenantIsolationMode#SHARED}
     * (default) is the only implemented mode; SCHEMA/DB are reserved for a future
     * heavy-tenant escalation without a data-model change.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "isolationMode", nullable = false, length = 16)
    private TurTenantIsolationMode isolationMode = TurTenantIsolationMode.SHARED;

    /** Creation instant; stamped on first persist (DB also defaults it for seeds). */
    @Column(name = "createdAt", nullable = false)
    private Instant createdAt;

    /**
     * T336 / §XIV.8.3 — when the tenant was last moved into
     * {@link TurTenantStatus#SUSPENDED}. Anchors the grace window before the
     * deprovision sweep hard-deletes an orphaned personal tenant; {@code null}
     * while {@link TurTenantStatus#ACTIVE}.
     */
    @Column(name = "suspendedAt")
    private Instant suspendedAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}

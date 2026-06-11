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

import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * T257 / §XIV.2.1 — the user ↔ tenant ↔ role join that lets one Keycloak
 * identity belong to several tenants (§XIV.1, decision 2: single realm + a
 * tenant claim).
 *
 * <p>The member is keyed by {@code username} (the principal name from the OIDC
 * token or the session-auth path) rather than a hard FK to {@code TurUser},
 * because the authoritative identity store is Keycloak and a member may be
 * provisioned lazily. T263 reads these rows to expose per-tenant authorities;
 * T265's switch API lists a user's memberships. Unique on
 * {@code (tenant, username)} — one membership per user per tenant.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@Entity
@Table(name = "tenant_membership", uniqueConstraints = @UniqueConstraint(
        name = "uq_tenant_membership", columnNames = { "tenant_id", "username" }))
public class TurTenantMembership implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** The tenant this membership grants access to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private TurTenant tenant;

    /** Principal name (Keycloak username / session principal) of the member. */
    @Column(name = "username", nullable = false, length = 255)
    private String username;

    /** The member's role within {@link #tenant}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private TurTenantRole role = TurTenantRole.MEMBER;

    /** Membership state; T259 requires {@link TurTenantMembershipStatus#ACTIVE} to serve. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TurTenantMembershipStatus status = TurTenantMembershipStatus.ACTIVE;

    /** When the membership was created; stamped on first persist. */
    @Column(name = "createdAt", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}

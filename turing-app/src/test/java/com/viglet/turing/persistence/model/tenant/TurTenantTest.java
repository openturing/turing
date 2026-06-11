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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TurTenant} — the multi-tenancy aggregate root (T257).
 *
 * <p>Guards the defaults and the {@code @PrePersist} stamping that the rest of
 * Block J relies on, plus the immutable {@code DEFAULT} invariant that keeps
 * tenancy-off installs unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurTenantTest {

    @Test
    void newTenantHasLegacySafeDefaults() {
        TurTenant tenant = new TurTenant();

        assertThat(tenant.getStatus()).isEqualTo(TurTenantStatus.ACTIVE);
        assertThat(tenant.getPlan()).isEqualTo("FREE");
        assertThat(tenant.getCreatedAt()).isNull();
        assertThat(tenant.getId()).isNull();
    }

    @Test
    void onCreateStampsCreatedAtWhenAbsent() {
        TurTenant tenant = new TurTenant();

        Instant before = Instant.now();
        tenant.onCreate();
        Instant after = Instant.now();

        assertThat(tenant.getCreatedAt()).isNotNull();
        assertThat(tenant.getCreatedAt()).isBetween(before, after);
    }

    @Test
    void onCreateDoesNotOverwriteAnExistingCreatedAt() {
        TurTenant tenant = new TurTenant();
        Instant explicit = Instant.parse("2020-01-01T00:00:00Z");
        tenant.setCreatedAt(explicit);

        tenant.onCreate();

        assertThat(tenant.getCreatedAt()).isEqualTo(explicit);
    }

    @Test
    void defaultTenantIdIsTheStableConstant() {
        // The discriminator value used when turing.tenancy.enabled=false; must
        // match the row seeded by Liquibase v2026.3.1.27.
        assertThat(TurTenant.DEFAULT_TENANT_ID).isEqualTo("DEFAULT");
    }

    @Test
    void fieldsRoundTripThroughAccessors() {
        TurTenant tenant = new TurTenant();
        tenant.setId("t-1");
        tenant.setSlug("acme");
        tenant.setName("Acme Inc.");
        tenant.setStatus(TurTenantStatus.SUSPENDED);
        tenant.setPlan("PRO");

        assertThat(tenant.getId()).isEqualTo("t-1");
        assertThat(tenant.getSlug()).isEqualTo("acme");
        assertThat(tenant.getName()).isEqualTo("Acme Inc.");
        assertThat(tenant.getStatus()).isEqualTo(TurTenantStatus.SUSPENDED);
        assertThat(tenant.getPlan()).isEqualTo("PRO");
    }
}

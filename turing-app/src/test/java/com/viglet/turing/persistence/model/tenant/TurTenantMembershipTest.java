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
 * Unit tests for {@link TurTenantMembership} — the user ↔ tenant ↔ role join
 * (T257).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurTenantMembershipTest {

    @Test
    void newMembershipDefaultsToActiveMember() {
        TurTenantMembership membership = new TurTenantMembership();

        assertThat(membership.getRole()).isEqualTo(TurTenantRole.MEMBER);
        assertThat(membership.getStatus()).isEqualTo(TurTenantMembershipStatus.ACTIVE);
        assertThat(membership.getCreatedAt()).isNull();
    }

    @Test
    void onCreateStampsCreatedAtWhenAbsent() {
        TurTenantMembership membership = new TurTenantMembership();

        Instant before = Instant.now();
        membership.onCreate();
        Instant after = Instant.now();

        assertThat(membership.getCreatedAt()).isNotNull();
        assertThat(membership.getCreatedAt()).isBetween(before, after);
    }

    @Test
    void onCreateDoesNotOverwriteAnExistingCreatedAt() {
        TurTenantMembership membership = new TurTenantMembership();
        Instant explicit = Instant.parse("2021-06-01T12:00:00Z");
        membership.setCreatedAt(explicit);

        membership.onCreate();

        assertThat(membership.getCreatedAt()).isEqualTo(explicit);
    }

    @Test
    void linksAUserToATenantWithARole() {
        TurTenant tenant = new TurTenant();
        tenant.setId("t-1");
        tenant.setSlug("acme");

        TurTenantMembership membership = new TurTenantMembership();
        membership.setId("m-1");
        membership.setTenant(tenant);
        membership.setUsername("alice");
        membership.setRole(TurTenantRole.OWNER);
        membership.setStatus(TurTenantMembershipStatus.INVITED);

        assertThat(membership.getTenant()).isSameAs(tenant);
        assertThat(membership.getTenant().getId()).isEqualTo("t-1");
        assertThat(membership.getUsername()).isEqualTo("alice");
        assertThat(membership.getRole()).isEqualTo(TurTenantRole.OWNER);
        assertThat(membership.getStatus()).isEqualTo(TurTenantMembershipStatus.INVITED);
    }
}

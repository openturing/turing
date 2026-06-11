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

import org.junit.jupiter.api.Test;

/**
 * Pin the tenancy enum value sets (T257). These names are persisted verbatim
 * ({@code @Enumerated(EnumType.STRING)}) and are also the literal
 * {@code defaultValue}s in Liquibase {@code v2026.3.1.27.yaml}
 * ({@code status=ACTIVE}, {@code plan=FREE}, {@code role=MEMBER}). Renaming a
 * constant without a migration would silently corrupt reads/writes, so this
 * test fails loudly if the contract drifts.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurTenantEnumsTest {

    @Test
    void tenantStatusValuesAreStable() {
        assertThat(TurTenantStatus.values())
                .containsExactly(TurTenantStatus.ACTIVE, TurTenantStatus.SUSPENDED);
    }

    @Test
    void tenantRoleValuesAreStable() {
        assertThat(TurTenantRole.values())
                .containsExactly(TurTenantRole.OWNER, TurTenantRole.ADMIN, TurTenantRole.MEMBER);
    }

    @Test
    void membershipStatusValuesAreStable() {
        assertThat(TurTenantMembershipStatus.values()).containsExactly(
                TurTenantMembershipStatus.ACTIVE,
                TurTenantMembershipStatus.INVITED,
                TurTenantMembershipStatus.SUSPENDED);
    }

    @Test
    void liquibaseDefaultStringsResolveToEnumConstants() {
        // The exact strings written as column defaults in v2026.3.1.27.yaml.
        assertThat(TurTenantStatus.valueOf("ACTIVE")).isEqualTo(TurTenantStatus.ACTIVE);
        assertThat(TurTenantRole.valueOf("MEMBER")).isEqualTo(TurTenantRole.MEMBER);
        assertThat(TurTenantMembershipStatus.valueOf("ACTIVE"))
                .isEqualTo(TurTenantMembershipStatus.ACTIVE);
    }
}

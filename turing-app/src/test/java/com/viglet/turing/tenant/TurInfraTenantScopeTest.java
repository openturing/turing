/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TurInfraTenantScope} — the layer that was missing when
 * BYO-infra controllers leaked instances across tenants: they listed via the
 * unscoped {@code findAll()} and never stamped {@code tenantId} on create, so
 * every instance landed in the {@code null} GLOBAL pool visible to everyone.
 * These assertions pin both halves of the fix (stamp-on-create + scoped list).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurInfraTenantScopeTest {

    @Mock
    private TurTenantContext tenantContext;
    @Mock
    private TurPlatformAdminService platformAdminService;
    @InjectMocks
    private TurInfraTenantScope scope;

    /** Minimal stand-in for any of the six BYO-infra entities. */
    static class Owned implements TurTenantOwnedInfra {
        private String tenantId;

        Owned(String tenantId) {
            this.tenantId = tenantId;
        }

        @Override
        public String getTenantId() {
            return tenantId;
        }

        @Override
        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }
    }

    @Test
    void stampsCurrentTenantOnCreateForRegularUser() {
        when(tenantContext.isTenancyEnabled()).thenReturn(true);
        when(tenantContext.resolveCurrentTenant()).thenReturn("tenantA");
        when(platformAdminService.isPlatformAdmin()).thenReturn(false);

        Owned entity = scope.stampOnCreate(new Owned(null));

        assertThat(entity.getTenantId()).isEqualTo("tenantA");
    }

    @Test
    void platformAdminCreatesGlobalInstance() {
        when(tenantContext.isTenancyEnabled()).thenReturn(true);
        when(platformAdminService.isPlatformAdmin()).thenReturn(true);

        Owned entity = scope.stampOnCreate(new Owned(null));

        assertThat(entity.getTenantId()).isNull();
    }

    @Test
    void doesNotOverwriteAnExistingOwner() {
        lenient().when(tenantContext.isTenancyEnabled()).thenReturn(true);
        lenient().when(tenantContext.resolveCurrentTenant()).thenReturn("tenantA");

        Owned entity = scope.stampOnCreate(new Owned("tenantB"));

        assertThat(entity.getTenantId()).isEqualTo("tenantB");
    }

    @Test
    void doesNotStampWhenTenancyDisabled() {
        when(tenantContext.isTenancyEnabled()).thenReturn(false);

        Owned entity = scope.stampOnCreate(new Owned(null));

        assertThat(entity.getTenantId()).isNull();
    }

    @Test
    void visibleListUsesScopedQueryWhenTenancyEnabled() {
        when(tenantContext.isTenancyEnabled()).thenReturn(true);
        when(tenantContext.resolveCurrentTenant()).thenReturn("tenantA");
        List<String> unscoped = List.of("all-1", "all-2", "other-tenant");
        List<String> scoped = List.of("mine", "global");

        List<String> result = scope.visibleList(
                () -> unscoped,
                tenantId -> "tenantA".equals(tenantId) ? scoped : List.of());

        assertThat(result).isEqualTo(scoped);
    }

    @Test
    void visibleListReturnsUnscopedWhenTenancyDisabled() {
        when(tenantContext.isTenancyEnabled()).thenReturn(false);
        List<String> unscoped = List.of("all-1", "all-2");

        List<String> result = scope.visibleList(
                () -> unscoped,
                tenantId -> List.of("should-not-be-used"));

        assertThat(result).isEqualTo(unscoped);
    }
}

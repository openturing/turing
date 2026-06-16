/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.core.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for {@link VigletInfraTenantScope} — the layer that scopes BYO-infra
 * instances by tenant: stamp-on-create, scoped list, read-only GLOBAL pool
 * (T372) and by-id visibility (T365).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class VigletInfraTenantScopeTest {

    @Mock
    private VigletTenantContext tenantContext;
    @Mock
    private VigletPlatformAdminService platformAdminService;
    @InjectMocks
    private VigletInfraTenantScope scope;

    /** Minimal stand-in for any tenant-owned infra entity. */
    static class Owned implements VigletTenantOwnedInfra {
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
    void stampOnImportKeepsGlobalWhenNoTenantBound() {
        when(tenantContext.getCurrentTenant()).thenReturn(null);

        Owned entity = scope.stampOnImport(new Owned(null));

        assertThat(entity.getTenantId()).isNull();
    }

    @Test
    void stampOnImportClaimsWhenTenantBound() {
        when(tenantContext.getCurrentTenant()).thenReturn("tenantA");
        when(tenantContext.isTenancyEnabled()).thenReturn(true);
        when(tenantContext.resolveCurrentTenant()).thenReturn("tenantA");
        when(platformAdminService.isPlatformAdmin()).thenReturn(false);

        Owned entity = scope.stampOnImport(new Owned(null));

        assertThat(entity.getTenantId()).isEqualTo("tenantA");
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

    // --- T372: GLOBAL pool is read-only for non-platform-admins ---

    @Test
    void globalInstanceIsReadOnlyForRegularTenant() {
        when(tenantContext.isTenancyEnabled()).thenReturn(true);
        when(platformAdminService.isPlatformAdmin()).thenReturn(false);

        assertThat(scope.isPlatformGlobalReadOnly(new Owned(null))).isTrue();
        assertThatThrownBy(() -> scope.assertWritable(new Owned(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ownedInstanceIsWritableForItsTenant() {
        when(tenantContext.isTenancyEnabled()).thenReturn(true);
        lenient().when(platformAdminService.isPlatformAdmin()).thenReturn(false);

        assertThat(scope.isPlatformGlobalReadOnly(new Owned("tenantA"))).isFalse();
        assertThatCode(() -> scope.assertWritable(new Owned("tenantA"))).doesNotThrowAnyException();
    }

    @Test
    void platformAdminMayMutateGlobalInstance() {
        when(tenantContext.isTenancyEnabled()).thenReturn(true);
        when(platformAdminService.isPlatformAdmin()).thenReturn(true);

        assertThat(scope.isPlatformGlobalReadOnly(new Owned(null))).isFalse();
        assertThatCode(() -> scope.assertWritable(new Owned(null))).doesNotThrowAnyException();
    }

    @Test
    void globalInstanceIsWritableWhenTenancyDisabled() {
        when(tenantContext.isTenancyEnabled()).thenReturn(false);

        assertThat(scope.isPlatformGlobalReadOnly(new Owned(null))).isFalse();
        assertThatCode(() -> scope.assertWritable(new Owned(null))).doesNotThrowAnyException();
    }

    // --- T365: by-id visibility (own + GLOBAL + platform-admin) ---

    @Test
    void ownInstanceIsVisibleToItsTenant() {
        when(tenantContext.isTenancyEnabled()).thenReturn(true);
        when(platformAdminService.isPlatformAdmin()).thenReturn(false);
        when(tenantContext.resolveCurrentTenant()).thenReturn("tenantA");

        assertThat(scope.isVisibleToTenant(new Owned("tenantA"))).isTrue();
    }

    @Test
    void globalInstanceIsVisibleToEveryTenant() {
        when(tenantContext.isTenancyEnabled()).thenReturn(true);
        when(platformAdminService.isPlatformAdmin()).thenReturn(false);

        assertThat(scope.isVisibleToTenant(new Owned(null))).isTrue();
    }

    @Test
    void otherTenantsInstanceIsNotVisible() {
        when(tenantContext.isTenancyEnabled()).thenReturn(true);
        when(platformAdminService.isPlatformAdmin()).thenReturn(false);
        when(tenantContext.resolveCurrentTenant()).thenReturn("tenantA");

        assertThat(scope.isVisibleToTenant(new Owned("tenantB"))).isFalse();
    }

    @Test
    void platformAdminSeesEveryTenantsInstance() {
        when(tenantContext.isTenancyEnabled()).thenReturn(true);
        when(platformAdminService.isPlatformAdmin()).thenReturn(true);

        assertThat(scope.isVisibleToTenant(new Owned("tenantB"))).isTrue();
    }

    @Test
    void everythingIsVisibleWhenTenancyDisabled() {
        when(tenantContext.isTenancyEnabled()).thenReturn(false);

        assertThat(scope.isVisibleToTenant(new Owned("tenantB"))).isTrue();
    }
}

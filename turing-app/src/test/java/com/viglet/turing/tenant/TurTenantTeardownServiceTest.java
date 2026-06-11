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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.repository.tenant.TurTenantMembershipRepository;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.service.storage.TurStorageService;

/**
 * Unit tests for {@link TurTenantTeardownService} (T281).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurTenantTeardownServiceTest {

    @Mock
    private TurTenantRepository tenantRepository;
    @Mock
    private TurTenantMembershipRepository membershipRepository;
    @Mock
    private TurStorageService storageService;
    @Mock
    private CacheManager cacheManager;

    private TurTenantContext context;
    private TurTenantTeardownService service;

    private void build() {
        TurConfigProperties props = new TurConfigProperties();
        props.getTenancy().setEnabled(true);
        context = new TurTenantContext(props);
        service = new TurTenantTeardownService(new TurPlatformAdminService(context), context,
                tenantRepository, membershipRepository, storageService, cacheManager);
    }

    private void authenticateAs(String name, String... authorities) {
        var granted = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(name, "n/a", granted));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        if (context != null) {
            context.clear();
        }
    }

    @Test
    void deleteRequiresPlatformAdmin() {
        build();
        authenticateAs("regular", "ROLE_ADMIN");

        assertThatThrownBy(() -> service.delete("t-1", false))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void dryRunReportsPlanWithoutMutating() {
        build();
        authenticateAs("ops", TurPlatformAdminService.ROLE_PLATFORM_ADMIN);
        TurTenant tenant = new TurTenant();
        tenant.setId("t-1");
        when(tenantRepository.findById("t-1")).thenReturn(Optional.of(tenant));
        when(membershipRepository.findAll()).thenReturn(List.of());

        var report = service.delete("t-1", true);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.tenantExisted()).isTrue();
        verify(tenantRepository, never()).delete(any(String.class));
        verify(cacheManager, never()).getCacheNames();
    }

    @Test
    void deleteMissingTenantIsIdempotent() {
        build();
        authenticateAs("ops", TurPlatformAdminService.ROLE_PLATFORM_ADMIN);
        when(tenantRepository.findById("ghost")).thenReturn(Optional.empty());

        var report = service.delete("ghost", false);

        assertThat(report.tenantExisted()).isFalse();
        verify(tenantRepository, never()).delete(any(String.class));
    }

    @Test
    void deleteEvictsCachesAndRemovesTenant() {
        build();
        authenticateAs("ops", TurPlatformAdminService.ROLE_PLATFORM_ADMIN);
        TurTenant tenant = new TurTenant();
        tenant.setId("t-1");
        when(tenantRepository.findById("t-1")).thenReturn(Optional.of(tenant));
        when(membershipRepository.findAll()).thenReturn(List.of());
        when(storageService.isEnabled()).thenReturn(false);
        when(cacheManager.getCacheNames()).thenReturn(List.of());

        var report = service.delete("t-1", false);

        assertThat(report.dryRun()).isFalse();
        assertThat(report.cachesEvicted()).isTrue();
        verify(tenantRepository).delete("t-1");
    }
}

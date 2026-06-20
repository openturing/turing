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
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.model.tenant.TurTenantMembership;
import com.viglet.turing.persistence.model.tenant.TurTenantRole;
import com.viglet.turing.persistence.model.tenant.TurTenantStatus;
import com.viglet.turing.persistence.repository.tenant.TurTenantMembershipRepository;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurTenantDeprovisionMode;

/**
 * Unit tests for {@link TurTenantDeprovisionService} (T336) — the identity
 * reconciliation sweep, with the identity provider and teardown service mocked.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurTenantDeprovisionServiceTest {

    @Mock
    private TurTenantRepository tenantRepository;
    @Mock
    private TurTenantMembershipRepository membershipRepository;
    @Mock
    private TurTenantOwnerIdentityProvider identityProvider;
    @Mock
    private TurTenantTeardownService teardownService;

    private TurConfigProperties props;
    private TurTenantDeprovisionService service;

    @BeforeEach
    void setUp() {
        props = new TurConfigProperties();
        props.getTenancy().setEnabled(true);
        props.getTenancy().getDeprovision().setEnabled(true);
        service = new TurTenantDeprovisionService(props, tenantRepository, membershipRepository,
                identityProvider, teardownService);
        service.setClockForTest(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    // Pinned "now" so the grace-period eligibility checks are deterministic;
    // the per-test suspendedAt values are offset from this same instant.
    private static final Instant FIXED_NOW = Instant.parse("2026-06-15T12:00:00Z");

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private TurTenant tenant(String id, TurTenantStatus status) {
        TurTenant t = new TurTenant();
        t.setId(id);
        t.setSlug(id);
        t.setStatus(status);
        return t;
    }

    private TurTenantMembership member(TurTenant tenant, String username) {
        TurTenantMembership m = new TurTenantMembership();
        m.setId("m-" + username);
        m.setTenant(tenant);
        m.setUsername(username);
        m.setRole(TurTenantRole.OWNER);
        return m;
    }

    @Test
    void noopWhenFeatureDisabled() {
        props.getTenancy().getDeprovision().setEnabled(false);

        var report = service.reconcile();

        assertThat(report.ran()).isFalse();
        verify(teardownService, never()).suspend(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void noopWhenIdentityProviderDisabled() {
        when(identityProvider.isEnabled()).thenReturn(false);

        var report = service.reconcile();

        assertThat(report.ran()).isFalse();
        verify(membershipRepository, never()).findAll();
    }

    @Test
    void suspendsOrphanedActiveTenant() {
        when(identityProvider.isEnabled()).thenReturn(true);
        TurTenant t = tenant("t-1", TurTenantStatus.ACTIVE);
        when(tenantRepository.findAll()).thenReturn(List.of(t));
        when(membershipRepository.findAll()).thenReturn(List.of(member(t, "gone@x.com")));
        when(identityProvider.statusOf("gone@x.com")).thenReturn(TurIdentityStatus.ABSENT);

        var report = service.reconcile();

        assertThat(report.ran()).isTrue();
        assertThat(report.suspended()).isEqualTo(1);
        assertThat(report.deleted()).isZero();
        verify(teardownService).suspend("t-1");
        verify(teardownService, never()).delete(org.mockito.ArgumentMatchers.anyString(), anyBoolean());
    }

    @Test
    void sparesTenantWithOneLiveMember() {
        when(identityProvider.isEnabled()).thenReturn(true);
        TurTenant t = tenant("t-1", TurTenantStatus.ACTIVE);
        when(tenantRepository.findAll()).thenReturn(List.of(t));
        when(membershipRepository.findAll())
                .thenReturn(List.of(member(t, "gone@x.com"), member(t, "live@x.com")));
        when(identityProvider.statusOf("gone@x.com")).thenReturn(TurIdentityStatus.DISABLED);
        when(identityProvider.statusOf("live@x.com")).thenReturn(TurIdentityStatus.ACTIVE);

        var report = service.reconcile();

        assertThat(report.skippedLive()).isEqualTo(1);
        assertThat(report.suspended()).isZero();
        verify(teardownService, never()).suspend(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void skipsTenantWhenAnyMemberUnknown() {
        when(identityProvider.isEnabled()).thenReturn(true);
        TurTenant t = tenant("t-1", TurTenantStatus.ACTIVE);
        when(tenantRepository.findAll()).thenReturn(List.of(t));
        when(membershipRepository.findAll())
                .thenReturn(List.of(member(t, "gone@x.com"), member(t, "huh@x.com")));
        lenient().when(identityProvider.statusOf("gone@x.com")).thenReturn(TurIdentityStatus.ABSENT);
        when(identityProvider.statusOf("huh@x.com")).thenReturn(TurIdentityStatus.UNKNOWN);

        var report = service.reconcile();

        assertThat(report.skippedUnknown()).isEqualTo(1);
        assertThat(report.suspended()).isZero();
        verify(teardownService, never()).suspend(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void neverTouchesDefaultTenant() {
        when(identityProvider.isEnabled()).thenReturn(true);
        TurTenant def = tenant(TurTenant.DEFAULT_TENANT_ID, TurTenantStatus.ACTIVE);
        when(tenantRepository.findAll()).thenReturn(List.of(def));
        when(membershipRepository.findAll()).thenReturn(List.of(member(def, "anyone@x.com")));

        var report = service.reconcile();

        assertThat(report.scanned()).isZero();
        verify(identityProvider, never()).statusOf(org.mockito.ArgumentMatchers.anyString());
        verify(teardownService, never()).suspend(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void deletesSuspendedOrphanPastGraceInDeleteMode() {
        props.getTenancy().getDeprovision().setMode(TurTenantDeprovisionMode.SUSPEND_THEN_DELETE);
        props.getTenancy().getDeprovision().setDeleteAfterDays(30);
        when(identityProvider.isEnabled()).thenReturn(true);
        TurTenant t = tenant("t-1", TurTenantStatus.SUSPENDED);
        t.setSuspendedAt(FIXED_NOW.minus(Duration.ofDays(40)));
        when(tenantRepository.findAll()).thenReturn(List.of(t));
        when(membershipRepository.findAll()).thenReturn(List.of(member(t, "gone@x.com")));
        when(identityProvider.statusOf("gone@x.com")).thenReturn(TurIdentityStatus.ABSENT);

        var report = service.reconcile();

        assertThat(report.deleted()).isEqualTo(1);
        verify(teardownService).delete("t-1", false);
        verify(teardownService, never()).suspend(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void keepsSuspendedOrphanWithinGrace() {
        props.getTenancy().getDeprovision().setMode(TurTenantDeprovisionMode.SUSPEND_THEN_DELETE);
        props.getTenancy().getDeprovision().setDeleteAfterDays(30);
        when(identityProvider.isEnabled()).thenReturn(true);
        TurTenant t = tenant("t-1", TurTenantStatus.SUSPENDED);
        t.setSuspendedAt(FIXED_NOW.minus(Duration.ofDays(1)));
        when(tenantRepository.findAll()).thenReturn(List.of(t));
        when(membershipRepository.findAll()).thenReturn(List.of(member(t, "gone@x.com")));
        when(identityProvider.statusOf("gone@x.com")).thenReturn(TurIdentityStatus.ABSENT);

        var report = service.reconcile();

        assertThat(report.deleted()).isZero();
        verify(teardownService, never()).delete(org.mockito.ArgumentMatchers.anyString(), anyBoolean());
    }

    @Test
    void suspendOnlyModeNeverDeletes() {
        props.getTenancy().getDeprovision().setMode(TurTenantDeprovisionMode.SUSPEND_ONLY);
        when(identityProvider.isEnabled()).thenReturn(true);
        TurTenant t = tenant("t-1", TurTenantStatus.SUSPENDED);
        t.setSuspendedAt(FIXED_NOW.minus(Duration.ofDays(999)));
        when(tenantRepository.findAll()).thenReturn(List.of(t));
        when(membershipRepository.findAll()).thenReturn(List.of(member(t, "gone@x.com")));
        when(identityProvider.statusOf("gone@x.com")).thenReturn(TurIdentityStatus.ABSENT);

        var report = service.reconcile();

        assertThat(report.deleted()).isZero();
        verify(teardownService, never()).delete(org.mockito.ArgumentMatchers.anyString(), anyBoolean());
    }
}

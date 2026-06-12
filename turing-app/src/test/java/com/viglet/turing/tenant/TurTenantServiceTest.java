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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.model.tenant.TurTenantMembership;
import com.viglet.turing.persistence.model.tenant.TurTenantMembershipStatus;
import com.viglet.turing.persistence.model.tenant.TurTenantRole;
import com.viglet.turing.persistence.repository.tenant.TurTenantMembershipRepository;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;

/**
 * Unit tests for {@link TurTenantService} (T264 signup, T265 listing).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurTenantServiceTest {

    @Mock
    private TurTenantRepository tenantRepository;
    @Mock
    private TurTenantMembershipRepository membershipRepository;

    private TurTenantService service() {
        return new TurTenantService(tenantRepository, membershipRepository);
    }

    @Test
    void signupCreatesTenantAndOwnerMembership() {
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(TurTenant.class))).thenAnswer(inv -> {
            TurTenant t = inv.getArgument(0);
            t.setId("t-1");
            return t;
        });

        TurTenant created = service().signup("Acme", "Acme Inc.", "alice");

        assertThat(created.getSlug()).isEqualTo("acme");
        assertThat(created.getName()).isEqualTo("Acme Inc.");
        verify(membershipRepository).save(org.mockito.ArgumentMatchers.argThat(m ->
                m.getUsername().equals("alice")
                        && m.getRole() == TurTenantRole.OWNER
                        && m.getStatus() == TurTenantMembershipStatus.ACTIVE));
    }

    @Test
    void signupIsIdempotentForTheSameOwner() {
        TurTenant existing = new TurTenant();
        existing.setId("t-1");
        existing.setSlug("acme");
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(existing));
        TurTenantMembership ownerM = new TurTenantMembership();
        ownerM.setRole(TurTenantRole.OWNER);
        when(membershipRepository.findByTenant_IdAndUsername("t-1", "alice"))
                .thenReturn(Optional.of(ownerM));

        TurTenant result = service().signup("acme", "Acme", "alice");

        assertThat(result).isSameAs(existing);
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void signupRejectsSlugTakenByAnotherOwner() {
        TurTenant existing = new TurTenant();
        existing.setId("t-1");
        existing.setSlug("acme");
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(existing));
        when(membershipRepository.findByTenant_IdAndUsername("t-1", "mallory"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().signup("acme", "Acme", "mallory"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void rejectsReservedAndMalformedSlugs() {
        assertThatThrownBy(() -> service().normalizeSlug("admin"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> service().normalizeSlug("A"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Invalid slug");
        assertThatThrownBy(() -> service().normalizeSlug("bad slug!"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Invalid slug");
    }

    @Test
    void normalizeSlugLowercasesAndTrims() {
        assertThat(service().normalizeSlug("  My-Co  ")).isEqualTo("my-co");
    }

    @Test
    void resolveOrCreatePersonalTenantReturnsExistingOwnedTenant() {
        TurTenant home = new TurTenant();
        home.setId("t-home");
        TurTenantMembership owner = new TurTenantMembership();
        owner.setTenant(home);
        owner.setRole(TurTenantRole.OWNER);
        owner.setStatus(TurTenantMembershipStatus.ACTIVE);
        when(membershipRepository.findByUsername("alice")).thenReturn(List.of(owner));

        TurTenant resolved = service().resolveOrCreatePersonalTenant("alice");

        assertThat(resolved).isSameAs(home);
        verify(tenantRepository, never()).save(any());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void resolveOrCreatePersonalTenantProvisionsWhenNone() {
        when(membershipRepository.findByUsername("alice")).thenReturn(List.of());
        when(tenantRepository.findBySlug("alice")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(TurTenant.class))).thenAnswer(inv -> {
            TurTenant t = inv.getArgument(0);
            t.setId("t-alice");
            return t;
        });

        TurTenant created = service().resolveOrCreatePersonalTenant("alice");

        assertThat(created.getSlug()).isEqualTo("alice");
        verify(membershipRepository).save(org.mockito.ArgumentMatchers.argThat(m ->
                m.getUsername().equals("alice")
                        && m.getRole() == TurTenantRole.OWNER
                        && m.getStatus() == TurTenantMembershipStatus.ACTIVE));
    }

    @Test
    void resolveOrCreatePersonalTenantSuffixesWhenSlugTakenByAnotherUser() {
        when(membershipRepository.findByUsername("bob")).thenReturn(List.of());
        TurTenant otherBob = new TurTenant();
        otherBob.setId("t-other");
        otherBob.setSlug("bob");
        when(tenantRepository.findBySlug("bob")).thenReturn(Optional.of(otherBob));
        when(membershipRepository.findByTenant_IdAndUsername("t-other", "bob"))
                .thenReturn(Optional.empty()); // not bob's
        when(tenantRepository.findBySlug("bob-2")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(TurTenant.class))).thenAnswer(inv -> {
            TurTenant t = inv.getArgument(0);
            t.setId("t-bob-2");
            return t;
        });

        TurTenant created = service().resolveOrCreatePersonalTenant("bob");

        assertThat(created.getSlug()).isEqualTo("bob-2");
    }

    @Test
    void basePersonalSlugSanitizesEmailsReservedAndShortNames() {
        assertThat(service().basePersonalSlug("Alice@Example.com")).isEqualTo("alice-example-com");
        assertThat(service().basePersonalSlug("admin")).isEqualTo("u-admin"); // reserved
        assertThat(service().basePersonalSlug("x")).isEqualTo("user-x");      // too short
        assertThat(service().basePersonalSlug("a".repeat(80)).length()).isLessThanOrEqualTo(56);
    }

    @Test
    void tenantsOfReturnsOnlyActiveMemberships() {
        TurTenant a = new TurTenant();
        a.setId("a");
        TurTenant b = new TurTenant();
        b.setId("b");
        TurTenantMembership active = new TurTenantMembership();
        active.setTenant(a);
        active.setStatus(TurTenantMembershipStatus.ACTIVE);
        TurTenantMembership suspended = new TurTenantMembership();
        suspended.setTenant(b);
        suspended.setStatus(TurTenantMembershipStatus.SUSPENDED);
        when(membershipRepository.findByUsername("alice")).thenReturn(List.of(active, suspended));

        assertThat(service().tenantsOf("alice")).extracting(TurTenant::getId).containsExactly("a");
    }
}

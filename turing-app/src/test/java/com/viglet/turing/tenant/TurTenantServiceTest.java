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
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.core.tenancy.VigletTenantMembershipService;
import com.viglet.core.tenancy.VigletTenantRef;
import com.viglet.core.tenancy.VigletTenantSlugValidator;
import com.viglet.core.tenancy.VigletTenantStatus;
import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.model.tenant.TurTenantStatus;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;

/**
 * Unit tests for {@link TurTenantService} — the Turing-facing adapter over the
 * shared {@link VigletTenantMembershipService}. The deep lifecycle behaviour
 * (idempotent signup, personal-tenant provisioning, slug rules) is owned and
 * tested in {@code viglet-core-tenancy}; here we assert the adapter delegates and
 * maps the neutral {@link VigletTenantRef} back to the {@link TurTenant} entity
 * (T396 / §XIV.9), plus that Turing's reserved-name set still rejects
 * {@code turing}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurTenantServiceTest {

    @Mock
    private VigletTenantMembershipService membershipService;
    @Mock
    private TurTenantRepository tenantRepository;

    /** Real slug validator carrying Turing's extra reserved label ({@code turing}). */
    private final VigletTenantSlugValidator slugValidator = new VigletTenantSlugValidator(Set.of("turing"));

    private TurTenantService service() {
        return new TurTenantService(membershipService, slugValidator, tenantRepository);
    }

    private static TurTenant tenant(String id, String slug) {
        TurTenant t = new TurTenant();
        t.setId(id);
        t.setSlug(slug);
        return t;
    }

    private static VigletTenantRef ref(String id, String slug) {
        return new VigletTenantRef(id, slug, slug, VigletTenantStatus.ACTIVE, "FREE");
    }

    @Test
    void signupDelegatesAndMapsRefToEntity() {
        when(membershipService.signup("Acme", "Acme Inc.", "alice")).thenReturn(ref("t-1", "acme"));
        TurTenant entity = tenant("t-1", "acme");
        when(tenantRepository.findById("t-1")).thenReturn(Optional.of(entity));

        TurTenant created = service().signup("Acme", "Acme Inc.", "alice");

        assertThat(created).isSameAs(entity);
    }

    @Test
    void resolveOrCreatePersonalTenantMapsRef() {
        when(membershipService.resolveOrCreatePersonalTenant("alice")).thenReturn(ref("t-home", "alice"));
        TurTenant entity = tenant("t-home", "alice");
        when(tenantRepository.findById("t-home")).thenReturn(Optional.of(entity));

        assertThat(service().resolveOrCreatePersonalTenant("alice")).isSameAs(entity);
    }

    @Test
    void resolveOrCreatePersonalTenantReturnsNullForBlankUser() {
        when(membershipService.resolveOrCreatePersonalTenant("")).thenReturn(null);

        assertThat(service().resolveOrCreatePersonalTenant("")).isNull();
    }

    @Test
    void tenantsOfMapsActiveRefsToEntities() {
        when(membershipService.tenantsOf("alice")).thenReturn(List.of(ref("a", "a"), ref("b", "b")));
        when(tenantRepository.findById("a")).thenReturn(Optional.of(tenant("a", "a")));
        when(tenantRepository.findById("b")).thenReturn(Optional.of(tenant("b", "b")));

        assertThat(service().tenantsOf("alice")).extracting(TurTenant::getId).containsExactly("a", "b");
    }

    @Test
    void isActiveMemberDelegates() {
        when(membershipService.isActiveMember("t-1", "alice")).thenReturn(true);

        assertThat(service().isActiveMember("t-1", "alice")).isTrue();
    }

    @Test
    void setStatusMapsTuringStatusToSharedAndBack() {
        when(membershipService.setStatus("t-1", VigletTenantStatus.SUSPENDED))
                .thenReturn(new VigletTenantRef("t-1", "acme", "Acme", VigletTenantStatus.SUSPENDED, "FREE"));
        TurTenant entity = tenant("t-1", "acme");
        entity.setStatus(TurTenantStatus.SUSPENDED);
        when(tenantRepository.findById("t-1")).thenReturn(Optional.of(entity));

        TurTenant result = service().setStatus("t-1", TurTenantStatus.SUSPENDED);

        assertThat(result.getStatus()).isEqualTo(TurTenantStatus.SUSPENDED);
    }

    @Test
    void findAllReadsRegistryDirectly() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenant("a", "a")));

        assertThat(service().findAll()).extracting(TurTenant::getId).containsExactly("a");
    }

    @Test
    void normalizeSlugRejectsReservedAndMalformed() {
        var svc = service();
        assertThatThrownBy(() -> svc.normalizeSlug("admin"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> svc.normalizeSlug("turing"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> svc.normalizeSlug("A"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Invalid slug");
        assertThatThrownBy(() -> svc.normalizeSlug("bad slug!"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Invalid slug");
    }

    @Test
    void normalizeSlugLowercasesAndTrims() {
        assertThat(service().normalizeSlug("  My-Co  ")).isEqualTo("my-co");
    }

    @Test
    void basePersonalSlugSanitizesEmailsReservedAndShortNames() {
        assertThat(service().basePersonalSlug("Alice@Example.com")).isEqualTo("alice-example-com");
        assertThat(service().basePersonalSlug("admin")).isEqualTo("u-admin"); // reserved
        assertThat(service().basePersonalSlug("x")).isEqualTo("user-x");      // too short
        assertThat(service().basePersonalSlug("a".repeat(80)).length()).isLessThanOrEqualTo(56);
    }
}

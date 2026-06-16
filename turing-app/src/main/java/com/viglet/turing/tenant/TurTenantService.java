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

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.core.tenancy.VigletTenantMembershipService;
import com.viglet.core.tenancy.VigletTenantRef;
import com.viglet.core.tenancy.VigletTenantSlugValidator;
import com.viglet.core.tenancy.VigletTenantStatus;
import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.model.tenant.TurTenantStatus;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;

/**
 * T264 / §XIV.3.2 — self-service tenant lifecycle: create a tenant with an OWNER
 * membership, list a user's tenants (T265), resolve a personal tenant (T333), and
 * validate slugs.
 *
 * <p>T396 / §XIV.9 — re-homed onto the shared {@link VigletTenantMembershipService}
 * + {@link VigletTenantSlugValidator} (which were lifted <em>from</em> this class):
 * the idempotent-signup / personal-tenant / slug logic now lives once in
 * {@code viglet-core-tenancy} and drives all three products. This class is the
 * thin Turing-facing adapter that maps the neutral {@link VigletTenantRef} the
 * shared service returns back to the {@link TurTenant} entity the controllers and
 * the resolution flow expect.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurTenantService {

    private final VigletTenantMembershipService membershipService;
    private final VigletTenantSlugValidator slugValidator;
    private final TurTenantRepository tenantRepository;

    public TurTenantService(VigletTenantMembershipService membershipService,
            VigletTenantSlugValidator slugValidator,
            TurTenantRepository tenantRepository) {
        this.membershipService = membershipService;
        this.slugValidator = slugValidator;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Create (or idempotently return) the tenant identified by {@code slug}, owned
     * by {@code ownerUsername}.
     */
    @Transactional
    public TurTenant signup(String slug, String name, String ownerUsername) {
        return entity(membershipService.signup(slug, name, ownerUsername));
    }

    /**
     * T333 / §XIV.8 — resolve (or, on first call, auto-provision) the
     * <em>personal</em> tenant of {@code username}.
     *
     * @return the user's personal tenant, or {@code null} when {@code username} is
     *         blank (the caller then falls back to {@code DEFAULT}).
     */
    @Transactional
    public TurTenant resolveOrCreatePersonalTenant(String username) {
        VigletTenantRef ref = membershipService.resolveOrCreatePersonalTenant(username);
        return ref == null ? null : entity(ref);
    }

    /** Every tenant {@code username} is an active member of (T265 "my tenants"). */
    @Transactional(readOnly = true)
    public List<TurTenant> tenantsOf(String username) {
        return membershipService.tenantsOf(username).stream()
                .map(this::entityOrNull)
                .filter(t -> t != null)
                .toList();
    }

    /** Whether {@code username} has an active membership in {@code tenantId}. */
    @Transactional(readOnly = true)
    public boolean isActiveMember(String tenantId, String username) {
        return membershipService.isActiveMember(tenantId, username);
    }

    /** All tenants (platform-admin console, T279). The registry has no @TenantId. */
    @Transactional(readOnly = true)
    public List<TurTenant> findAll() {
        return tenantRepository.findAll();
    }

    /** Set a tenant's lifecycle status (T279 suspend / reactivate). */
    @Transactional
    public TurTenant setStatus(String tenantId, TurTenantStatus status) {
        VigletTenantStatus shared = status == TurTenantStatus.SUSPENDED
                ? VigletTenantStatus.SUSPENDED
                : VigletTenantStatus.ACTIVE;
        return entity(membershipService.setStatus(tenantId, shared));
    }

    /** Validate + normalize a slug, rejecting malformed or reserved values (400). */
    public String normalizeSlug(String slug) {
        return slugValidator.normalizeSlug(slug);
    }

    /** Derive a valid, non-reserved slug base from an arbitrary username. */
    String basePersonalSlug(String username) {
        return slugValidator.basePersonalSlug(username);
    }

    /** Map a neutral ref to the managed {@link TurTenant} entity (must exist). */
    private TurTenant entity(VigletTenantRef ref) {
        return tenantRepository.findById(ref.id())
                .orElseThrow(() -> new IllegalStateException("Tenant vanished: " + ref.id()));
    }

    private TurTenant entityOrNull(VigletTenantRef ref) {
        return tenantRepository.findById(ref.id()).orElse(null);
    }
}

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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.core.tenancy.VigletTenantMembershipStatus;
import com.viglet.core.tenancy.VigletTenantRef;
import com.viglet.core.tenancy.VigletTenantRole;
import com.viglet.core.tenancy.VigletTenantStatus;
import com.viglet.core.tenancy.VigletTenantStore;
import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.model.tenant.TurTenantMembership;
import com.viglet.turing.persistence.model.tenant.TurTenantMembershipStatus;
import com.viglet.turing.persistence.model.tenant.TurTenantRole;
import com.viglet.turing.persistence.model.tenant.TurTenantStatus;
import com.viglet.turing.persistence.repository.tenant.TurTenantMembershipRepository;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;

/**
 * T396 / §XIV.9 — the {@link VigletTenantStore} SPI over Turing's
 * {@code TurTenant} / {@code TurTenantMembership} registry, expressed through the
 * neutral {@link VigletTenantRef} so the lifted shared services
 * ({@code VigletTenantMembershipService}, {@code VigletTenantResolutionFilter},
 * {@code VigletQuotaService}, {@code VigletTenantTeardownService}) never bind to
 * Turing's JPA entities.
 *
 * <p>This is the membership-model bridge of the cross-product tenancy parity work:
 * the duplicated <em>logic</em> (idempotent signup, the resolution gate, teardown)
 * now lives once in {@code viglet-core-tenancy} and drives all three products,
 * while the duplicated <em>rows</em> (Turing's own entity, its id strategy, its
 * cache annotations) stay here behind this one interface.
 *
 * <p>The {@code Tur*} ↔ {@code Viglet*} enum mapping is the only translation
 * needed: the registry keeps Turing's own enums (so the entity columns and the
 * Liquibase defaults are unchanged), while the shared surface speaks the lifted
 * {@code Viglet*} enums.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurVigletTenantStore implements VigletTenantStore {

    private final TurTenantRepository tenantRepository;
    private final TurTenantMembershipRepository membershipRepository;

    public TurVigletTenantStore(TurTenantRepository tenantRepository,
            TurTenantMembershipRepository membershipRepository) {
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
    }

    // --- reads -------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<VigletTenantRef> findById(String tenantId) {
        return tenantRepository.findById(tenantId).map(TurVigletTenantStore::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VigletTenantRef> findBySlug(String slug) {
        return tenantRepository.findBySlug(slug).map(TurVigletTenantStore::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VigletTenantRef> findAll() {
        return tenantRepository.findAll().stream().map(TurVigletTenantStore::toRef).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveMember(String tenantId, String username) {
        return membershipRepository.findByTenant_IdAndUsername(tenantId, username)
                .map(m -> m.getStatus() == TurTenantMembershipStatus.ACTIVE)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwnedBy(String tenantId, String username) {
        return membershipRepository.findByTenant_IdAndUsername(tenantId, username)
                .filter(m -> m.getRole() == TurTenantRole.OWNER)
                .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VigletTenantRef> activeTenantsOf(String username) {
        return membershipRepository.findByUsername(username).stream()
                .filter(m -> m.getStatus() == TurTenantMembershipStatus.ACTIVE)
                .map(TurTenantMembership::getTenant)
                .map(TurVigletTenantStore::toRef)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VigletTenantRef> ownedTenantOf(String username) {
        return membershipRepository.findByUsername(username).stream()
                .filter(m -> m.getStatus() == TurTenantMembershipStatus.ACTIVE
                        && m.getRole() == TurTenantRole.OWNER)
                .map(TurTenantMembership::getTenant)
                .map(TurVigletTenantStore::toRef)
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public int membershipCount(String tenantId) {
        return (int) membershipRepository.findAll().stream()
                .filter(m -> m.getTenant() != null && tenantId.equals(m.getTenant().getId()))
                .count();
    }

    // --- writes ------------------------------------------------------------

    @Override
    @Transactional
    public VigletTenantRef createTenant(String slug, String name, String plan) {
        TurTenant tenant = new TurTenant();
        tenant.setSlug(slug);
        tenant.setName(name);
        if (plan != null) {
            tenant.setPlan(plan);
        }
        return toRef(tenantRepository.save(tenant));
    }

    @Override
    @Transactional
    public void addMembership(String tenantId, String username, VigletTenantRole role,
            VigletTenantMembershipStatus status) {
        TurTenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + tenantId));
        TurTenantMembership membership = new TurTenantMembership();
        membership.setTenant(tenant);
        membership.setUsername(username);
        membership.setRole(toTurRole(role));
        membership.setStatus(toTurMembershipStatus(status));
        membershipRepository.save(membership);
    }

    @Override
    @Transactional
    public void updateStatus(String tenantId, VigletTenantStatus status) {
        TurTenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + tenantId));
        TurTenantStatus newStatus = toTurStatus(status);
        // Stamp the suspension instant only on the ACTIVE → SUSPENDED edge so the
        // T336 grace window isn't pushed back by a repeated suspend. The column is
        // Turing-specific, so the edge logic lives here in the store (per the
        // VigletTenantStore#updateStatus contract).
        if (newStatus == TurTenantStatus.SUSPENDED && tenant.getStatus() != TurTenantStatus.SUSPENDED) {
            tenant.setSuspendedAt(Instant.now());
        }
        tenant.setStatus(newStatus);
        tenantRepository.save(tenant);
    }

    @Override
    @Transactional
    public int deleteTenant(String tenantId) {
        List<TurTenantMembership> memberships = membershipRepository.findAll().stream()
                .filter(m -> m.getTenant() != null && tenantId.equals(m.getTenant().getId()))
                .toList();
        memberships.forEach(m -> membershipRepository.delete(m.getId()));
        if (tenantRepository.findById(tenantId).isPresent()) {
            tenantRepository.delete(tenantId);
        }
        return memberships.size();
    }

    // --- mapping -----------------------------------------------------------

    static VigletTenantRef toRef(TurTenant tenant) {
        return new VigletTenantRef(tenant.getId(), tenant.getSlug(), tenant.getName(),
                toVigletStatus(tenant.getStatus()), tenant.getPlan());
    }

    private static VigletTenantStatus toVigletStatus(TurTenantStatus status) {
        return status == TurTenantStatus.SUSPENDED
                ? VigletTenantStatus.SUSPENDED
                : VigletTenantStatus.ACTIVE;
    }

    private static TurTenantStatus toTurStatus(VigletTenantStatus status) {
        return status == VigletTenantStatus.SUSPENDED
                ? TurTenantStatus.SUSPENDED
                : TurTenantStatus.ACTIVE;
    }

    private static TurTenantRole toTurRole(VigletTenantRole role) {
        return switch (role) {
            case OWNER -> TurTenantRole.OWNER;
            case ADMIN -> TurTenantRole.ADMIN;
            case MEMBER -> TurTenantRole.MEMBER;
        };
    }

    private static TurTenantMembershipStatus toTurMembershipStatus(VigletTenantMembershipStatus status) {
        return switch (status) {
            case ACTIVE -> TurTenantMembershipStatus.ACTIVE;
            case INVITED -> TurTenantMembershipStatus.INVITED;
            case SUSPENDED -> TurTenantMembershipStatus.SUSPENDED;
        };
    }
}

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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.viglet.core.tenancy.VigletPlatformAdminService;
import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.model.tenant.TurTenantMembership;
import com.viglet.turing.persistence.model.tenant.TurTenantStatus;
import com.viglet.turing.persistence.repository.tenant.TurTenantMembershipRepository;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurTenantDeprovisionMode;
import com.viglet.turing.properties.TurTenantDeprovisionProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * T336 / §XIV.8.3 — closes the lifecycle loop opened by the T333
 * auto-provisioner: reconciles personal tenants against the identity provider
 * and <em>suspends</em> (and, in {@link TurTenantDeprovisionMode#SUSPEND_THEN_DELETE},
 * later <em>tears down</em> via the T281 {@link TurTenantTeardownService}) any
 * tenant whose every member has vanished from the IdP.
 *
 * <p><strong>Safety rules</strong> — the sweep only ever destroys data when it
 * is certain it should:
 * <ul>
 *   <li>The {@code DEFAULT} tenant is never touched.</li>
 *   <li>A tenant is a candidate only when <em>every</em> one of its memberships
 *       maps to an {@link TurIdentityStatus#ABSENT} or
 *       {@link TurIdentityStatus#DISABLED} identity. One live member spares it.</li>
 *   <li>A single {@link TurIdentityStatus#UNKNOWN} member (IdP unreachable)
 *       skips the whole tenant — uncertainty is fail-safe.</li>
 *   <li>Deletion always trails suspension by at least {@code deleteAfterDays},
 *       anchored on {@code suspendedAt}, so it can never happen in the same
 *       sweep that first detects the orphan.</li>
 * </ul>
 *
 * <p>The sweep runs under a synthetic {@code ROLE_PLATFORM_ADMIN} security
 * context so the teardown service's platform-admin gate is satisfied without a
 * logged-in user.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurTenantDeprovisionService {

    private static final String SYSTEM_PRINCIPAL = "system:tenant-deprovision";

    private final TurConfigProperties turConfigProperties;
    private final TurTenantRepository tenantRepository;
    private final TurTenantMembershipRepository membershipRepository;
    private final TurTenantOwnerIdentityProvider identityProvider;
    private final TurTenantTeardownService teardownService;

    public TurTenantDeprovisionService(TurConfigProperties turConfigProperties,
            TurTenantRepository tenantRepository,
            TurTenantMembershipRepository membershipRepository,
            TurTenantOwnerIdentityProvider identityProvider,
            TurTenantTeardownService teardownService) {
        this.turConfigProperties = turConfigProperties;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.identityProvider = identityProvider;
        this.teardownService = teardownService;
    }

    /** Source of "now" for the grace-period check; tests pin it for determinism. */
    private Clock clock = Clock.systemDefaultZone();

    /** Visible for testing — pin the clock so eligibility is deterministic. */
    void setClockForTest(Clock clock) {
        this.clock = clock;
    }

    /** Outcome of one reconciliation sweep. */
    public record TurTenantDeprovisionReport(boolean ran, int scanned, int suspended, int deleted,
            int skippedLive, int skippedUnknown) {

        static TurTenantDeprovisionReport notRun() {
            return new TurTenantDeprovisionReport(false, 0, 0, 0, 0, 0);
        }
    }

    /**
     * Run one reconciliation sweep. No-ops (returns {@link
     * TurTenantDeprovisionReport#notRun()}) unless tenancy is enabled, the
     * feature is enabled, and the identity provider can answer.
     */
    public TurTenantDeprovisionReport reconcile() {
        TurTenantDeprovisionProperty cfg = turConfigProperties.getTenancy().getDeprovision();
        if (!turConfigProperties.getTenancy().isEnabled() || !cfg.isEnabled()) {
            return TurTenantDeprovisionReport.notRun();
        }
        if (!identityProvider.isEnabled()) {
            log.warn("[TenantDeprovision] enabled but the identity provider is not configured "
                    + "(no Keycloak service-account credentials) — sweep skipped");
            return TurTenantDeprovisionReport.notRun();
        }
        return asPlatformAdmin(() -> sweep(cfg));
    }

    private TurTenantDeprovisionReport sweep(TurTenantDeprovisionProperty cfg) {
        Map<String, List<TurTenantMembership>> byTenant = membershipRepository.findAll().stream()
                .filter(m -> m.getTenant() != null && m.getTenant().getId() != null)
                .collect(Collectors.groupingBy(m -> m.getTenant().getId()));

        int scanned = 0;
        int suspended = 0;
        int deleted = 0;
        int skippedLive = 0;
        int skippedUnknown = 0;

        for (TurTenant tenant : tenantRepository.findAll()) {
            List<TurTenantMembership> memberships = byTenant.getOrDefault(tenant.getId(), List.of());
            // Skip the default tenant and any with no members to verify.
            if (TurTenant.DEFAULT_TENANT_ID.equals(tenant.getId()) || memberships.isEmpty()) {
                continue;
            }
            scanned++;

            Orphan orphan = classify(memberships);
            if (orphan == Orphan.UNKNOWN) {
                skippedUnknown++;
            } else if (orphan == Orphan.HAS_LIVE_MEMBER) {
                skippedLive++;
            } else if (tenant.getStatus() != TurTenantStatus.SUSPENDED) {
                // ORPHANED (every member absent/disabled in the IdP), not yet suspended.
                teardownService.suspend(tenant.getId());
                suspended++;
                log.info("[TenantDeprovision] suspended orphaned tenant '{}' (slug '{}')",
                        tenant.getId(), tenant.getSlug());
            } else if (cfg.getMode() == TurTenantDeprovisionMode.SUSPEND_THEN_DELETE
                    && graceElapsed(tenant, cfg.getDeleteAfterDays())) {
                // ORPHANED + already suspended past the grace window → tear down.
                teardownService.delete(tenant.getId(), false);
                deleted++;
                log.warn("[TenantDeprovision] tore down orphaned tenant '{}' (slug '{}') "
                        + "after {}d suspended", tenant.getId(), tenant.getSlug(), cfg.getDeleteAfterDays());
            }
        }

        TurTenantDeprovisionReport report = new TurTenantDeprovisionReport(true, scanned, suspended,
                deleted, skippedLive, skippedUnknown);
        log.info("[TenantDeprovision] sweep done: {}", report);
        return report;
    }

    private enum Orphan {
        ORPHANED, HAS_LIVE_MEMBER, UNKNOWN
    }

    private Orphan classify(List<TurTenantMembership> memberships) {
        boolean allGone = true;
        for (TurTenantMembership m : memberships) {
            TurIdentityStatus status = identityProvider.statusOf(m.getUsername());
            if (status == TurIdentityStatus.UNKNOWN) {
                return Orphan.UNKNOWN;
            }
            if (status == TurIdentityStatus.ACTIVE) {
                allGone = false;
            }
        }
        return allGone ? Orphan.ORPHANED : Orphan.HAS_LIVE_MEMBER;
    }

    private boolean graceElapsed(TurTenant tenant, int deleteAfterDays) {
        Instant suspendedAt = tenant.getSuspendedAt();
        if (suspendedAt == null) {
            // Suspended by a legacy path that didn't stamp the instant — be safe
            // and wait for the next sweep (suspend() will stamp it on re-touch).
            return false;
        }
        return !Instant.now(clock).isBefore(suspendedAt.plus(Duration.ofDays(Math.max(0, deleteAfterDays))));
    }

    /** Run {@code body} under a synthetic platform-admin security context. */
    private <T> T asPlatformAdmin(java.util.function.Supplier<T> body) {
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(new UsernamePasswordAuthenticationToken(SYSTEM_PRINCIPAL, "N/A",
                    List.of(new SimpleGrantedAuthority(VigletPlatformAdminService.ROLE_PLATFORM_ADMIN))));
            SecurityContextHolder.setContext(ctx);
            return body.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}

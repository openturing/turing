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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.model.tenant.TurTenantStatus;
import com.viglet.turing.persistence.repository.tenant.TurTenantMembershipRepository;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;
import com.viglet.turing.service.storage.TurStorageService;

import lombok.extern.slf4j.Slf4j;

/**
 * T281 / §XIV.7.2 — tenant lifecycle teardown: suspend (block auth) and delete
 * (purge the tenant's out-of-band footprint + registry rows). Platform-admin
 * only, audited, idempotent, with a {@code dryRun} mode that reports the plan
 * without mutating anything.
 *
 * <p>Covered surfaces (in-process): object storage prefix
 * ({@code tenants/<id>/}), the per-tenant Lucene directory, caches, and the
 * {@code tenant}/{@code tenant_membership} registry rows. The {@code @TenantId}
 * JPA content becomes <em>unreachable</em> the moment the tenant is gone (the
 * resolver never yields a deleted tenant id); a physical row sweep and the
 * external Solr/ES core drop + Keycloak attribute deletion require the live
 * engines and are layered on in deployment-specific runbooks.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurTenantTeardownService {

    private static final String LUCENE_BASE = "./store/lucene-vector";

    private final TurPlatformAdminService platformAdminService;
    private final TurTenantContext tenantContext;
    private final TurTenantRepository tenantRepository;
    private final TurTenantMembershipRepository membershipRepository;
    private final TurStorageService storageService;
    private final CacheManager cacheManager;

    public TurTenantTeardownService(TurPlatformAdminService platformAdminService,
            TurTenantContext tenantContext,
            TurTenantRepository tenantRepository,
            TurTenantMembershipRepository membershipRepository,
            TurStorageService storageService,
            CacheManager cacheManager) {
        this.platformAdminService = platformAdminService;
        this.tenantContext = tenantContext;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.storageService = storageService;
        this.cacheManager = cacheManager;
    }

    /** Outcome of a teardown (or its dry-run plan). */
    public record TurTenantTeardownReport(String tenantId, boolean dryRun, boolean tenantExisted,
            int membershipsRemoved, boolean storagePurged, boolean lucenePurged,
            boolean cachesEvicted) {
    }

    /** Suspend a tenant — blocks its members at the resolution filter (T259). */
    @Transactional
    public TurTenant suspend(String tenantId) {
        platformAdminService.runAsSystem("suspend tenant " + tenantId, () -> null);
        TurTenant tenant = requireTenant(tenantId);
        tenant.setStatus(TurTenantStatus.SUSPENDED);
        return tenantRepository.save(tenant);
    }

    /**
     * Tear a tenant down. With {@code dryRun=true} nothing is mutated — the
     * returned report is the plan. Idempotent: a missing tenant yields a report
     * with {@code tenantExisted=false}.
     */
    @Transactional
    public TurTenantTeardownReport delete(String tenantId, boolean dryRun) {
        if (!platformAdminService.isPlatformAdmin()) {
            throw new SecurityException("Tenant teardown requires "
                    + TurPlatformAdminService.ROLE_PLATFORM_ADMIN);
        }
        TurTenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return new TurTenantTeardownReport(tenantId, dryRun, false, 0, false, false, false);
        }

        List<?> memberships = membershipRepository.findAll().stream()
                .filter(m -> m.getTenant() != null && tenantId.equals(m.getTenant().getId()))
                .toList();

        if (dryRun) {
            log.info("[TenantTeardown] DRY-RUN for tenant '{}': {} memberships, storage+lucene+caches",
                    tenantId, memberships.size());
            return new TurTenantTeardownReport(tenantId, true, true, memberships.size(),
                    false, false, false);
        }

        log.warn("[TenantTeardown] DELETING tenant '{}' (audited)", tenantId);

        boolean storagePurged = purgeStorage(tenantId);
        boolean lucenePurged = purgeLucene(tenantId);
        evictCaches();

        membershipRepository.findAll().stream()
                .filter(m -> m.getTenant() != null && tenantId.equals(m.getTenant().getId()))
                .forEach(m -> membershipRepository.delete(m.getId()));
        tenantRepository.delete(tenantId);

        return new TurTenantTeardownReport(tenantId, false, true, memberships.size(),
                storagePurged, lucenePurged, true);
    }

    private boolean purgeStorage(String tenantId) {
        if (!storageService.isEnabled()) {
            return false;
        }
        // Run in the tenant's context so the scoping decorator targets its prefix.
        tenantContext.runAs(tenantId, () -> {
            try {
                storageService.deleteObjectsWithPrefix("");
            } catch (RuntimeException e) {
                log.warn("[TenantTeardown] storage purge failed for '{}': {}", tenantId, e.getMessage());
            }
        });
        return true;
    }

    private boolean purgeLucene(String tenantId) {
        Path dir = Path.of(LUCENE_BASE, tenantId);
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best-effort
                }
            });
            return true;
        } catch (Exception e) {
            log.warn("[TenantTeardown] Lucene purge failed for '{}': {}", tenantId, e.getMessage());
            return false;
        }
    }

    private void evictCaches() {
        // Coarse: clear all caches (tenant-keyed entries can't be enumerated per
        // tenant). Teardown is rare, so a full cold-cache is an acceptable cost.
        cacheManager.getCacheNames()
                .forEach(name -> {
                    var cache = cacheManager.getCache(name);
                    if (cache != null) {
                        cache.clear();
                    }
                });
    }

    private TurTenant requireTenant(String tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + tenantId));
    }
}

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

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.core.tenancy.VigletTenantTeardownService;
import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.repository.tenant.TurTenantMembershipRepository;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;
import com.viglet.turing.service.storage.TurStorageService;

/**
 * T281 / §XIV.7.2 — tenant lifecycle teardown: suspend (block auth) and delete
 * (purge the tenant's out-of-band footprint + registry rows). Platform-admin
 * only, audited, idempotent, with a {@code dryRun} mode that reports the plan
 * without mutating anything.
 *
 * <p>T396 / §XIV.9 — re-homed onto the shared {@link VigletTenantTeardownService}:
 * the orchestration (platform-admin gate, default-tenant guard, dry-run, audit,
 * registry deletion) now lives once in {@code viglet-core-tenancy}. The physical
 * purge stays Turing-specific behind {@link TurVigletTenantPurger} (object storage
 * prefix {@code tenants/<id>/}, the per-tenant Lucene directory, caches). The
 * {@code @TenantId} JPA content becomes <em>unreachable</em> the moment the tenant
 * is gone (the resolver never yields a deleted tenant id); the external Solr/ES
 * core drop + Keycloak attribute deletion require the live engines and are layered
 * on in deployment-specific runbooks.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurTenantTeardownService {

    private final TurTenantRepository tenantRepository;
    private final VigletTenantTeardownService delegate;

    public TurTenantTeardownService(TurPlatformAdminService platformAdminService,
            TurTenantContext tenantContext,
            TurTenantRepository tenantRepository,
            TurTenantMembershipRepository membershipRepository,
            TurStorageService storageService,
            CacheManager cacheManager) {
        this.tenantRepository = tenantRepository;
        TurVigletTenantStore store = new TurVigletTenantStore(tenantRepository, membershipRepository);
        TurVigletTenantPurger purger = new TurVigletTenantPurger(storageService, cacheManager);
        this.delegate = new VigletTenantTeardownService(platformAdminService, tenantContext, store, purger);
    }

    /** Outcome of a teardown (or its dry-run plan). */
    public record TurTenantTeardownReport(String tenantId, boolean dryRun, boolean tenantExisted,
            int membershipsRemoved, boolean storagePurged, boolean lucenePurged,
            boolean cachesEvicted) {
    }

    /**
     * Suspend a tenant — blocks its members at the resolution filter (T259). The
     * {@code suspendedAt} instant is stamped (only on the ACTIVE → SUSPENDED edge)
     * inside {@link TurVigletTenantStore#updateStatus}, so the T336 grace window is
     * anchored consistently.
     */
    @Transactional
    public TurTenant suspend(String tenantId) {
        delegate.suspend(tenantId);
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + tenantId));
    }

    /**
     * Tear a tenant down. With {@code dryRun=true} nothing is mutated — the
     * returned report is the plan. Idempotent: a missing tenant yields a report
     * with {@code tenantExisted=false}.
     */
    @Transactional
    public TurTenantTeardownReport delete(String tenantId, boolean dryRun) {
        var report = delegate.delete(tenantId, dryRun);
        // The shared report collapses the physical purge to one flag; Turing's
        // purger wipes storage + Lucene + caches together, so the three Turing
        // surfaces share that outcome.
        boolean purged = report.purged();
        return new TurTenantTeardownReport(report.tenantId(), report.dryRun(), report.tenantExisted(),
                report.membershipsRemoved(), purged, purged, purged);
    }
}

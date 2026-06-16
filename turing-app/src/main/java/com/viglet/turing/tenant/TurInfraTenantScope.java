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
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * §XIV.5.1 — the single place that applies tenant scoping to the
 * "bring-your-own-infra" instance entities (see {@link TurTenantOwnedInfra}).
 *
 * <p>Hibernate cannot do it for these because their {@code tenantId} is a plain
 * column, not {@code @TenantId} (so the shared GLOBAL/{@code null} pool stays
 * visible). This helper supplies the two operations every such controller
 * needs:
 * <ul>
 *   <li>{@link #stampOnCreate(TurTenantOwnedInfra)} — on create, claim the
 *       instance for the current tenant (a platform admin keeps it GLOBAL);</li>
 *   <li>{@link #visibleList(Supplier, Function)} — on read, return only the
 *       current tenant's instances plus the GLOBAL pool.</li>
 * </ul>
 *
 * <p>Both are no-ops when {@code turing.tenancy.enabled=false}: stamping leaves
 * {@code tenantId} untouched and listing returns the full unscoped result, so
 * single-tenant installs behave exactly as before.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurInfraTenantScope {

    private final TurTenantContext tenantContext;
    private final TurPlatformAdminService platformAdminService;

    public TurInfraTenantScope(TurTenantContext tenantContext,
            TurPlatformAdminService platformAdminService) {
        this.tenantContext = tenantContext;
        this.platformAdminService = platformAdminService;
    }

    /**
     * Claim a newly created instance for the current tenant. No-op when tenancy
     * is off, when the instance already carries an owner, or when the caller is
     * a platform admin — admins provision the shared GLOBAL pool, which is
     * modeled by a {@code null} tenant id.
     *
     * @return the same {@code entity}, for fluent use before {@code save(...)}.
     */
    public <T extends TurTenantOwnedInfra> T stampOnCreate(T entity) {
        if (entity != null
                && tenantContext.isTenancyEnabled()
                && entity.getTenantId() == null
                && !platformAdminService.isPlatformAdmin()) {
            entity.setTenantId(tenantContext.resolveCurrentTenant());
        }
        return entity;
    }

    /**
     * Claim an instance created during an <em>import</em> for the current tenant.
     * Identical to {@link #stampOnCreate} except it additionally requires a
     * tenant to be actually bound to the thread (a real authenticated request):
     * a startup/seed import (e.g. {@code TurExportImportOnStartup}) runs with no
     * bound tenant and must leave the instance GLOBAL ({@code tenantId = null}),
     * visible to every tenant — exactly the platform-provided pool. Without this
     * guard the {@code DEFAULT} fallback from {@link TurTenantContext#resolveCurrentTenant()}
     * would stamp seeded infra as DEFAULT-owned and hide it from other tenants.
     *
     * @return the same {@code entity}, for fluent use before {@code save(...)}.
     */
    public <T extends TurTenantOwnedInfra> T stampOnImport(T entity) {
        if (tenantContext.getCurrentTenant() == null) {
            return entity; // startup/seed import — keep GLOBAL
        }
        return stampOnCreate(entity);
    }

    /**
     * Return the instances visible to the current tenant. When tenancy is on,
     * delegates to {@code visibleToTenant} (the repository's
     * {@code tenantId = :current OR IS NULL} query); when off, returns the full
     * {@code unscoped} list so single-tenant behavior is unchanged.
     */
    public <T> List<T> visibleList(Supplier<List<T>> unscoped,
            Function<String, List<T>> visibleToTenant) {
        if (!tenantContext.isTenancyEnabled()) {
            return unscoped.get();
        }
        return visibleToTenant.apply(tenantContext.resolveCurrentTenant());
    }
}

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

import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.model.tenant.TurTenantStatus;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T273 / §XIV.4.7 — runs a {@code @Scheduled} sweep <em>once per active
 * tenant</em>, with {@link TurTenantContext} bound so each pass only sees (and
 * the Hibernate {@code @TenantId} filter only returns) that tenant's rows.
 *
 * <p>A naive {@code @Scheduled} sweep calls {@code findAll()} and would either
 * see every tenant's rows at once (DEFAULT context) or nothing. Wrapping the
 * body in {@link #forEachActiveTenant} restores per-tenant isolation.
 *
 * <p>When tenancy is off the work runs exactly once (no binding), so existing
 * single-tenant schedules are unchanged. A failure in one tenant's pass is
 * logged and does not abort the remaining tenants.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurTenantScheduledFanOut {

    private final TurTenantContext tenantContext;
    private final TurTenantRepository tenantRepository;

    public TurTenantScheduledFanOut(TurTenantContext tenantContext,
            TurTenantRepository tenantRepository) {
        this.tenantContext = tenantContext;
        this.tenantRepository = tenantRepository;
    }

    /** Run {@code work} once when tenancy is off, else once per ACTIVE tenant. */
    public void forEachActiveTenant(Runnable work) {
        if (!tenantContext.isTenancyEnabled()) {
            work.run();
            return;
        }
        for (TurTenant tenant : tenantRepository.findAll()) {
            if (tenant.getStatus() != TurTenantStatus.ACTIVE) {
                continue;
            }
            try {
                tenantContext.runAs(tenant.getId(), work);
            } catch (RuntimeException e) {
                log.warn("[TenantFanOut] scheduled sweep failed for tenant '{}': {}",
                        tenant.getId(), e.getMessage(), e);
            }
        }
    }
}

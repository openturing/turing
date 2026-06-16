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

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.viglet.core.tenancy.VigletPlanLimits;
import com.viglet.core.tenancy.VigletQuotaService;

/**
 * T277 / §XIV.5.3 — enforces plan-based quotas at resource-create time and at
 * LLM-call admission.
 *
 * <ul>
 *   <li>Over the agent/site count limit → HTTP 402 (Payment Required).</li>
 *   <li>Over the monthly token budget → HTTP 429 (Too Many Requests).</li>
 * </ul>
 *
 * <p>T396 / §XIV.9 — re-homed onto the shared {@link VigletQuotaService}: the
 * compare-and-throw enforcement (and its "no-op when tenancy is off / for the
 * default tenant" guard) now lives once in {@code viglet-core-tenancy}, driven by
 * Turing's {@link TurVigletResourceCounter} (the per-plan limits + the per-tenant
 * counts). This class keeps the Turing-named, per-resource entry points so the
 * create endpoints read clearly; the resource keys are
 * {@link TurVigletResourceCounter#AGENTS}, {@link TurVigletResourceCounter#SITES}
 * and {@link TurVigletResourceCounter#MONTHLY_TOKENS}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurTenantQuotaService {

    private final VigletQuotaService quotaService;

    public TurTenantQuotaService(VigletQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    /** Resolve the limits for the current tenant (UNLIMITED when off / default). */
    public VigletPlanLimits currentLimits() {
        return quotaService.currentLimits();
    }

    /** Reject (402) when the current tenant is at its agent limit. */
    public void checkCanCreateAgent() {
        quotaService.check(TurVigletResourceCounter.AGENTS);
    }

    /** Reject (402) when the current tenant is at its site limit. */
    public void checkCanCreateSite() {
        quotaService.check(TurVigletResourceCounter.SITES);
    }

    /** Reject (429) when the current tenant has exhausted its monthly token budget. */
    public void checkLlmAdmission() {
        quotaService.check(TurVigletResourceCounter.MONTHLY_TOKENS, HttpStatus.TOO_MANY_REQUESTS);
    }
}

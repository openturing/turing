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

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;

/**
 * T277 / §XIV.5.3 — enforces {@link TurTenantPlanLimits plan-based quotas} at
 * resource-create time and at LLM-call admission.
 *
 * <ul>
 *   <li>Over the agent/site count limit → HTTP 402 (Payment Required) with a
 *       clear upgrade message.</li>
 *   <li>Over the monthly token budget → HTTP 429 (Too Many Requests).</li>
 * </ul>
 *
 * <p>All checks are <strong>no-ops when tenancy is off</strong> or for the
 * {@code DEFAULT} tenant, so single-tenant installs are never throttled. Agent
 * and site counts come from the {@code @TenantId}-filtered repositories (so
 * {@code count()} is already scoped to the current tenant); token usage is
 * summed from the per-tenant attribution added in T276.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurTenantQuotaService {

    private final TurTenantContext tenantContext;
    private final TurTenantRepository tenantRepository;
    private final TurAIAgentRepository agentRepository;
    private final TurSNSiteRepository snSiteRepository;
    private final TurLLMTokenUsageRepository tokenUsageRepository;

    public TurTenantQuotaService(TurTenantContext tenantContext,
            TurTenantRepository tenantRepository,
            TurAIAgentRepository agentRepository,
            TurSNSiteRepository snSiteRepository,
            TurLLMTokenUsageRepository tokenUsageRepository) {
        this.tenantContext = tenantContext;
        this.tenantRepository = tenantRepository;
        this.agentRepository = agentRepository;
        this.snSiteRepository = snSiteRepository;
        this.tokenUsageRepository = tokenUsageRepository;
    }

    /** Resolve the limits for the current tenant (UNLIMITED when off/DEFAULT). */
    public TurTenantPlanLimits currentLimits() {
        if (!tenantContext.isTenancyEnabled()) {
            return TurTenantPlanLimits.UNLIMITED;
        }
        String tenantId = tenantContext.resolveCurrentTenant();
        if (tenantId == null || TurTenant.DEFAULT_TENANT_ID.equals(tenantId)) {
            return TurTenantPlanLimits.UNLIMITED;
        }
        return tenantRepository.findById(tenantId)
                .map(t -> TurTenantPlanLimits.forPlan(t.getPlan()))
                .orElse(TurTenantPlanLimits.UNLIMITED);
    }

    /** Reject (402) when the current tenant is at its agent limit. */
    public void checkCanCreateAgent() {
        TurTenantPlanLimits limits = currentLimits();
        if (!limits.isUnlimited(limits.maxAgents()) && agentRepository.count() >= limits.maxAgents()) {
            throw quotaExceeded(HttpStatus.PAYMENT_REQUIRED,
                    "Plan limit reached: max " + limits.maxAgents() + " AI agents. Upgrade your plan.");
        }
    }

    /** Reject (402) when the current tenant is at its site limit. */
    public void checkCanCreateSite() {
        TurTenantPlanLimits limits = currentLimits();
        if (!limits.isUnlimited(limits.maxSites()) && snSiteRepository.count() >= limits.maxSites()) {
            throw quotaExceeded(HttpStatus.PAYMENT_REQUIRED,
                    "Plan limit reached: max " + limits.maxSites() + " sites. Upgrade your plan.");
        }
    }

    /** Reject (429) when the current tenant has exhausted its monthly token budget. */
    public void checkLlmAdmission() {
        TurTenantPlanLimits limits = currentLimits();
        if (limits.isUnlimited(limits.monthlyTokens())) {
            return;
        }
        String tenantId = tenantContext.resolveCurrentTenant();
        LocalDateTime monthStart = LocalDateTime.now(ZoneId.systemDefault())
                .withDayOfMonth(1).toLocalDate().atStartOfDay();
        long used = tokenUsageRepository.findUsageByTenant(monthStart, monthStart.plusMonths(1)).stream()
                .filter(row -> tenantId != null && tenantId.equals(row[0]))
                .mapToLong(row -> ((Number) row[3]).longValue())
                .sum();
        if (used >= limits.monthlyTokens()) {
            throw quotaExceeded(HttpStatus.TOO_MANY_REQUESTS,
                    "Monthly token budget exhausted (" + limits.monthlyTokens() + "). Try next month or upgrade.");
        }
    }

    private ResponseStatusException quotaExceeded(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}

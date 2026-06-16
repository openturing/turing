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
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.viglet.core.tenancy.VigletPlanLimits;
import com.viglet.core.tenancy.VigletResourceCounter;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

/**
 * T396 / §XIV.9 — the {@link VigletResourceCounter} SPI feeding the shared
 * {@code VigletQuotaService}: Turing supplies the per-plan limits and the current
 * per-tenant counts; the compare-and-throw enforcement is shared.
 *
 * <p>The resource keys are the opaque contract between this counter and the
 * Turing quota call sites: {@value #AGENTS}, {@value #SITES},
 * {@value #MONTHLY_TOKENS}. Agent and site counts come from the
 * {@code @TenantId}-filtered repositories (so {@code count()} is already scoped to
 * the current tenant); token usage is summed from the per-tenant attribution
 * added in T276.
 *
 * <p>Plan mapping (lifted from {@code TurTenantPlanLimits}): the {@code FREE} plan
 * is bounded (3 agents, 2 sites, 100k monthly tokens); any other plan is unlimited
 * until a richer billing model lands, so a paying tenant is never blocked by
 * accident.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurVigletResourceCounter implements VigletResourceCounter {

    public static final String AGENTS = "agents";
    public static final String SITES = "sites";
    public static final String MONTHLY_TOKENS = "monthlyTokens";

    /** The bounded FREE plan: 3 agents, 2 sites, 100k monthly tokens. */
    private static final VigletPlanLimits FREE = VigletPlanLimits.of(
            AGENTS, 3L, SITES, 2L, MONTHLY_TOKENS, 100_000L);

    private final TurAIAgentRepository agentRepository;
    private final TurSNSiteRepository snSiteRepository;
    private final TurLLMTokenUsageRepository tokenUsageRepository;

    public TurVigletResourceCounter(TurAIAgentRepository agentRepository,
            TurSNSiteRepository snSiteRepository,
            TurLLMTokenUsageRepository tokenUsageRepository) {
        this.agentRepository = agentRepository;
        this.snSiteRepository = snSiteRepository;
        this.tokenUsageRepository = tokenUsageRepository;
    }

    @Override
    public long count(String tenantId, String resourceKey) {
        return switch (resourceKey) {
            case AGENTS -> agentRepository.count();
            case SITES -> snSiteRepository.count();
            case MONTHLY_TOKENS -> monthlyTokens(tenantId);
            default -> 0L;
        };
    }

    @Override
    public VigletPlanLimits limitsFor(String plan) {
        if (plan != null && "FREE".equals(plan.trim().toUpperCase(Locale.ROOT))) {
            return FREE;
        }
        return VigletPlanLimits.UNLIMITED;
    }

    /** Tokens consumed by {@code tenantId} so far this calendar month (T276 attribution). */
    private long monthlyTokens(String tenantId) {
        LocalDateTime monthStart = LocalDateTime.now(ZoneId.systemDefault())
                .withDayOfMonth(1).toLocalDate().atStartOfDay();
        return tokenUsageRepository.findUsageByTenant(monthStart, monthStart.plusMonths(1)).stream()
                .filter(row -> tenantId != null && tenantId.equals(row[0]))
                .mapToLong(row -> ((Number) row[3]).longValue())
                .sum();
    }
}

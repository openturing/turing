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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.core.tenancy.VigletPlanLimits;
import com.viglet.core.tenancy.VigletQuotaService;
import com.viglet.core.tenancy.VigletTenantContext;
import com.viglet.turing.api.exception.TurQuotaExhaustedException;
import com.viglet.turing.genai.vigletai.TurVigletUpgradeTargetService;

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
 * <p>T959 / §XIV.10.4 — the shared service's refusal is a bare status, which is
 * correct as a gate and useless as a product surface. Every check here therefore
 * rethrows it as a {@link TurQuotaExhaustedException} carrying the counts, the
 * reset instant and — when this install shares a secret with the managed service
 * — a signed upgrade target, so the console can render the two exits instead of a
 * request that merely failed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurTenantQuotaService {

    private static final DateTimeFormatter RESET_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final VigletQuotaService quotaService;
    private final VigletTenantContext tenantContext;
    private final TurVigletResourceCounter resourceCounter;
    private final TurVigletUpgradeTargetService upgradeTargetService;

    public TurTenantQuotaService(VigletQuotaService quotaService,
            VigletTenantContext tenantContext,
            TurVigletResourceCounter resourceCounter,
            TurVigletUpgradeTargetService upgradeTargetService) {
        this.quotaService = quotaService;
        this.tenantContext = tenantContext;
        this.resourceCounter = resourceCounter;
        this.upgradeTargetService = upgradeTargetService;
    }

    /** Resolve the limits for the current tenant (UNLIMITED when off / default). */
    public VigletPlanLimits currentLimits() {
        return quotaService.currentLimits();
    }

    /** Reject (402) when the current tenant is at its agent limit. */
    public void checkCanCreateAgent() {
        guard(TurVigletResourceCounter.AGENTS, HttpStatus.PAYMENT_REQUIRED);
    }

    /** Reject (402) when the current tenant is at its site limit. */
    public void checkCanCreateSite() {
        guard(TurVigletResourceCounter.SITES, HttpStatus.PAYMENT_REQUIRED);
    }

    /** Reject (429) when the current tenant has exhausted its monthly token budget. */
    public void checkLlmAdmission() {
        guard(TurVigletResourceCounter.MONTHLY_TOKENS, HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Runs the shared check and, when it refuses, replaces the bare status with
     * the refusal the console can act on. The enrichment reads the same counter
     * the gate just consulted, so the numbers in the body are the numbers that
     * caused the refusal.
     */
    private void guard(String resourceKey, HttpStatus status) {
        try {
            quotaService.check(resourceKey, status);
        } catch (ResponseStatusException e) {
            throw exhausted(resourceKey, status, e);
        }
    }

    private TurQuotaExhaustedException exhausted(String resourceKey, HttpStatus status,
            ResponseStatusException cause) {
        String tenantId = tenantContext.resolveCurrentTenant();
        long limit = currentLimits().limit(resourceKey);
        long used = resourceCounter.count(tenantId, resourceKey);
        OffsetDateTime resetsAt = resetsAt(resourceKey);
        String upgradeUrl = upgradeTargetService.upgradeUrl(tenantId, resourceKey).orElse(null);
        return new TurQuotaExhaustedException(status, resourceKey, used, limit, resetsAt,
                upgradeUrl, detail(resourceKey, used, limit, resetsAt, cause));
    }

    /**
     * When the window rolls over. Only the monthly token budget resets by
     * waiting — an agent or a site count stays where it is until the operator
     * deletes one or moves to a bigger plan, and saying "resets on…" there would
     * be a promise the product does not keep.
     */
    private static OffsetDateTime resetsAt(String resourceKey) {
        if (!TurVigletResourceCounter.MONTHLY_TOKENS.equals(resourceKey)) {
            return null;
        }
        ZoneId zone = ZoneId.systemDefault();
        return LocalDate.now(zone).withDayOfMonth(1).plusMonths(1).atStartOfDay(zone)
                .toOffsetDateTime();
    }

    /**
     * The human-readable half of the refusal. The console renders its own copy
     * from the structured properties; this is what a curl, a log line or a
     * non-console SDK consumer sees, so it has to stand on its own.
     */
    private static String detail(String resourceKey, long used, long limit,
            OffsetDateTime resetsAt, ResponseStatusException cause) {
        if (limit <= 0) {
            // The gate refused but the limit no longer reads as bounded (a plan
            // changed mid-request). Keep the shared service's wording rather
            // than inventing numbers.
            return cause.getReason() != null ? cause.getReason() : "Plan limit reached.";
        }
        String base = "Plan limit reached for '" + resourceKey + "': " + used + " of " + limit + " used.";
        return resetsAt == null
                ? base + " Upgrade the plan, or use your own API key."
                : base + " Resets on " + RESET_FORMAT.format(resetsAt) + ".";
    }
}

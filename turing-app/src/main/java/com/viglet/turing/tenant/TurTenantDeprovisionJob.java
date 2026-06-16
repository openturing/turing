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

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * T336 / §XIV.8.3 — periodic sweep that drives {@link
 * TurTenantDeprovisionService#reconcile()}. Suspends (and, opt-in, tears down)
 * personal tenants whose owner(s) have been deleted/disabled in Keycloak.
 *
 * <p>Cron defaults to daily at 03:25 ({@code turing.tenancy.deprovision.cron}).
 * Cluster-wide-once via ShedLock so a multi-node deploy never runs the
 * destructive path twice. The whole feature stays dormant unless
 * {@code turing.tenancy.deprovision.enabled=true} (and tenancy itself is on);
 * {@code reconcile()} short-circuits otherwise, so the lock is cheap.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurTenantDeprovisionJob {

    private final TurTenantDeprovisionService deprovisionService;

    public TurTenantDeprovisionJob(TurTenantDeprovisionService deprovisionService) {
        this.deprovisionService = deprovisionService;
    }

    @Scheduled(cron = "${turing.tenancy.deprovision.cron:0 25 3 * * *}")
    @SchedulerLock(name = "tenantDeprovisionSweep",
            lockAtMostFor = "PT30M", lockAtLeastFor = "PT1S")
    public void run() {
        try {
            deprovisionService.reconcile();
        } catch (RuntimeException e) {
            log.error("[TenantDeprovision] sweep failed: {}", e.getMessage(), e);
        }
    }
}

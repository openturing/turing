/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatanalytics;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * T71 / §VII.8.b — daily champion-challenger sweep. Delegates to
 * {@link TurChampionChallengerService#runScheduledPromotions()}, which evaluates
 * each opted-in A/B experiment ({@code autoPromote=true} on any variant) and
 * auto-promotes the statistically-significant winner.
 *
 * <p>Runs once per day (cron {@code 0 47 3 * * *}, overridable via
 * {@code turing.chat.champion.promote.cron}). Cluster-wide-once via ShedLock so
 * multi-node deploys don't double-promote. Experiments without the opt-in flag
 * stay under manual operator control via the
 * {@code POST /experiment/{key}/promote} endpoint.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChampionChallengerPromotionJob {

    private final TurChampionChallengerService championChallengerService;
    private final com.viglet.turing.tenant.TurTenantScheduledFanOut tenantFanOut;

    public TurChampionChallengerPromotionJob(TurChampionChallengerService championChallengerService,
            com.viglet.turing.tenant.TurTenantScheduledFanOut tenantFanOut) {
        this.championChallengerService = championChallengerService;
        this.tenantFanOut = tenantFanOut;
    }

    @Scheduled(cron = "${turing.chat.champion.promote.cron:0 47 3 * * *}")
    @SchedulerLock(name = "championChallengerPromote",
            lockAtMostFor = "PT30M", lockAtLeastFor = "PT1S")
    public void run() {
        // T273 / §XIV.4.7 — run the promotion sweep once per active tenant.
        tenantFanOut.forEachActiveTenant(this::promoteForCurrentTenant);
    }

    private void promoteForCurrentTenant() {
        int promoted = championChallengerService.runScheduledPromotions();
        if (promoted > 0) {
            log.info("[ChampionChallenger] daily sweep promoted a winner in {} experiment(s)", promoted);
        }
    }
}

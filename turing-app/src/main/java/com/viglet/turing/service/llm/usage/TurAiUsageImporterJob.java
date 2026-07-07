/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.usage;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * T183 / §X.14.c — nightly importer of vendor-reported AI spend. Pulls the last
 * {@code turing.usage.anthropic.lookback-days} days from the Anthropic Admin
 * usage + cost reports and upserts them into {@code tur_ai_usage_daily} via
 * {@link TurAiUsageImportService}.
 *
 * <p>A lookback window (not just yesterday) is used because vendor usage/cost
 * figures settle over a day or two; re-importing the recent window each night
 * lets late-arriving numbers correct themselves (the import is idempotent —
 * it deletes the window before re-insert).
 *
 * <p>Cluster-wide-once via ShedLock. Strictly opt-in: a no-op until an Admin
 * key is configured (and {@code turing.usage.anthropic.enabled=true}), so the
 * job is free until an operator turns it on.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurAiUsageImporterJob {

    private final TurAiUsageImportService importService;
    private final boolean enabled;
    private final int lookbackDays;

    public TurAiUsageImporterJob(TurAiUsageImportService importService,
            @Value("${turing.usage.anthropic.enabled:false}") boolean enabled,
            @Value("${turing.usage.anthropic.lookback-days:2}") int lookbackDays) {
        this.importService = importService;
        this.enabled = enabled;
        this.lookbackDays = Math.max(1, lookbackDays);
    }

    @Scheduled(cron = "${turing.usage.import-cron:0 40 3 * * *}")
    @SchedulerLock(name = "aiUsageImport", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1S")
    public void run() {
        if (!enabled || !importService.isAnthropicConfigured()) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = today.minusDays(lookbackDays);
        try {
            int rows = importService.importAnthropic(from, today);
            log.info("[Usage][Anthropic] nightly import wrote {} row(s) for {}..{}", rows, from, today);
        } catch (RuntimeException e) {
            log.error("[Usage][Anthropic] nightly import failed: {}", e.getMessage(), e);
        }
    }
}

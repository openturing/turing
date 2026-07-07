/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.citation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * §X.7.d / T155 — daily citation drift scan. Delegates to
 * {@link TurCitationDriftService#runDriftScan()}, which re-resolves recent
 * persisted Anthropic Citations (T152–T154) against the live index and flips
 * {@code citationStale} on any whose source was re-indexed since the answer or
 * whose cited passage is no longer present.
 *
 * <p>Runs once per day (cron {@code 0 23 4 * * *}, overridable via
 * {@code turing.genai.citation-drift.cron}). Cluster-wide-once via ShedLock so
 * multi-node deploys don't re-resolve the same citations in parallel. The scan
 * itself short-circuits when {@code turing.genai.citation-drift.enabled} is
 * {@code false} (the default), so the job is a cheap no-op until an operator
 * opts in.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurCitationDriftDetectionJob {

    private final TurCitationDriftService driftService;

    public TurCitationDriftDetectionJob(TurCitationDriftService driftService) {
        this.driftService = driftService;
    }

    @Scheduled(cron = "${turing.genai.citation-drift.cron:0 23 4 * * *}")
    @SchedulerLock(name = "citationDriftDetection",
            lockAtMostFor = "PT30M", lockAtLeastFor = "PT1S")
    public void run() {
        if (!driftService.isEnabled()) {
            return;
        }
        int flagged = driftService.runDriftScan();
        if (flagged > 0) {
            log.info("[CitationDrift] daily scan flagged {} citation(s) as stale", flagged);
        }
    }
}

/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatslots;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * T66 / §VII.6.g — daily sweep that enforces every agent's
 * {@code RETAIN_DAYS} submission-retention policy by delegating to
 * {@link TurSubmissionRetentionService#purgeExpiredSubmissions()}.
 *
 * <p>Runs once per day (cron {@code 0 41 3 * * *}, overridable via
 * {@code turing.chat.submission.cleanup.cron}). Cluster-wide-once via
 * ShedLock so multi-node deploys don't race on the same deletes. The
 * {@code DELETE_AFTER_EXPORT} mode is event-driven (fired from the T65 export
 * endpoint) and is intentionally not part of this scheduled path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurSubmissionRetentionCleanupJob {

    private final TurSubmissionRetentionService retentionService;

    public TurSubmissionRetentionCleanupJob(TurSubmissionRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${turing.chat.submission.cleanup.cron:0 41 3 * * *}")
    @SchedulerLock(name = "submissionRetentionCleanup",
            lockAtMostFor = "PT30M", lockAtLeastFor = "PT1S")
    public void run() {
        int deleted = retentionService.purgeExpiredSubmissions();
        if (deleted > 0) {
            log.info("[SubmissionRetention] daily sweep deleted {} expired submission(s)", deleted);
        }
    }
}

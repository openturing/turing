/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.service.chatanalytics;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Periodic housekeeping for the analytics store. Drops sessions whose
 * {@code startedAt} is older than {@code turing.chat.analytics.retention-days}.
 * Defaults to {@code 0} (disabled) — operators opt in explicitly because
 * "delete history" is a one-way action.
 *
 * <p>Cluster-wide-once via ShedLock — so a 4-node deploy doesn't fire the
 * delete four times in parallel.
 *
 * <p>Tunables:
 * <ul>
 *   <li>{@code turing.chat.analytics.retention-days} — keep sessions started
 *       within the last N days. {@code 0} disables purging entirely.</li>
 *   <li>{@code turing.chat.analytics.retention.cron} — cron expression,
 *       default daily at 03:17 UTC. Off-peak time avoids racing with the
 *       enricher's 5-minute cycle.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@Service
public class TurChatAnalyticsRetentionWorker {

    private final TurChatAnalyticsService analyticsService;
    private final int retentionDays;

    public TurChatAnalyticsRetentionWorker(TurChatAnalyticsService analyticsService,
            @Value("${turing.chat.analytics.retention-days:0}") int retentionDays) {
        this.analyticsService = analyticsService;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${turing.chat.analytics.retention.cron:0 17 3 * * *}")
    @SchedulerLock(name = "chatAnalyticsRetention",
            lockAtMostFor = "PT30M", lockAtLeastFor = "PT1S")
    public void run() {
        if (retentionDays <= 0) {
            log.debug("[ChatRetention] disabled (retention-days={})", retentionDays);
            return;
        }
        if (!analyticsService.isEnabled()) return;
        Instant threshold = Instant.now().minus(Duration.ofDays(retentionDays));
        long removed = analyticsService.getStore().purgeOlderThan(threshold);
        log.info("[ChatRetention] removed {} session(s) older than {} ({} days)",
                removed, threshold, retentionDays);
    }
}

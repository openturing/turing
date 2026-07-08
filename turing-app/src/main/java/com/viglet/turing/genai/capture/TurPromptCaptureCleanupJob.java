/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.capture;

import java.time.Duration;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.service.storage.TurStorageService;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * T618 / §XXXIV.6 — the retention-window bound of the per-turn prompt-capture
 * store. Complements the write-time bounds (per-conversation ring + per-entry
 * byte cap) with a time-based global prune so a deployment that opens many
 * conversations over time can't grow the store without limit.
 *
 * <p>Walks every {@code .../prompt-captures/turn-*.json} object across all
 * tenants and deletes those older than
 * {@link TurPromptCaptureProperties#retentionDays}. Age comes from the
 * object's {@code lastModified} (tolerantly parsed by {@link TurPromptCaptureTimes});
 * an entry whose backend reports no parseable timestamp is left alone (never
 * deleted undated) — the per-conversation ring still bounds it.
 *
 * <p>Cluster-wide-once via ShedLock. A no-op when storage is disabled or the
 * retention window is non-positive.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPromptCaptureCleanupJob {

    private final TurStorageService storageService;
    private final TurPromptCaptureProperties properties;

    public TurPromptCaptureCleanupJob(TurStorageService storageService,
            TurPromptCaptureProperties properties) {
        this.storageService = storageService;
        this.properties = properties;
    }

    @Scheduled(cron = "${turing.prompt.capture.cleanup-cron:0 40 3 * * *}")
    @SchedulerLock(name = "promptCaptureRetentionSweep",
            lockAtMostFor = "PT30M", lockAtLeastFor = "PT1S")
    public void run() {
        int retentionDays = properties.getRetentionDays();
        if (!storageService.isEnabled() || retentionDays <= 0) {
            log.debug("[PromptCapture] retention sweep disabled (storage={}, retentionDays={})",
                    storageService.isEnabled(), retentionDays);
            return;
        }
        long cutoff = System.currentTimeMillis() - Duration.ofDays(retentionDays).toMillis();
        int deleted = 0;
        int scanned = 0;
        try {
            for (TurAssetItem item : storageService.listAllObjects()) {
                if (item.directory() || !isCaptureObject(item.name())) {
                    continue;
                }
                scanned++;
                long age = TurPromptCaptureTimes.toEpochMillis(item.lastModified());
                if (age > 0 && age < cutoff) {
                    storageService.deleteObject(item.name());
                    deleted++;
                }
            }
        } catch (Exception e) {
            log.warn("[PromptCapture] retention sweep aborted: {}", e.getMessage());
            return;
        }
        if (deleted > 0) {
            log.info("[PromptCapture] retention sweep deleted {} capture(s) older than {} day(s) "
                    + "(scanned {})", deleted, retentionDays, scanned);
        } else {
            log.debug("[PromptCapture] retention sweep found nothing to prune (scanned {})", scanned);
        }
    }

    private static boolean isCaptureObject(String objectName) {
        if (objectName == null) {
            return false;
        }
        String name = objectName.replace('\\', '/');
        return name.contains("/" + TurPromptCaptureService.CAPTURES_DIR + "/")
                && TurPromptCaptureService.parseTurnIndex(name) != null;
    }
}

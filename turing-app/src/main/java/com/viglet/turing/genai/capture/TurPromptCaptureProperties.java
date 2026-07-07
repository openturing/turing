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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * T618 / §XXXIV.6 — bounds for the per-turn assembled-prompt capture store.
 *
 * <p>Per-agent opt-in ({@code TurAIAgent.promptCaptureEnabled}) turns capture
 * <em>on</em>; these properties keep it from growing without limit — the same
 * "retention window + per-entry cap + global cap" posture as the T427 tool-call
 * trace, but adapted to the pluggable {@link com.viglet.turing.service.storage.TurStorageService}
 * seam instead of an in-memory ring:
 *
 * <ul>
 *   <li><b>{@link #maxTurnsPerConversation}</b> — per-conversation ring: after
 *       each write, turns beyond this count (oldest turn index first) are pruned,
 *       so a single long conversation cannot accumulate unboundedly.</li>
 *   <li><b>{@link #maxBytesPerEntry}</b> — per-entry cap: a serialized capture
 *       larger than this is skipped (a runaway prompt never lands a giant blob).</li>
 *   <li><b>{@link #retentionDays}</b> — retention window: a scheduled sweep
 *       ({@code TurPromptCaptureCleanupJob}) prunes captures older than this
 *       across every conversation, bounding the store globally over time.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Component
@ConfigurationProperties("turing.prompt.capture")
public class TurPromptCaptureProperties {

    /** Per-conversation ring size — newest N turns kept, older pruned. */
    private int maxTurnsPerConversation = 50;

    /** Per-entry size cap in bytes — a larger serialized capture is skipped. */
    private int maxBytesPerEntry = 262144;

    /** Retention window in days — the scheduled sweep prunes captures older than this. */
    private int retentionDays = 30;

    /** Cron for the retention sweep; the default runs nightly at 03:40. */
    private String cleanupCron = "0 40 3 * * *";
}

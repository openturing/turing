/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.match.dto;

import java.time.Instant;
import java.util.List;

/**
 * A Persona Match project as returned to the studio (Block AT / §XLIII). Used
 * both for the list ({@code sources}/{@code personas} omitted, counts present)
 * and the detail view (collections populated). Doubles as the create/update
 * request body — only {@code name}, {@code description}, {@code enabled},
 * {@code schedule}, {@code llmInstanceId}, {@code fitThreshold} and the three
 * {@code driftWebhook*} fields are read on write ({@code effectiveFitThreshold}
 * and {@code driftWebhookAuthConfigured} are derived, read-only; the auth header
 * is write-only and never returned). Matches the frontend {@code MatchProject}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPersonaMatchProjectDto(
        String id,
        String name,
        String description,
        boolean enabled,
        String schedule,
        String llmInstanceId,
        String llmName,
        /**
         * T826 — the project's own good-fit bar, or {@code null} to inherit the
         * deployment default. This is the write field (what the form edits).
         */
        Double fitThreshold,
        /**
         * T826 — the bar actually in force (the project's, else the deployment
         * default), so the studio can colour the heatmap and label the legend
         * without knowing the server's configuration. Derived; ignored on write.
         */
        double effectiveFitThreshold,
        /**
         * T827 — where to POST the drift report after a scheduled run that
         * regressed the project; blank = the alert is off (default).
         */
        String driftWebhookUrl,
        /**
         * T827 — plaintext {@code Authorization} value, <strong>write-only</strong>:
         * encrypted on save and always {@code null} on read. Leave blank on an
         * update to keep the stored secret (see {@code driftWebhookAuthConfigured}).
         */
        String driftWebhookAuthHeader,
        /** T827 — read-only: whether a secret is stored, so the form can say so. */
        boolean driftWebhookAuthConfigured,
        /**
         * T827 — also alert when the project average dropped by more than this many
         * fit points; {@code null} = alert on threshold crossings only.
         */
        Double driftWebhookAvgDrop,
        Instant lastRunAt,
        int sourceCount,
        int personaCount,
        List<TurPersonaMatchSourceDto> sources,
        List<TurPersonaMatchPersonaRefDto> personas) {
}

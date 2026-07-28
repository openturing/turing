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
 * {@code schedule}, {@code llmInstanceId} and {@code fitThreshold} are read on
 * write ({@code effectiveFitThreshold} is derived, read-only). Matches the
 * frontend {@code MatchProject}.
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
        Instant lastRunAt,
        int sourceCount,
        int personaCount,
        List<TurPersonaMatchSourceDto> sources,
        List<TurPersonaMatchPersonaRefDto> personas) {
}

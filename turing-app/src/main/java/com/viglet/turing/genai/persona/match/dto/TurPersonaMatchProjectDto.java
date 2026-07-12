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
 * {@code schedule} and {@code llmInstanceId} are read on write. Matches the
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
        Instant lastRunAt,
        int sourceCount,
        int personaCount,
        List<TurPersonaMatchSourceDto> sources,
        List<TurPersonaMatchPersonaRefDto> personas) {
}

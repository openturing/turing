/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.research.dto;

import java.time.Instant;
import java.util.List;

/**
 * One persona's interview transcript within a study (Block AW / §XLVI.2). The
 * {@code turns} are omitted from list/summary payloads and populated only on the
 * detail/run views to keep the roster list lean.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchInterviewDto(
        String id,
        String personaId,
        String personaName,
        String status,
        int turnCount,
        String error,
        Instant startedAt,
        Instant completedAt,
        List<TurResearchTurnDto> turns) {
}

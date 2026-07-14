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

/**
 * One point in a study's insight-drift series (Block AW / §XLVI.4, T729): the
 * deterministic sufficiency snapshot captured after a run, plus the theme delta
 * versus the previous point so the studio's trend chart can show movement.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchDriftPointDto(
        Instant capturedAt,
        int interviewCount,
        int totalUniqueThemes,
        int adequateAtN,
        boolean saturated,
        int newThemesSincePrevious) {
}

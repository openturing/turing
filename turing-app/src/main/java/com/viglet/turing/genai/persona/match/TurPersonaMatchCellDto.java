/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.match;

import java.util.List;

import com.viglet.turing.genai.persona.fit.TurContentFitMisfit;

/**
 * One matrix cell as returned to the studio (Block AT / §XLIII) — the
 * client-facing projection of {@code TurPersonaMatchCell}, matching the
 * frontend {@code MatchCell} shape. Carried both in the live SSE run stream and
 * the persisted matrix report.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPersonaMatchCellDto(
        String sourceId,
        String personaId,
        double fitScore,
        double readability,
        boolean llmUsed,
        String summary,
        List<String> fits,
        List<TurContentFitMisfit> misfits) {
}

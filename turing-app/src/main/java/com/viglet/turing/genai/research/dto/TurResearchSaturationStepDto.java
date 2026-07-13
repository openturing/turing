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

/**
 * One step on a study's saturation curve (Block AW / §XLVI.3, T723) — the state
 * <em>after</em> the persona at roster {@code index} (1-based) was folded in.
 * {@code newThemes} is how many distinct theme tokens this persona contributed
 * that none of the earlier personas had already raised; {@code cumulativeThemes}
 * is the running total of distinct tokens; {@code noveltyRatio} is
 * {@code newThemes / personaThemeCount} (0 when the persona raised nothing).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchSaturationStepDto(
        int index,
        String personaId,
        String personaName,
        int personaThemes,
        int newThemes,
        int cumulativeThemes,
        double noveltyRatio) {
}

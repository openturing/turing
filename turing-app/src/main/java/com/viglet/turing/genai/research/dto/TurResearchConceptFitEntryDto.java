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

import com.viglet.turing.genai.persona.fit.TurContentFitResult;

/**
 * One roster persona's reaction to a CONCEPT_TEST study's concept (Block AW /
 * §XLVI.4, T727) — the persona identity paired with the shared
 * {@link TurContentFitResult} produced by the Block AT
 * {@code TurPersonaContentFitEvaluator}, so concept feedback and content-fit
 * scoring speak the exact same verdict shape.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchConceptFitEntryDto(String personaId, String personaName,
        TurContentFitResult result) {
}

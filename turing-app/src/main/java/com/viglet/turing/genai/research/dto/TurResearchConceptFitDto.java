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

import java.util.List;

/**
 * The concept-fit report for a CONCEPT_TEST study (Block AW / §XLVI.4, T727): the
 * study's concept scored against each roster persona through the shared Block AT
 * content-fit evaluator, plus the roster's mean fit. Fail-open like the sibling
 * insights report — {@code available} is {@code false} (with {@code error}) when
 * the study is not a concept test, has no concept text, or has no roster.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchConceptFitDto(
        boolean available,
        String error,
        String conceptText,
        double averageFitScore,
        List<TurResearchConceptFitEntryDto> personas) {

    /** An unavailable concept-fit report. */
    public static TurResearchConceptFitDto unavailable(String error) {
        return new TurResearchConceptFitDto(false, error, null, 0.0, List.of());
    }
}

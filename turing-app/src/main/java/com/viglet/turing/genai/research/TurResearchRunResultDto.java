/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.research;

import java.util.List;

import com.viglet.turing.genai.research.dto.TurResearchInterviewDto;

/**
 * The result of a study cohort-interview run (Block AW / §XLVI.2, T721) — the
 * full set of per-persona interview transcripts, mirroring Persona Match's
 * matrix DTO. Returned by the blocking run and fetched after the SSE stream
 * completes (the persisted interviews are authoritative).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchRunResultDto(String studyId, List<TurResearchInterviewDto> interviews) {
}

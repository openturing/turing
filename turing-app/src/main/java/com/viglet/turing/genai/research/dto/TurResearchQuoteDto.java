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
 * A verbatim participant quote inside an insights theme (Block AW / §XLVI.3,
 * T722), traceable back to the interview it came from. {@code personaId} /
 * {@code personaName} attribute it; {@code resolved} is {@code true} when the id
 * matched a persona actually in the study roster (fabricated attributions are
 * flagged {@code false} rather than silently trusted).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchQuoteDto(
        String personaId,
        String personaName,
        String quote,
        boolean resolved) {
}

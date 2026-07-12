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

/**
 * The persisted N×N matrix of a Persona Match project (Block AT / §XLIII) —
 * every {@code (content × persona)} cell computed so far. The report lenses
 * (by-content / by-persona) are derived from this flat cell list.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPersonaMatchMatrixDto(
        String projectId,
        List<TurPersonaMatchCellDto> cells) {
}

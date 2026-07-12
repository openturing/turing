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

/**
 * A project-scoped content as returned to the studio (Block AT / §XLIII) — the
 * client-facing projection of {@code TurPersonaMatchSource} (never the raw
 * cached text). Matches the frontend {@code MatchSource}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPersonaMatchSourceDto(
        String id,
        String type,
        String name,
        String ref,
        String siteName,
        int charCount,
        String status,
        String error) {
}

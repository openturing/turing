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
 * One affinity edge in a study's theme graph (Block AW / §XLVI.3, T724): a persona
 * node ({@code source}) supporting a theme node ({@code target}), weighted by how
 * many verbatim quotes that persona contributed to the theme.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchGraphEdgeDto(
        String source,
        String target,
        int weight) {
}

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
 * One node in a study's theme/affinity graph (Block AW / §XLVI.3, T724). A node is
 * either a synthesized {@code THEME} (a concept/pain point) or a {@code PERSONA}
 * (an audience member who voiced it). {@code weight} carries the theme's
 * prevalence (distinct personas supporting it) or the persona's involvement
 * (total supporting quotes) so the studio can size the node.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchGraphNodeDto(
        String id,
        String label,
        String type,
        int weight) {

    /** Node kinds. */
    public static final String TYPE_THEME = "THEME";
    public static final String TYPE_PERSONA = "PERSONA";
}

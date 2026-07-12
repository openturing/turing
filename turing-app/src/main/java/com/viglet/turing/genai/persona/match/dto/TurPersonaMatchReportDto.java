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

import java.util.List;

/**
 * The two bidirectional aggregations over a project's persisted matrix cells
 * (Block AT / §XLIII, T699): <em>by content</em> (each content → its personas
 * ranked by fit) and <em>by persona</em> (each persona → its contents ranked by
 * fit), each carrying the group's average fit. The raw matrix is served
 * separately as {@code TurPersonaMatchMatrixDto}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPersonaMatchReportDto(
        List<ByContent> byContent,
        List<ByPersona> byPersona) {

    /** One ranked entry: the counterpart id and its fit score. */
    public record Ranked(String id, double fitScore) {
    }

    public record ByContent(String sourceId, double avg, List<Ranked> personas) {
    }

    public record ByPersona(String personaId, double avg, List<Ranked> sources) {
    }
}

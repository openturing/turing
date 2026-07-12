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

import java.time.Instant;

/**
 * A progress event streamed over SSE while a Persona Match project's N×N
 * analysis runs (Block AT / §XLIII, T698). The studio subscribes and fills the
 * heatmap live: {@link Type#STARTED} carries the total cell count,
 * {@link Type#CELL} carries one freshly computed (or reused) cell,
 * {@link Type#DONE} stamps the run's completion, and {@link Type#ERROR} reports
 * a run-level failure.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPersonaMatchRunEvent(
        Type type,
        String projectId,
        int total,
        int completed,
        TurPersonaMatchCellDto cell,
        Instant lastRunAt,
        String error) {

    public enum Type {
        STARTED,
        CELL,
        DONE,
        ERROR
    }

    public static TurPersonaMatchRunEvent started(String projectId, int total) {
        return new TurPersonaMatchRunEvent(Type.STARTED, projectId, total, 0, null, null, null);
    }

    public static TurPersonaMatchRunEvent cell(String projectId, int total, int completed,
            TurPersonaMatchCellDto cell) {
        return new TurPersonaMatchRunEvent(Type.CELL, projectId, total, completed, cell, null, null);
    }

    public static TurPersonaMatchRunEvent done(String projectId, int total, Instant lastRunAt) {
        return new TurPersonaMatchRunEvent(Type.DONE, projectId, total, total, null, lastRunAt, null);
    }

    public static TurPersonaMatchRunEvent error(String projectId, String error) {
        return new TurPersonaMatchRunEvent(Type.ERROR, projectId, 0, 0, null, null, error);
    }
}

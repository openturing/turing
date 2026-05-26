/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.agent.TurRoutine;
import com.viglet.turing.persistence.model.agent.TurRoutineKind;

/**
 * REST projection for {@link TurRoutine}. Mirrors the entity fields one-to-one
 * for the admin CRUD form behind {@code /api/genai/routine}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurRoutineDto(
        String id,
        String name,
        String description,
        TurRoutineKind kind,
        String nativeToolName,
        String groovyScript,
        Integer defaultTimeoutMs,
        Boolean enabled) {

    public static TurRoutineDto from(TurRoutine routine) {
        if (routine == null) {
            return null;
        }
        return new TurRoutineDto(
                routine.getId(),
                routine.getName(),
                routine.getDescription(),
                routine.getKind(),
                routine.getNativeToolName(),
                routine.getGroovyScript(),
                routine.getDefaultTimeoutMs(),
                routine.isEnabled());
    }
}

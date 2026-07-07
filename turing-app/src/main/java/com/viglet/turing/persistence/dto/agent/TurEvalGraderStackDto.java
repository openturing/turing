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

import java.util.List;

/**
 * T600 / §XXXIII.15 — API view / upsert payload of a reusable grader stack.
 * On create the {@code id} is ignored; {@code configs} are the ordered grader
 * entries.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurEvalGraderStackDto(
        String id,
        String name,
        String description,
        List<GraderConfigEntry> configs) {

    /**
     * One grader entry of a stack.
     *
     * @param graderId   the grader id (resolved against the registry)
     * @param name       human label
     * @param kind       grader kind name (informational)
     * @param configJson per-instance config
     * @param weight     aggregate weight
     * @param threshold  pass threshold
     * @param blocking   1 = a failing result blocks the stack
     * @param enabled    1 = active
     * @param sortOrder  order in the stack
     */
    public record GraderConfigEntry(
            String graderId,
            String name,
            String kind,
            String configJson,
            double weight,
            double threshold,
            int blocking,
            int enabled,
            int sortOrder) {
    }
}

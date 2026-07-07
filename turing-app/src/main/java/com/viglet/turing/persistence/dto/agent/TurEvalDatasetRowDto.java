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

import com.viglet.turing.persistence.model.agent.TurEvalDatasetRow;

/**
 * T596 / §XXXIII.11 — API view of one {@link TurEvalDatasetRow}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurEvalDatasetRowDto(
        String id,
        String name,
        String seedTurnsJson,
        String expectedSlotsJson,
        String expectedOutcome,
        String expectedNodeId,
        String rubric,
        String referenceAnswer,
        String tags,
        String metadataJson,
        int sortOrder) {

    public static TurEvalDatasetRowDto fromEntity(TurEvalDatasetRow r) {
        return new TurEvalDatasetRowDto(r.getId(), r.getName(), r.getSeedTurnsJson(),
                r.getExpectedSlotsJson(),
                r.getExpectedOutcome() == null ? null : r.getExpectedOutcome().name(),
                r.getExpectedNodeId(), r.getRubric(), r.getReferenceAnswer(), r.getTags(),
                r.getMetadataJson(), r.getSortOrder());
    }
}

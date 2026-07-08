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

import java.time.LocalDateTime;
import java.util.List;

import com.viglet.turing.persistence.model.agent.TurEvalDataset;

/**
 * T596 / §XXXIII.11 — API view of a {@link TurEvalDataset}. {@code rows} is
 * empty on list responses and populated on the detail response.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurEvalDatasetDto(
        String id,
        String name,
        String description,
        int version,
        int rowCount,
        String metadataJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<TurEvalDatasetRowDto> rows) {

    /** Summary view (no rows) for list responses. */
    public static TurEvalDatasetDto summary(TurEvalDataset d, int rowCount) {
        return new TurEvalDatasetDto(d.getId(), d.getName(), d.getDescription(), d.getVersion(),
                rowCount, d.getMetadataJson(), d.getCreatedAt(), d.getUpdatedAt(), List.of());
    }

    /** Detail view with rows. */
    public static TurEvalDatasetDto detail(TurEvalDataset d, List<TurEvalDatasetRowDto> rows) {
        return new TurEvalDatasetDto(d.getId(), d.getName(), d.getDescription(), d.getVersion(),
                rows.size(), d.getMetadataJson(), d.getCreatedAt(), d.getUpdatedAt(), rows);
    }
}

/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.dto.agent.TurEvalDatasetDiffDto;
import com.viglet.turing.persistence.dto.agent.TurEvalDatasetRowDto;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.agent.TurEvalDatasetSnapshot;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRowRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetSnapshotRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * T598 / §XXXIII.13 — freezes immutable dataset snapshots and diffs two
 * versions. A snapshot serializes the dataset's current rows and bumps the
 * live version; a diff compares two snapshots' rows by id → added / removed /
 * changed. Reports pin the version they ran against so a regression comparison
 * knows whether the dataset itself drifted.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurEvalDatasetVersionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurEvalDatasetRepository datasetRepository;
    private final TurEvalDatasetRowRepository datasetRowRepository;
    private final TurEvalDatasetSnapshotRepository snapshotRepository;

    public TurEvalDatasetVersionService(TurEvalDatasetRepository datasetRepository,
            TurEvalDatasetRowRepository datasetRowRepository,
            TurEvalDatasetSnapshotRepository snapshotRepository) {
        this.datasetRepository = datasetRepository;
        this.datasetRowRepository = datasetRowRepository;
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * Freezes the dataset's current rows into an immutable snapshot at its
     * current version, then bumps the live version by one.
     */
    @Transactional
    public TurEvalDatasetSnapshot snapshot(String datasetId) {
        TurEvalDataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dataset not found: " + datasetId));
        List<TurEvalDatasetRowDto> rows =
                datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc(datasetId).stream()
                        .map(TurEvalDatasetRowDto::fromEntity)
                        .toList();
        TurEvalDatasetSnapshot snapshot = new TurEvalDatasetSnapshot();
        snapshot.setDatasetId(datasetId);
        snapshot.setVersion(dataset.getVersion());
        snapshot.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        snapshot.setRowsJson(OBJECT_MAPPER.writeValueAsString(rows));
        snapshot = snapshotRepository.save(snapshot);

        dataset.setVersion(dataset.getVersion() + 1);
        dataset.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        datasetRepository.save(dataset);
        return snapshot;
    }

    /** Versions of a dataset that have been snapshotted, newest first. */
    public List<Integer> versions(String datasetId) {
        return snapshotRepository.findByDatasetIdOrderByVersionDesc(datasetId).stream()
                .map(TurEvalDatasetSnapshot::getVersion)
                .toList();
    }

    /** Row-level drift between two snapshotted versions. */
    public TurEvalDatasetDiffDto diff(String datasetId, int fromVersion, int toVersion) {
        Map<String, TurEvalDatasetRowDto> from = rowsByIdAt(datasetId, fromVersion);
        Map<String, TurEvalDatasetRowDto> to = rowsByIdAt(datasetId, toVersion);
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, TurEvalDatasetRowDto> e : to.entrySet()) {
            TurEvalDatasetRowDto before = from.get(e.getKey());
            if (before == null) {
                added.add(label(e.getValue()));
            } else if (!before.equals(e.getValue())) {
                changed.add(label(e.getValue()));
            }
        }
        for (Map.Entry<String, TurEvalDatasetRowDto> e : from.entrySet()) {
            if (!to.containsKey(e.getKey())) {
                removed.add(label(e.getValue()));
            }
        }
        return new TurEvalDatasetDiffDto(datasetId, fromVersion, toVersion, added, removed, changed);
    }

    private Map<String, TurEvalDatasetRowDto> rowsByIdAt(String datasetId, int version) {
        TurEvalDatasetSnapshot snapshot = snapshotRepository
                .findByDatasetIdAndVersion(datasetId, version)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No snapshot for dataset " + datasetId + " version " + version));
        List<TurEvalDatasetRowDto> rows = parseRows(snapshot.getRowsJson());
        Map<String, TurEvalDatasetRowDto> byId = new LinkedHashMap<>();
        for (TurEvalDatasetRowDto row : rows) {
            byId.put(row.id(), row);
        }
        return byId;
    }

    private static List<TurEvalDatasetRowDto> parseRows(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<TurEvalDatasetRowDto> rows = OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
            return rows == null ? List.of() : rows;
        } catch (RuntimeException e) {
            log.warn("[AgentEval] bad snapshot rowsJson: {}", e.getMessage());
            return List.of();
        }
    }

    private static String label(TurEvalDatasetRowDto row) {
        return row.name() != null && !row.name().isBlank() ? row.name() : row.id();
    }
}

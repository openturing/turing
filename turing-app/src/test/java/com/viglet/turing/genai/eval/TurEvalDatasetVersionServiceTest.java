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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.agent.TurEvalDatasetDiffDto;
import com.viglet.turing.persistence.dto.agent.TurEvalDatasetRowDto;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.agent.TurEvalDatasetRow;
import com.viglet.turing.persistence.model.agent.TurEvalDatasetSnapshot;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRowRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetSnapshotRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * T598 / §XXXIII.13 — snapshot freezing (+ version bump) and row-level diff.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurEvalDatasetVersionServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private TurEvalDatasetRepository datasetRepository;
    @Mock
    private TurEvalDatasetRowRepository datasetRowRepository;
    @Mock
    private TurEvalDatasetSnapshotRepository snapshotRepository;

    private TurEvalDatasetVersionService service() {
        return new TurEvalDatasetVersionService(datasetRepository, datasetRowRepository,
                snapshotRepository);
    }

    private static TurEvalDatasetRowDto row(String id, String name, String reference) {
        return new TurEvalDatasetRowDto(id, name, "[\"hi\"]", null, "ANY", null, null, reference,
                null, null, 0);
    }

    private static TurEvalDatasetSnapshot snapshotWith(int version, List<TurEvalDatasetRowDto> rows) {
        TurEvalDatasetSnapshot s = new TurEvalDatasetSnapshot();
        s.setVersion(version);
        s.setRowsJson(MAPPER.writeValueAsString(rows));
        return s;
    }

    @Test
    void snapshotFreezesRowsAndBumpsVersion() {
        TurEvalDataset dataset = new TurEvalDataset();
        dataset.setId("d1");
        dataset.setVersion(1);
        when(datasetRepository.findById("d1")).thenReturn(Optional.of(dataset));
        TurEvalDatasetRow r = new TurEvalDatasetRow();
        r.setId("r1");
        r.setName("case A");
        when(datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc("d1"))
                .thenReturn(List.of(r));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TurEvalDatasetSnapshot snap = service().snapshot("d1");

        assertThat(snap.getVersion()).isEqualTo(1);
        assertThat(snap.getRowsJson()).contains("r1").contains("case A");

        ArgumentCaptor<TurEvalDataset> captor = ArgumentCaptor.forClass(TurEvalDataset.class);
        verify(datasetRepository).save(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(2);
    }

    @Test
    void diffReportsAddedRemovedChanged() {
        when(snapshotRepository.findByDatasetIdAndVersion(eq("d1"), eq(1))).thenReturn(
                Optional.of(snapshotWith(1, List.of(row("r1", "A", "old"), row("r2", "B", "keep")))));
        when(snapshotRepository.findByDatasetIdAndVersion(eq("d1"), eq(2))).thenReturn(
                Optional.of(snapshotWith(2, List.of(row("r1", "A", "new"), row("r3", "C", "fresh")))));

        TurEvalDatasetDiffDto diff = service().diff("d1", 1, 2);

        assertThat(diff.changed()).containsExactly("A");
        assertThat(diff.added()).containsExactly("C");
        assertThat(diff.removed()).containsExactly("B");
    }
}

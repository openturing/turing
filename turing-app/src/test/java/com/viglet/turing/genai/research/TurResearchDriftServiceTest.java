/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.research.dto.TurResearchDriftDto;
import com.viglet.turing.genai.research.dto.TurResearchSaturationResultDto;
import com.viglet.turing.persistence.model.research.TurResearchInsightSnapshot;
import com.viglet.turing.persistence.model.research.TurResearchInterview;
import com.viglet.turing.persistence.model.research.TurResearchInterviewStatus;
import com.viglet.turing.persistence.model.research.TurResearchSchedule;
import com.viglet.turing.persistence.model.research.TurResearchStudy;
import com.viglet.turing.persistence.repository.research.TurResearchInsightSnapshotRepository;
import com.viglet.turing.persistence.repository.research.TurResearchInterviewRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyRepository;

/**
 * T729 / §XLVI.4 — Continuous Insight drift: capturing the deterministic
 * sufficiency snapshot after a run and reading back the ordered series with
 * per-point theme deltas.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurResearchDriftServiceTest {

    private static final String STUDY_ID = "s1";

    @Mock
    private TurResearchStudyRepository studyRepository;
    @Mock
    private TurResearchInterviewRepository interviewRepository;
    @Mock
    private TurResearchInsightSnapshotRepository snapshotRepository;
    @Mock
    private TurResearchInsightsService insightsService;

    private TurResearchDriftService service() {
        return new TurResearchDriftService(studyRepository, interviewRepository,
                snapshotRepository, insightsService);
    }

    private TurResearchStudy study() {
        TurResearchStudy study = new TurResearchStudy();
        study.setId(STUDY_ID);
        study.setSchedule(TurResearchSchedule.DAILY);
        return study;
    }

    private TurResearchInterview completed() {
        TurResearchInterview interview = new TurResearchInterview();
        interview.setStatus(TurResearchInterviewStatus.COMPLETED);
        return interview;
    }

    private static TurResearchInsightSnapshot snapshot(int themes, int adequateAtN) {
        TurResearchInsightSnapshot snapshot = new TurResearchInsightSnapshot();
        snapshot.setCapturedAt(Instant.EPOCH);
        snapshot.setInterviewCount(themes);
        snapshot.setTotalUniqueThemes(themes);
        snapshot.setAdequateAtN(adequateAtN);
        return snapshot;
    }

    @Test
    void snapshotPersistsDeterministicSaturationCounts() {
        when(studyRepository.findById(STUDY_ID)).thenReturn(Optional.of(study()));
        when(interviewRepository.findByStudy_IdOrderByPersonaIdAsc(STUDY_ID))
                .thenReturn(List.of(completed(), completed()));
        when(insightsService.saturation(STUDY_ID)).thenReturn(new TurResearchSaturationResultDto(
                true, null, 2, 7, true, 2, 0.15, 2, List.of()));

        service().snapshot(STUDY_ID);

        ArgumentCaptor<TurResearchInsightSnapshot> captor =
                ArgumentCaptor.forClass(TurResearchInsightSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        TurResearchInsightSnapshot saved = captor.getValue();
        assertThat(saved.getInterviewCount()).isEqualTo(2);
        assertThat(saved.getTotalUniqueThemes()).isEqualTo(7);
        assertThat(saved.getAdequateAtN()).isEqualTo(2);
        assertThat(saved.isSaturated()).isTrue();
        assertThat(saved.getCapturedAt()).isNotNull();
    }

    @Test
    void snapshotUnavailableSaturationStoresZeros() {
        when(studyRepository.findById(STUDY_ID)).thenReturn(Optional.of(study()));
        when(interviewRepository.findByStudy_IdOrderByPersonaIdAsc(STUDY_ID))
                .thenReturn(List.of(completed()));
        when(insightsService.saturation(STUDY_ID))
                .thenReturn(TurResearchSaturationResultDto.unavailable("too few", 1));

        service().snapshot(STUDY_ID);

        ArgumentCaptor<TurResearchInsightSnapshot> captor =
                ArgumentCaptor.forClass(TurResearchInsightSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalUniqueThemes()).isZero();
        assertThat(captor.getValue().getAdequateAtN()).isEqualTo(-1);
        assertThat(captor.getValue().isSaturated()).isFalse();
    }

    @Test
    void snapshotNoOpWhenStudyUnknown() {
        when(studyRepository.findById("missing")).thenReturn(Optional.empty());

        service().snapshot("missing");

        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void driftBuildsSeriesWithThemeDeltas() {
        when(studyRepository.findById(STUDY_ID)).thenReturn(Optional.of(study()));
        when(snapshotRepository.findByStudy_IdOrderByCapturedAtAsc(STUDY_ID))
                .thenReturn(List.of(snapshot(3, -1), snapshot(5, 4)));

        TurResearchDriftDto drift = service().drift(STUDY_ID);

        assertThat(drift.schedule()).isEqualTo("DAILY");
        assertThat(drift.snapshotCount()).isEqualTo(2);
        // First point's delta is its own theme count; the second is the increment.
        assertThat(drift.points().get(0).newThemesSincePrevious()).isEqualTo(3);
        assertThat(drift.points().get(1).newThemesSincePrevious()).isEqualTo(2);
    }

    @Test
    void driftEmptyWhenNoSnapshots() {
        when(studyRepository.findById(STUDY_ID)).thenReturn(Optional.of(study()));
        when(snapshotRepository.findByStudy_IdOrderByCapturedAtAsc(STUDY_ID)).thenReturn(List.of());

        TurResearchDriftDto drift = service().drift(STUDY_ID);

        assertThat(drift.snapshotCount()).isZero();
        assertThat(drift.points()).isEmpty();
        assertThat(drift.schedule()).isEqualTo("DAILY");
    }
}

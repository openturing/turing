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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchDriftDto;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchCellHistory;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchCellHistoryRepository;

/**
 * T826 / §LXI.2 (Block BN) — the drift report's "crossed below/above fit" flags
 * are decided by the <em>project's</em> good-fit bar, and the report says which
 * bar it used. The same pair of runs therefore alerts or stays quiet purely
 * because the operator chose a different standard, which is the whole point of
 * making the old hardcoded 75 configurable.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurPersonaMatchHistoryServiceTest {

    private static final String PROJECT = "p1";
    private static final Instant RUN_1 = Instant.parse("2026-07-01T03:30:00Z");
    private static final Instant RUN_2 = Instant.parse("2026-07-02T03:30:00Z");

    @Mock
    private TurPersonaMatchCellHistoryRepository historyRepository;

    @Mock
    private TurPersonaMatchThresholdService thresholdService;

    @InjectMocks
    private TurPersonaMatchHistoryService service;

    private static TurPersonaMatchCellHistory point(Instant at, double fit) {
        TurPersonaMatchCellHistory h = new TurPersonaMatchCellHistory();
        h.setProjectId(PROJECT);
        h.setSourceId("s1");
        h.setPersonaId("a1");
        h.setFitScore(fit);
        h.setReadabilityScore(fit);
        h.setContentHash("hash");
        h.setEvaluatedAt(at);
        return h;
    }

    /** One pair sliding 80 → 70 across two runs — below 75, still above 60. */
    private void twoRunsDroppingFrom80To70() {
        when(historyRepository.findByProjectIdOrderByEvaluatedAtAsc(PROJECT))
                .thenReturn(List.of(point(RUN_1, 80.0), point(RUN_2, 70.0)));
    }

    @Test
    void defaultBarFlagsThePairAsCrossedBelowFit() {
        twoRunsDroppingFrom80To70();
        when(thresholdService.forProject(PROJECT)).thenReturn(75.0);

        TurPersonaMatchDriftDto drift = service.drift(PROJECT);

        assertThat(drift.fitThreshold()).isEqualTo(75.0);
        assertThat(drift.entries()).hasSize(1);
        TurPersonaMatchDriftDto.Entry entry = drift.entries().getFirst();
        assertThat(entry.delta()).isEqualTo(-10.0);
        assertThat(entry.crossedBelowFit()).isTrue();
        assertThat(entry.crossedAboveFit()).isFalse();
        assertThat(drift.regressedCount()).isEqualTo(1);
    }

    @Test
    void aLowerProjectBarLeavesTheSameDropInsideTheGoodFitBand() {
        twoRunsDroppingFrom80To70();
        when(thresholdService.forProject(PROJECT)).thenReturn(60.0);

        TurPersonaMatchDriftDto drift = service.drift(PROJECT);

        assertThat(drift.fitThreshold()).isEqualTo(60.0);
        // Still a regression (-10) — but not an "it fell below our bar" alert.
        assertThat(drift.entries().getFirst().delta()).isEqualTo(-10.0);
        assertThat(drift.entries().getFirst().crossedBelowFit()).isFalse();
        assertThat(drift.regressedCount()).isEqualTo(1);
    }

    @Test
    void aStricterProjectBarKeepsAPairThatWasNeverGoodEnoughUnflagged() {
        twoRunsDroppingFrom80To70();
        when(thresholdService.forProject(PROJECT)).thenReturn(90.0);

        TurPersonaMatchDriftDto drift = service.drift(PROJECT);

        // 80 was already below 90, so nothing was "crossed" — it stayed below.
        assertThat(drift.entries().getFirst().crossedBelowFit()).isFalse();
        assertThat(drift.entries().getFirst().crossedAboveFit()).isFalse();
    }

    @Test
    void recoveryAcrossTheProjectBarIsFlaggedAsCrossedAbove() {
        when(historyRepository.findByProjectIdOrderByEvaluatedAtAsc(PROJECT))
                .thenReturn(List.of(point(RUN_1, 55.0), point(RUN_2, 65.0)));
        when(thresholdService.forProject(PROJECT)).thenReturn(60.0);

        TurPersonaMatchDriftDto drift = service.drift(PROJECT);

        assertThat(drift.entries().getFirst().crossedAboveFit()).isTrue();
        assertThat(drift.improvedCount()).isEqualTo(1);
    }

    @Test
    void aSingleRunYieldsAnEmptyReportThatStillCarriesTheBar() {
        when(historyRepository.findByProjectIdOrderByEvaluatedAtAsc(PROJECT))
                .thenReturn(List.of(point(RUN_1, 80.0)));
        when(thresholdService.forProject(PROJECT)).thenReturn(70.0);

        TurPersonaMatchDriftDto drift = service.drift(PROJECT);

        assertThat(drift.runCount()).isEqualTo(1);
        assertThat(drift.entries()).isEmpty();
        assertThat(drift.fitThreshold()).isEqualTo(70.0);
        assertThat(drift.currentRunAt()).isEqualTo(RUN_1);
    }
}

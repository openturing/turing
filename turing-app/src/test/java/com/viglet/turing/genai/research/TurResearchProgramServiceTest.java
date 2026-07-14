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
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.research.dto.TurResearchProgramRollupDto;
import com.viglet.turing.genai.research.dto.TurResearchReportDto;
import com.viglet.turing.genai.research.dto.TurResearchSaturationResultDto;
import com.viglet.turing.genai.research.dto.TurResearchStudyDto;
import com.viglet.turing.genai.research.dto.TurResearchThemeDto;

/**
 * Unit coverage for {@link TurResearchProgramService} (Block AW / §XLVI.5, T733) —
 * cross-study theme folding + shared-theme count, and the fail-open guards (empty
 * selection, all-unresolvable, un-run study skips report generation).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurResearchProgramServiceTest {

    @Mock
    private TurResearchStudyService studyService;

    @Mock
    private TurResearchInsightsService insightsService;

    @InjectMocks
    private TurResearchProgramService service;

    private static TurResearchStudyDto study(String id, String name, int personas, int interviews,
            Instant lastRunAt) {
        return new TurResearchStudyDto(id, name, "goal", null, null, true, "DYNAMIC_SCRIPT", null,
                List.of(), 6, null, null, null, null, null, null, "MANUAL", lastRunAt, personas,
                interviews, List.of(), List.of());
    }

    private static TurResearchReportDto report(String... titles) {
        List<TurResearchThemeDto> themes = Arrays.stream(titles)
                .map(t -> new TurResearchThemeDto(t, "summary", 2, List.of()))
                .toList();
        return new TurResearchReportDto(true, null, true, "exec", themes, List.of(), List.of(), null);
    }

    private static TurResearchSaturationResultDto saturation() {
        return new TurResearchSaturationResultDto(true, null, 3, 5, true, 3, 0.15, 2, List.of());
    }

    @Test
    void foldsThemesAcrossStudiesAndCountsShared() {
        when(studyService.get("s1")).thenReturn(Optional.of(study("s1", "Onboarding study", 3, 3, Instant.EPOCH)));
        when(studyService.get("s2")).thenReturn(Optional.of(study("s2", "Pricing study", 4, 4, Instant.EPOCH)));
        when(insightsService.report("s1", false)).thenReturn(report("Onboarding friction", "Pricing clarity"));
        when(insightsService.report("s2", false)).thenReturn(report("Onboarding friction", "Support gaps"));
        when(insightsService.saturation("s1")).thenReturn(saturation());
        when(insightsService.saturation("s2")).thenReturn(saturation());

        TurResearchProgramRollupDto rollup = service.rollup(List.of("s1", "s2"));

        assertThat(rollup.available()).isTrue();
        assertThat(rollup.studyCount()).isEqualTo(2);
        assertThat(rollup.totalParticipants()).isEqualTo(7);
        assertThat(rollup.totalInterviews()).isEqualTo(7);
        assertThat(rollup.sharedThemeCount()).isEqualTo(1);
        // "Onboarding friction" appears in both studies → ranked first with studyCount 2.
        assertThat(rollup.themes().get(0).title()).isEqualTo("Onboarding friction");
        assertThat(rollup.themes().get(0).studyCount()).isEqualTo(2);
        assertThat(rollup.totalDistinctThemes()).isEqualTo(3);
    }

    @Test
    void emptySelectionUnavailable() {
        TurResearchProgramRollupDto rollup = service.rollup(List.of());

        assertThat(rollup.available()).isFalse();
    }

    @Test
    void allUnresolvableUnavailable() {
        when(studyService.get("missing")).thenReturn(Optional.empty());

        TurResearchProgramRollupDto rollup = service.rollup(List.of("missing"));

        assertThat(rollup.available()).isFalse();
    }

    @Test
    void unrunStudyIncludedWithoutReport() {
        when(studyService.get("s3")).thenReturn(Optional.of(study("s3", "Fresh study", 2, 0, null)));

        TurResearchProgramRollupDto rollup = service.rollup(List.of("s3"));

        assertThat(rollup.available()).isTrue();
        assertThat(rollup.studies()).hasSize(1);
        assertThat(rollup.studies().get(0).reportAvailable()).isFalse();
        assertThat(rollup.themes()).isEmpty();
    }
}

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.research.dto.TurResearchProgramRollupDto;
import com.viglet.turing.genai.research.dto.TurResearchProgramStudyDto;
import com.viglet.turing.genai.research.dto.TurResearchProgramThemeDto;
import com.viglet.turing.genai.research.dto.TurResearchReportDto;
import com.viglet.turing.genai.research.dto.TurResearchSaturationResultDto;
import com.viglet.turing.genai.research.dto.TurResearchStudyDto;

/**
 * Multi-study program rollup (Block AW / §XLVI.5, T733 — the PRISMA view). Given a
 * set of study ids, rolls their per-study insights up into a program-level view:
 * totals, each study's sufficiency (T723 saturation), and themes aggregated across
 * studies so recurring pain points across different audiences surface. Reuses each
 * study's already-synthesized (cached) report and the deterministic saturation
 * counts — it adds <strong>no new inference</strong> (the T724 theme-graph
 * discipline). Fail-open per study: an unresolvable or un-run study is skipped, not
 * fatal.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurResearchProgramService {

    private final TurResearchStudyService studyService;
    private final TurResearchInsightsService insightsService;

    public TurResearchProgramService(TurResearchStudyService studyService,
            TurResearchInsightsService insightsService) {
        this.studyService = studyService;
        this.insightsService = insightsService;
    }

    /** Mutable accumulator for one aggregated theme (folded across studies). */
    private static final class ThemeAgg {
        private final String title;
        private final List<String> studyNames = new ArrayList<>();
        private int totalPrevalence;

        private ThemeAgg(String title) {
            this.title = title;
        }
    }

    public TurResearchProgramRollupDto rollup(List<String> studyIds) {
        if (studyIds == null || studyIds.isEmpty()) {
            return TurResearchProgramRollupDto.unavailable("Select at least one study.");
        }

        List<TurResearchProgramStudyDto> studies = new ArrayList<>();
        Map<String, ThemeAgg> themes = new LinkedHashMap<>();
        int totalParticipants = 0;
        int totalInterviews = 0;

        for (String id : studyIds) {
            TurResearchStudyDto study = studyService.get(id).orElse(null);
            if (study == null) {
                continue;
            }
            boolean run = study.lastRunAt() != null && study.interviewCount() > 0;
            TurResearchReportDto report = run ? insightsService.report(id, false) : null;
            TurResearchSaturationResultDto saturation = run ? insightsService.saturation(id) : null;

            boolean reportAvailable = report != null && report.available();
            int themeCount = reportAvailable ? report.themes().size() : 0;
            boolean saturated = saturation != null && saturation.available() && saturation.saturated();
            int adequateAtN = saturation != null && saturation.available() ? saturation.adequateAtN() : -1;

            studies.add(new TurResearchProgramStudyDto(study.id(), study.name(), study.protocol(),
                    study.personaCount(), study.interviewCount(), reportAvailable, themeCount,
                    saturated, adequateAtN, study.lastRunAt()));

            totalParticipants += study.personaCount();
            totalInterviews += study.interviewCount();

            if (reportAvailable) {
                foldThemes(themes, study.name(), report);
            }
        }

        if (studies.isEmpty()) {
            return TurResearchProgramRollupDto.unavailable("None of the selected studies could be resolved.");
        }

        List<TurResearchProgramThemeDto> aggregated = themes.values().stream()
                .map(agg -> new TurResearchProgramThemeDto(agg.title, agg.studyNames.size(),
                        agg.totalPrevalence, List.copyOf(agg.studyNames)))
                .sorted(Comparator.comparingInt(TurResearchProgramThemeDto::studyCount).reversed()
                        .thenComparing(Comparator.comparingInt(TurResearchProgramThemeDto::totalPrevalence).reversed())
                        .thenComparing(TurResearchProgramThemeDto::title))
                .toList();
        int sharedThemeCount = (int) aggregated.stream().filter(t -> t.studyCount() >= 2).count();

        return new TurResearchProgramRollupDto(true, null, studies.size(), totalParticipants,
                totalInterviews, aggregated.size(), sharedThemeCount, studies, aggregated);
    }

    /** Fold one study's themes into the program aggregate, keyed by normalized title. */
    private void foldThemes(Map<String, ThemeAgg> themes, String studyName, TurResearchReportDto report) {
        report.themes().forEach(theme -> {
            String title = StringUtils.trimToEmpty(theme.title());
            if (title.isEmpty()) {
                return;
            }
            String key = title.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            ThemeAgg agg = themes.computeIfAbsent(key, k -> new ThemeAgg(title));
            if (!agg.studyNames.contains(studyName)) {
                agg.studyNames.add(studyName);
            }
            agg.totalPrevalence += Math.max(0, theme.prevalence());
        });
    }
}

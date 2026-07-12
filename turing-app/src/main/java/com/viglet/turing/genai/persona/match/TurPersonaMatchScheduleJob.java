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

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchProject;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchSchedule;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchSource;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchProjectRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchSourceRepository;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Scheduled periodic re-analysis of Persona Match projects (Block AT / §XLIII,
 * T700) — the "keep personas and content tuned to each other over time" loop.
 * Enabled projects on a {@link TurPersonaMatchSchedule#DAILY DAILY} or
 * {@link TurPersonaMatchSchedule#WEEKLY WEEKLY} cadence are picked up, their
 * sources re-extracted (remote URLs may have drifted), and their matrix
 * recomputed — the content-hash guard in {@link TurPersonaMatchAnalysisService}
 * means only cells whose text actually changed pay for a fresh evaluation.
 *
 * <p>Opt-in at the data level: a project is only re-run once an operator sets
 * its schedule away from the default {@code MANUAL}. Cluster-wide-once via
 * ShedLock so multi-node deploys don't double-run. A global kill-switch
 * ({@code turing.persona-match.schedule.enabled}, default {@code true}) short-
 * circuits both cadences.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPersonaMatchScheduleJob {

    private final TurPersonaMatchProjectRepository projectRepository;
    private final TurPersonaMatchSourceRepository sourceRepository;
    private final TurPersonaMatchSourceService sourceService;
    private final TurPersonaMatchAnalysisService analysisService;

    @Value("${turing.persona-match.schedule.enabled:true}")
    private boolean enabled;

    public TurPersonaMatchScheduleJob(TurPersonaMatchProjectRepository projectRepository,
            TurPersonaMatchSourceRepository sourceRepository,
            TurPersonaMatchSourceService sourceService,
            TurPersonaMatchAnalysisService analysisService) {
        this.projectRepository = projectRepository;
        this.sourceRepository = sourceRepository;
        this.sourceService = sourceService;
        this.analysisService = analysisService;
    }

    @Scheduled(cron = "${turing.persona-match.schedule.daily-cron:0 30 3 * * *}")
    @SchedulerLock(name = "personaMatchDaily", lockAtMostFor = "PT2H", lockAtLeastFor = "PT1S")
    public void runDaily() {
        runCadence(TurPersonaMatchSchedule.DAILY);
    }

    @Scheduled(cron = "${turing.persona-match.schedule.weekly-cron:0 0 4 * * SUN}")
    @SchedulerLock(name = "personaMatchWeekly", lockAtMostFor = "PT4H", lockAtLeastFor = "PT1S")
    public void runWeekly() {
        runCadence(TurPersonaMatchSchedule.WEEKLY);
    }

    private void runCadence(TurPersonaMatchSchedule schedule) {
        if (!enabled) {
            return;
        }
        List<TurPersonaMatchProject> projects =
                projectRepository.findByEnabledTrueAndSchedule(schedule);
        if (projects.isEmpty()) {
            return;
        }
        log.info("[PersonaMatch] {} scheduled re-analysis of {} project(s)", schedule,
                projects.size());
        for (TurPersonaMatchProject project : projects) {
            try {
                reExtractSources(project.getId());
                analysisService.run(project.getId(), false);
            } catch (RuntimeException e) {
                log.warn("[PersonaMatch] scheduled re-analysis failed for project={}: {}",
                        project.getId(), e.getMessage());
            }
        }
    }

    /** Re-extract every re-fetchable source so drifted remote content is picked up. */
    private void reExtractSources(String projectId) {
        for (TurPersonaMatchSource source
                : sourceRepository.findByProject_IdOrderBySourceNameAsc(projectId)) {
            sourceService.extract(source);
            sourceRepository.save(source);
        }
    }
}

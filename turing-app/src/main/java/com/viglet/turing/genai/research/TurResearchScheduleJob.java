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

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.research.TurResearchSchedule;
import com.viglet.turing.persistence.model.research.TurResearchStudy;
import com.viglet.turing.persistence.repository.research.TurResearchStudyRepository;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Scheduled periodic re-run of Synthetic User Research studies (Block AW /
 * §XLVI.4, T729 — Continuous Insight) — the "keep validating over time" loop.
 * Enabled studies on a {@link TurResearchSchedule#DAILY DAILY} or
 * {@link TurResearchSchedule#WEEKLY WEEKLY} cadence are re-run through the shared
 * {@link TurResearchRunnerService}; the runner's content-hash guard means an
 * unchanged study reuses its transcripts, and each run captures an insight-drift
 * snapshot ({@link TurResearchDriftService}) so theme/saturation movement is
 * tracked over time.
 *
 * <p>Opt-in at the data level: a study is only re-run once an operator sets its
 * schedule away from the default {@code MANUAL}. Cluster-wide-once via ShedLock so
 * multi-node deploys don't double-run. A global kill-switch
 * ({@code turing.research.schedule.enabled}, default {@code true}) short-circuits
 * both cadences. Mirrors {@code TurPersonaMatchScheduleJob} (Block AT).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurResearchScheduleJob {

    private final TurResearchStudyRepository studyRepository;
    private final TurResearchRunnerService runnerService;

    @Value("${turing.research.schedule.enabled:true}")
    private boolean enabled;

    public TurResearchScheduleJob(TurResearchStudyRepository studyRepository,
            TurResearchRunnerService runnerService) {
        this.studyRepository = studyRepository;
        this.runnerService = runnerService;
    }

    @Scheduled(cron = "${turing.research.schedule.daily-cron:0 45 3 * * *}")
    @SchedulerLock(name = "researchStudyDaily", lockAtMostFor = "PT2H", lockAtLeastFor = "PT1S")
    public void runDaily() {
        runCadence(TurResearchSchedule.DAILY);
    }

    @Scheduled(cron = "${turing.research.schedule.weekly-cron:0 15 4 * * SUN}")
    @SchedulerLock(name = "researchStudyWeekly", lockAtMostFor = "PT4H", lockAtLeastFor = "PT1S")
    public void runWeekly() {
        runCadence(TurResearchSchedule.WEEKLY);
    }

    private void runCadence(TurResearchSchedule schedule) {
        if (!enabled) {
            return;
        }
        List<TurResearchStudy> studies = studyRepository.findByEnabledTrueAndSchedule(schedule);
        if (studies.isEmpty()) {
            return;
        }
        log.info("[Research] {} scheduled re-run of {} study(ies)", schedule, studies.size());
        for (TurResearchStudy study : studies) {
            try {
                runnerService.run(study.getId(), false);
            } catch (RuntimeException e) {
                log.warn("[Research] scheduled re-run failed for study={}: {}",
                        study.getId(), e.getMessage());
            }
        }
    }
}

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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.genai.research.dto.TurResearchDriftDto;
import com.viglet.turing.genai.research.dto.TurResearchDriftPointDto;
import com.viglet.turing.genai.research.dto.TurResearchSaturationResultDto;
import com.viglet.turing.persistence.model.research.TurResearchInsightSnapshot;
import com.viglet.turing.persistence.model.research.TurResearchInterview;
import com.viglet.turing.persistence.model.research.TurResearchInterviewStatus;
import com.viglet.turing.persistence.model.research.TurResearchStudy;
import com.viglet.turing.persistence.repository.research.TurResearchInsightSnapshotRepository;
import com.viglet.turing.persistence.repository.research.TurResearchInterviewRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T729 / §XLVI.4 — Continuous Insight drift. Captures a deterministic sufficiency
 * snapshot after every study run and exposes the ordered series, so re-running a
 * study on a cadence turns one-shot research into continuous validation: you can
 * see whether theme coverage and saturation are still moving or have stabilized.
 *
 * <p>The snapshot reuses the T723 saturation counts (theme coverage + "adequate at
 * N") and the completed-interview count — all <strong>LLM-free</strong>, so
 * capturing drift costs nothing and reproduces in CI (T385/T466 discipline). It is
 * intentionally cheap and additive; richer drift (per-theme deltas, T87 sentiment
 * trajectory) can layer on later without changing this contract.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurResearchDriftService {

    private final TurResearchStudyRepository studyRepository;
    private final TurResearchInterviewRepository interviewRepository;
    private final TurResearchInsightSnapshotRepository snapshotRepository;
    private final TurResearchInsightsService insightsService;

    public TurResearchDriftService(TurResearchStudyRepository studyRepository,
            TurResearchInterviewRepository interviewRepository,
            TurResearchInsightSnapshotRepository snapshotRepository,
            TurResearchInsightsService insightsService) {
        this.studyRepository = studyRepository;
        this.interviewRepository = interviewRepository;
        this.snapshotRepository = snapshotRepository;
        this.insightsService = insightsService;
    }

    /**
     * Capture one deterministic sufficiency snapshot for the study (a no-op when
     * the study is unknown). Called after every run — never let a snapshot failure
     * break the run; callers should still guard defensively.
     */
    @Transactional
    public void snapshot(String studyId) {
        TurResearchStudy study = studyRepository.findById(studyId).orElse(null);
        if (study == null) {
            return;
        }
        TurResearchSaturationResultDto saturation = insightsService.saturation(studyId);
        TurResearchInsightSnapshot snapshot = new TurResearchInsightSnapshot();
        snapshot.setStudy(study);
        snapshot.setCapturedAt(Instant.now());
        snapshot.setInterviewCount(countCompleted(studyId));
        snapshot.setTotalUniqueThemes(saturation.available() ? saturation.totalUniqueThemes() : 0);
        snapshot.setAdequateAtN(saturation.available() ? saturation.adequateAtN() : -1);
        snapshot.setSaturated(saturation.available() && saturation.saturated());
        snapshotRepository.save(snapshot);
    }

    /** The ordered insight-drift series for a study (empty when none captured yet). */
    @Transactional(readOnly = true)
    public TurResearchDriftDto drift(String studyId) {
        TurResearchStudy study = studyRepository.findById(studyId).orElse(null);
        String schedule = study == null ? null : study.getSchedule().name();
        List<TurResearchInsightSnapshot> snapshots =
                snapshotRepository.findByStudy_IdOrderByCapturedAtAsc(studyId);
        if (snapshots.isEmpty()) {
            return TurResearchDriftDto.empty(schedule);
        }
        List<TurResearchDriftPointDto> points = new ArrayList<>(snapshots.size());
        int previousThemes = 0;
        boolean first = true;
        for (TurResearchInsightSnapshot snapshot : snapshots) {
            int delta = first ? snapshot.getTotalUniqueThemes()
                    : snapshot.getTotalUniqueThemes() - previousThemes;
            points.add(new TurResearchDriftPointDto(snapshot.getCapturedAt(),
                    snapshot.getInterviewCount(), snapshot.getTotalUniqueThemes(),
                    snapshot.getAdequateAtN(), snapshot.isSaturated(), delta));
            previousThemes = snapshot.getTotalUniqueThemes();
            first = false;
        }
        return new TurResearchDriftDto(schedule, points.size(), points);
    }

    private int countCompleted(String studyId) {
        int count = 0;
        for (TurResearchInterview interview
                : interviewRepository.findByStudy_IdOrderByPersonaIdAsc(studyId)) {
            if (interview.getStatus() == TurResearchInterviewStatus.COMPLETED) {
                count++;
            }
        }
        return count;
    }
}

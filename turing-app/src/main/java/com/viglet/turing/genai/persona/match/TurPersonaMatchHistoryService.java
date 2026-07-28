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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchDriftDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchTrendDto;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchCellHistory;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchCellHistoryRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Per-cell fit history and drift detection for Persona Match projects
 * (Block AT / §XLIII, T704) — the memory the in-place cell overwrite does not
 * keep. The T700 scheduled runner recomputes a project on a DAILY/WEEKLY cadence
 * and each run replaces the cell, so without a series there is no way to say how
 * a {@code (content, persona)} pair's fit <em>moved</em>. This service appends one
 * point per cell per run (including cells the content-hash guard reused, so a
 * run's average covers the whole matrix), trims each series to a bounded window,
 * and derives the two read models the studio needs: the trend chart and the
 * "what regressed since last run" drift report.
 *
 * <p>Nothing here judges content — it only reads the scores the Block AA
 * evaluator already produced, so no LLM is involved and the drift report works
 * on a readability-only project too.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPersonaMatchHistoryService {

    /**
     * Fit at/above this is a "good fit" (mirrors the studio's emerald band), so
     * crossing it downwards is what makes drift worth surfacing.
     */
    public static final double GOOD_FIT_THRESHOLD = 75.0;

    private final TurPersonaMatchCellHistoryRepository historyRepository;

    /** Points kept per cell; older points are trimmed after each run. */
    @Value("${turing.persona-match.history.max-points:24}")
    private int maxPoints = 24;

    public TurPersonaMatchHistoryService(
            TurPersonaMatchCellHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    // ---- write -------------------------------------------------------------

    /**
     * Append one point per cell of a completed run and trim each affected series
     * to {@code maxPoints}. Failures are swallowed (logged): a history write must
     * never fail an analysis run that already produced its matrix.
     *
     * @param runAt the run instant — shared by every point so runs group cleanly
     */
    @Transactional
    public void record(String projectId, List<RecordedCell> cells, Instant runAt) {
        if (cells == null || cells.isEmpty()) {
            return;
        }
        try {
            List<TurPersonaMatchCellHistory> rows = new ArrayList<>(cells.size());
            for (RecordedCell c : cells) {
                TurPersonaMatchCellHistory row = new TurPersonaMatchCellHistory();
                row.setProjectId(projectId);
                row.setSourceId(c.sourceId());
                row.setPersonaId(c.personaId());
                row.setFitScore(c.fitScore());
                row.setReadabilityScore(c.readabilityScore());
                row.setLlmUsed(c.llmUsed());
                row.setReused(c.reused());
                row.setContentHash(c.contentHash());
                row.setEvaluatedAt(runAt);
                rows.add(row);
            }
            historyRepository.saveAll(rows);
            trim(projectId, cells);
        } catch (RuntimeException e) {
            log.warn("[PersonaMatch] could not record fit history for project={}: {}",
                    projectId, e.getMessage());
        }
    }

    private void trim(String projectId, List<RecordedCell> cells) {
        int cap = Math.max(2, maxPoints);
        for (RecordedCell c : cells) {
            List<TurPersonaMatchCellHistory> series = historyRepository
                    .findByProjectIdAndSourceIdAndPersonaIdOrderByEvaluatedAtAsc(projectId,
                            c.sourceId(), c.personaId());
            if (series.size() > cap) {
                historyRepository.deleteAll(series.subList(0, series.size() - cap));
            }
        }
    }

    // ---- prune -------------------------------------------------------------

    @Transactional
    public void deleteByProject(String projectId) {
        historyRepository.deleteByProjectId(projectId);
    }

    @Transactional
    public void deleteBySource(String projectId, String sourceId) {
        historyRepository.deleteByProjectIdAndSourceId(projectId, sourceId);
    }

    @Transactional
    public void deleteByPersona(String projectId, String personaId) {
        historyRepository.deleteByProjectIdAndPersonaId(projectId, personaId);
    }

    /** Prune exactly one {@code (content, persona)} pair's series. */
    @Transactional
    public void deleteByCell(String projectId, String sourceId, String personaId) {
        historyRepository.deleteByProjectIdAndSourceIdAndPersonaId(projectId, sourceId,
                personaId);
    }

    // ---- read: trend -------------------------------------------------------

    /** The project's per-cell series plus the run-level average series. */
    public TurPersonaMatchTrendDto trend(String projectId) {
        List<TurPersonaMatchCellHistory> all =
                historyRepository.findByProjectIdOrderByEvaluatedAtAsc(projectId);
        if (all.isEmpty()) {
            return new TurPersonaMatchTrendDto(projectId, 0, List.of(), List.of());
        }

        Map<Instant, List<TurPersonaMatchCellHistory>> byRun = new LinkedHashMap<>();
        Map<String, List<TurPersonaMatchCellHistory>> byCell = new LinkedHashMap<>();
        for (TurPersonaMatchCellHistory h : all) {
            byRun.computeIfAbsent(h.getEvaluatedAt(), k -> new ArrayList<>()).add(h);
            byCell.computeIfAbsent(key(h.getSourceId(), h.getPersonaId()),
                    k -> new ArrayList<>()).add(h);
        }

        List<TurPersonaMatchTrendDto.Point> average = byRun.entrySet().stream()
                .map(e -> new TurPersonaMatchTrendDto.Point(e.getKey(),
                        avg(e.getValue(), TurPersonaMatchCellHistory::getFitScore),
                        avg(e.getValue(), TurPersonaMatchCellHistory::getReadabilityScore),
                        e.getValue().stream().anyMatch(TurPersonaMatchCellHistory::isLlmUsed),
                        e.getValue().stream().allMatch(TurPersonaMatchCellHistory::isReused)))
                .toList();

        List<TurPersonaMatchTrendDto.CellSeries> cells = byCell.values().stream()
                .map(series -> new TurPersonaMatchTrendDto.CellSeries(
                        series.getFirst().getSourceId(), series.getFirst().getPersonaId(),
                        series.stream().map(this::toPoint).toList()))
                .toList();

        return new TurPersonaMatchTrendDto(projectId, byRun.size(), average, cells);
    }

    private TurPersonaMatchTrendDto.Point toPoint(TurPersonaMatchCellHistory h) {
        return new TurPersonaMatchTrendDto.Point(h.getEvaluatedAt(), h.getFitScore(),
                h.getReadabilityScore(), h.isLlmUsed(), h.isReused());
    }

    // ---- read: drift -------------------------------------------------------

    /**
     * How every cell moved between the project's last two runs, sharpest
     * regression first. A project with fewer than two runs on record yields an
     * empty report rather than an error — drift is simply not knowable yet.
     */
    public TurPersonaMatchDriftDto drift(String projectId) {
        List<TurPersonaMatchCellHistory> all =
                historyRepository.findByProjectIdOrderByEvaluatedAtAsc(projectId);
        List<Instant> runs = all.stream().map(TurPersonaMatchCellHistory::getEvaluatedAt)
                .distinct().sorted().toList();
        if (runs.size() < 2) {
            return new TurPersonaMatchDriftDto(projectId, runs.size(), null,
                    runs.isEmpty() ? null : runs.getLast(), 0, 0, 0, 0, List.of());
        }
        Instant previousRun = runs.get(runs.size() - 2);
        Instant currentRun = runs.getLast();

        Map<String, TurPersonaMatchCellHistory> previous = indexByCell(all, previousRun);
        Map<String, TurPersonaMatchCellHistory> current = indexByCell(all, currentRun);

        List<TurPersonaMatchDriftDto.Entry> entries = new ArrayList<>();
        for (Map.Entry<String, TurPersonaMatchCellHistory> e : current.entrySet()) {
            TurPersonaMatchCellHistory before = previous.get(e.getKey());
            if (before == null) {
                continue; // a cell added after the previous run has nothing to drift from
            }
            entries.add(toEntry(before, e.getValue()));
        }
        entries.sort(Comparator.comparingDouble(TurPersonaMatchDriftDto.Entry::delta)
                .thenComparing(TurPersonaMatchDriftDto.Entry::sourceId));

        int regressed = (int) entries.stream().filter(en -> en.delta() < 0).count();
        int improved = (int) entries.stream().filter(en -> en.delta() > 0).count();

        return new TurPersonaMatchDriftDto(projectId, runs.size(), previousRun, currentRun,
                avg(previous.values(), TurPersonaMatchCellHistory::getFitScore),
                avg(current.values(), TurPersonaMatchCellHistory::getFitScore),
                regressed, improved, entries);
    }

    private TurPersonaMatchDriftDto.Entry toEntry(TurPersonaMatchCellHistory before,
            TurPersonaMatchCellHistory after) {
        double delta = round(after.getFitScore() - before.getFitScore());
        boolean crossedBelow = before.getFitScore() >= GOOD_FIT_THRESHOLD
                && after.getFitScore() < GOOD_FIT_THRESHOLD;
        boolean crossedAbove = before.getFitScore() < GOOD_FIT_THRESHOLD
                && after.getFitScore() >= GOOD_FIT_THRESHOLD;
        boolean contentChanged =
                !Objects.equals(before.getContentHash(), after.getContentHash());
        return new TurPersonaMatchDriftDto.Entry(after.getSourceId(), after.getPersonaId(),
                round(before.getFitScore()), round(after.getFitScore()), delta,
                crossedBelow, crossedAbove, contentChanged);
    }

    private Map<String, TurPersonaMatchCellHistory> indexByCell(
            List<TurPersonaMatchCellHistory> all, Instant run) {
        Map<String, TurPersonaMatchCellHistory> map = new LinkedHashMap<>();
        for (TurPersonaMatchCellHistory h : all) {
            if (run.equals(h.getEvaluatedAt())) {
                map.put(key(h.getSourceId(), h.getPersonaId()), h);
            }
        }
        return map;
    }

    // ---- helpers -----------------------------------------------------------

    private double avg(java.util.Collection<TurPersonaMatchCellHistory> points,
            java.util.function.ToDoubleFunction<TurPersonaMatchCellHistory> field) {
        if (points.isEmpty()) {
            return 0;
        }
        return round(points.stream().mapToDouble(field).average().orElse(0));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String key(String sourceId, String personaId) {
        return sourceId + "::" + personaId;
    }

    /**
     * What the analysis runner hands over for one cell of a finished run — the
     * scores as persisted, plus whether the run reused the previous verdict.
     */
    public record RecordedCell(String sourceId, String personaId, double fitScore,
            double readabilityScore, boolean llmUsed, boolean reused, String contentHash) {
    }
}

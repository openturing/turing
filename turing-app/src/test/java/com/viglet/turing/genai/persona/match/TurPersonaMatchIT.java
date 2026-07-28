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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchPersonaRefDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchProjectDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchAdjustmentDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchDriftDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchReportDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchTrendDto;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.model.persona.TurPersonaSourceStatus;
import com.viglet.turing.persistence.model.persona.TurPersonaSourceType;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchCell;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchProject;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchSource;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchCellHistoryRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchCellRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchProjectRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchSourceRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * Block AT / §XLIII — end-to-end validation of the Persona Match backend
 * (T696–T700) against the full Spring context: entity mapping + Liquibase (new
 * changelog boots), project CRUD, persona set, project-scoped source, the N×N
 * runner (LLM-free — it degrades to the deterministic readability score when no
 * default LLM is configured, so no network is touched), the content-hash re-run
 * skip, and both report lenses. T704 adds a second pass over the tuning loop:
 * the per-cell history series, drift between two runs, and the (LLM-free,
 * deterministic) adjustment suggestions.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurPersonaMatchIT extends AbstractTuringSpringIT {

    @Autowired
    private TurPersonaMatchService service;
    @Autowired
    private TurPersonaMatchAnalysisService analysisService;
    @Autowired
    private TurPersonaRepository personaRepository;
    @Autowired
    private TurPersonaMatchProjectRepository projectRepository;
    @Autowired
    private TurPersonaMatchSourceRepository sourceRepository;
    @Autowired
    private TurPersonaMatchCellRepository cellRepository;
    @Autowired
    private TurPersonaMatchHistoryService historyService;
    @Autowired
    private TurPersonaMatchAdjustmentService adjustmentService;
    @Autowired
    private TurPersonaMatchCellHistoryRepository historyRepository;

    @Test
    void fullProjectLifecycle() {
        // --- an audience persona to evaluate against ---
        TurPersona persona = new TurPersona();
        persona.setName("Aluno iniciante (match-it)");
        persona.setDescription("Leitor sem familiaridade com jargão técnico.");
        persona.setPersonaKind(TurPersonaKind.AUDIENCE);
        persona = personaRepository.save(persona);
        String personaId = persona.getId();

        // --- create a project ---
        TurPersonaMatchProjectDto created = service.create(new TurPersonaMatchProjectDto(
                null, "Match IT project", "desc", true, "WEEKLY", null, null, null, 0,
                null, null, false, null, null, 0, 0, null, null));
        String projectId = created.id();
        assertNotNull(projectId);
        assertEquals("WEEKLY", created.schedule());

        // --- attach the persona ---
        List<TurPersonaMatchPersonaRefDto> personas =
                service.setPersonas(projectId, List.of(personaId));
        assertEquals(1, personas.size());
        assertEquals(personaId, personas.get(0).id());

        // --- a pre-extracted content (skip the network fetch) ---
        String text = "Este é um texto simples e curto. Ele usa frases claras. "
                + "O objetivo é ser fácil de ler para qualquer pessoa.";
        TurPersonaMatchProject projectRef = projectRepository.findById(projectId).orElseThrow();
        TurPersonaMatchSource source = new TurPersonaMatchSource();
        source.setProject(projectRef);
        source.setType(TurPersonaSourceType.URL);
        source.setSourceName("Texto simples");
        source.setRef("https://example.test/texto");
        source.setCachedText(text);
        source.setContentHash(TurPersonaMatchSourceService.contentHash(text));
        source.setExtractionStatus(TurPersonaSourceStatus.EXTRACTED);
        source = sourceRepository.save(source);
        String sourceId = source.getId();

        // --- run the N×N analysis (1×1) ---
        TurPersonaMatchMatrixDto matrix = analysisService.run(projectId, false);
        assertEquals(1, matrix.cells().size());
        TurPersonaMatchCellDto cell = matrix.cells().get(0);
        assertEquals(sourceId, cell.sourceId());
        assertEquals(personaId, cell.personaId());
        assertFalse(cell.llmUsed(), "no default LLM configured → readability-only");
        assertTrue(cell.fitScore() >= 0 && cell.fitScore() <= 100);

        // persisted + lastRunAt stamped
        assertEquals(1, cellRepository.findByProjectId(projectId).size());
        assertNotNull(projectRepository.findById(projectId).orElseThrow().getLastRunAt());

        // --- re-run: unchanged content hash → cell reused, still exactly one ---
        TurPersonaMatchMatrixDto rerun = analysisService.run(projectId, false);
        assertEquals(1, rerun.cells().size());
        assertEquals(1, cellRepository.findByProjectId(projectId).size());

        // --- report lenses ---
        TurPersonaMatchReportDto report = service.report(projectId);
        assertEquals(1, report.byContent().size());
        assertEquals(sourceId, report.byContent().get(0).sourceId());
        assertEquals(1, report.byPersona().size());
        assertEquals(personaId, report.byPersona().get(0).personaId());

        // --- delete cascades cells away ---
        assertTrue(service.delete(projectId));
        assertTrue(cellRepository.findByProjectId(projectId).isEmpty());
        assertTrue(projectRepository.findById(projectId).isEmpty());
    }

    /**
     * T704 — the tuning loop. Two runs over a content whose text changes in between
     * must leave a two-point history series, a drift entry that remembers the score
     * the cell used to hold, and a non-empty adjustment report (deterministic, since
     * no default LLM is configured here).
     */
    @Test
    void fitHistoryDriftAndAdjustments() {
        TurPersona persona = new TurPersona();
        persona.setName("Leitor tecnico (drift-it)");
        persona.setDescription("Le documentacao tecnica com frequencia.");
        persona.setPersonaKind(TurPersonaKind.AUDIENCE);
        persona = personaRepository.save(persona);
        String personaId = persona.getId();

        TurPersonaMatchProjectDto created = service.create(new TurPersonaMatchProjectDto(
                null, "Drift IT project", "desc", true, "DAILY", null, null, null, 0,
                null, null, false, null, null, 0, 0, null, null));
        String projectId = created.id();
        service.setPersonas(projectId, List.of(personaId));

        String simple = "Texto curto. Frases claras. Facil de ler.";
        TurPersonaMatchSource source = saveSource(projectId, simple);
        String sourceId = source.getId();

        // --- run 1: first point on record, nothing to drift from yet ---
        analysisService.run(projectId, false);
        assertEquals(1, historyRepository.findByProjectIdOrderByEvaluatedAtAsc(projectId).size());
        TurPersonaMatchDriftDto firstDrift = historyService.drift(projectId);
        assertEquals(1, firstDrift.runCount());
        assertTrue(firstDrift.entries().isEmpty(), "one run cannot show drift");
        double firstFit = cellRepository
                .findByProjectIdAndSourceIdAndPersonaId(projectId, sourceId, personaId)
                .orElseThrow().getFitScore();

        // --- the content drifts: a denser rewrite, so the hash guard recomputes ---
        String dense = "A implementacao subjacente pressupoe familiaridade previa com "
                + "paradigmas de instrumentacao distribuida, cuja compreensao demanda "
                + "conhecimento consolidado de topologias assincronas e de suas respectivas "
                + "garantias transacionais em cenarios de particionamento arbitrario.";
        source.setCachedText(dense);
        source.setContentHash(TurPersonaMatchSourceService.contentHash(dense));
        sourceRepository.save(source);

        // --- run 2: cell recomputed, second point appended ---
        analysisService.run(projectId, false);
        assertEquals(2, historyRepository.findByProjectIdOrderByEvaluatedAtAsc(projectId).size());

        TurPersonaMatchCell cell = cellRepository
                .findByProjectIdAndSourceIdAndPersonaId(projectId, sourceId, personaId)
                .orElseThrow();
        assertNotNull(cell.getPreviousFitScore(), "the replaced score is carried forward");
        assertEquals(firstFit, cell.getPreviousFitScore(), 0.001);
        assertNotNull(cell.getPreviousEvaluatedAt());

        // --- drift now knows both runs ---
        TurPersonaMatchDriftDto drift = historyService.drift(projectId);
        assertEquals(2, drift.runCount());
        assertNotNull(drift.previousRunAt());
        assertNotNull(drift.currentRunAt());
        assertEquals(1, drift.entries().size());
        TurPersonaMatchDriftDto.Entry entry = drift.entries().get(0);
        assertEquals(sourceId, entry.sourceId());
        assertEquals(personaId, entry.personaId());
        assertEquals(firstFit, entry.previousFit(), 0.01);
        assertEquals(cell.getFitScore(), entry.currentFit(), 0.01);
        assertEquals(entry.currentFit() - entry.previousFit(), entry.delta(), 0.02);
        assertTrue(entry.contentChanged(), "the source text changed between runs");

        // --- trend: one series of two points + a two-point average ---
        TurPersonaMatchTrendDto trend = historyService.trend(projectId);
        assertEquals(2, trend.runCount());
        assertEquals(2, trend.average().size());
        assertEquals(1, trend.cells().size());
        assertEquals(2, trend.cells().get(0).points().size());

        // --- adjustments: no default LLM here, so the deterministic report answers ---
        TurPersonaMatchAdjustmentDto adjustments = adjustmentService.suggest(projectId, false);
        assertFalse(adjustments.llmUsed(), "no default LLM configured -> deterministic report");
        assertFalse(adjustments.items().isEmpty(), "the fallback is never empty");
        assertTrue(adjustments.items().stream().anyMatch(i ->
                i.target() == TurPersonaMatchAdjustmentDto.Target.CONTENT
                        && sourceId.equals(i.targetId())));
        assertTrue(adjustments.items().stream().anyMatch(i ->
                i.target() == TurPersonaMatchAdjustmentDto.Target.PERSONA
                        && personaId.equals(i.targetId())));
        adjustments.items().forEach(i ->
                assertFalse(i.actions().isEmpty(), "every item carries at least one action"));

        // --- deleting the project prunes the series too ---
        assertTrue(service.delete(projectId));
        assertTrue(historyRepository.findByProjectIdOrderByEvaluatedAtAsc(projectId).isEmpty());
    }

    /** A pre-extracted project content, so no network fetch is needed. */
    private TurPersonaMatchSource saveSource(String projectId, String text) {
        TurPersonaMatchProject projectRef = projectRepository.findById(projectId).orElseThrow();
        TurPersonaMatchSource source = new TurPersonaMatchSource();
        source.setProject(projectRef);
        source.setType(TurPersonaSourceType.URL);
        source.setSourceName("Conteudo drift");
        source.setRef("https://example.test/drift");
        source.setCachedText(text);
        source.setContentHash(TurPersonaMatchSourceService.contentHash(text));
        source.setExtractionStatus(TurPersonaSourceStatus.EXTRACTED);
        return sourceRepository.save(source);
    }
}

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
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchReportDto;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.model.persona.TurPersonaSourceStatus;
import com.viglet.turing.persistence.model.persona.TurPersonaSourceType;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchProject;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchSource;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
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
 * skip, and both report lenses.
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
                null, "Match IT project", "desc", true, "WEEKLY", null, null, null, 0, 0,
                null, null));
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
}

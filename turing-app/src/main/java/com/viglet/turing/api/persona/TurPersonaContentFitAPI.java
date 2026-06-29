/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.persona;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.persona.fit.TurContentFitResult;
import com.viglet.turing.genai.persona.fit.TurPersonaContentFitEvaluator;
import com.viglet.turing.genai.persona.fit.TurPersonaFitReport;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaSource;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaSourceRepository;
import com.viglet.turing.system.TurLlmSummaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Content-fit report endpoint (Block AA / §XXVI.5). Evaluates one source or the
 * whole notebook of an audience persona and returns an aggregated report with a
 * notebook-wide average plus per-source verdicts. Mirrors the
 * {@code TurSNSiteSummaryAPI} regenerate-aware shape.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/persona/{personaId}/content-fit")
@Tag(name = "AI Persona Content Fit", description = "Audience content-fit report API")
public class TurPersonaContentFitAPI {

    private final TurPersonaRepository personaRepository;
    private final TurPersonaSourceRepository sourceRepository;
    private final TurPersonaContentFitEvaluator evaluator;
    private final TurLlmSummaryService llmSummaryService;

    public TurPersonaContentFitAPI(TurPersonaRepository personaRepository,
            TurPersonaSourceRepository sourceRepository,
            TurPersonaContentFitEvaluator evaluator,
            TurLlmSummaryService llmSummaryService) {
        this.personaRepository = personaRepository;
        this.sourceRepository = sourceRepository;
        this.evaluator = evaluator;
        this.llmSummaryService = llmSummaryService;
    }

    @Operation(summary = "Evaluate content fit for one source or the whole notebook")
    @PostMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW", "AI_AGENT_EDIT" })
    public TurPersonaFitReport evaluate(@PathVariable String personaId,
            @RequestParam(required = false) String sourceId,
            @RequestParam(defaultValue = "false") boolean regenerate) {
        TurPersona persona = personaRepository.findById(personaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Persona not found"));

        List<TurPersonaSource> sources =
                sourceRepository.findByTurPersona_IdOrderBySourceNameAsc(personaId);
        if (sourceId != null && !sourceId.isBlank()) {
            sources = sources.stream()
                    .filter(s -> sourceId.equals(s.getId()))
                    .toList();
        }

        List<TurContentFitResult> results = new ArrayList<>();
        double sum = 0;
        int evaluated = 0;
        boolean canRegenerate = false;
        for (TurPersonaSource source : sources) {
            TurContentFitResult result = evaluator.evaluate(persona, source, regenerate);
            results.add(result);
            canRegenerate = canRegenerate || result.canRegenerate();
            // Only sources that actually contributed a usable score count toward
            // the notebook average (skip the not-yet-extracted ones).
            if (source.getCachedText() != null && !source.getCachedText().isBlank()) {
                sum += result.fitScore();
                evaluated++;
            }
        }

        double overall = evaluated == 0 ? 0.0
                : Math.round((sum / evaluated) * 100.0) / 100.0;

        return new TurPersonaFitReport(personaId, overall, evaluated, sources.size(),
                llmSummaryService.isAvailable(), canRegenerate, results);
    }
}

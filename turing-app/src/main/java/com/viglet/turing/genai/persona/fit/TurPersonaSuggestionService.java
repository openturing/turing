/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.fit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.system.TurLlmSummaryService;

/**
 * Auto-suggest the best-fitting audience persona for a piece of content
 * (Block AA / §XXVI.8). Batches the T467 {@link TurPersonaContentFitEvaluator}
 * across a set of audience personas and ranks them by fit, giving editorial
 * routing and Block Z A/B persona selection a content-side companion to T69:
 * not "which persona variation was preferred" but "which persona is this
 * content even <em>for</em>".
 *
 * <p>Candidate personas are the {@code AUDIENCE}/{@code BOTH} personas (those
 * carrying the audience facet the evaluator reads); {@code SPEAKER}-only
 * personas are skipped because they have nothing to grade against. The caller
 * may pin an explicit subset by id; otherwise every enabled audience persona is
 * considered. Each persona is graded independently and the underlying evaluator
 * caches by persona + content hash, so repeated calls on the same content are
 * cheap. The evaluator degrades to the deterministic readability score when the
 * LLM is unavailable, so the ranking is still produced (just less nuanced).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurPersonaSuggestionService {

    /** Stable synthetic source id so the evaluator cache keys on content hash. */
    private static final String SUGGEST_SOURCE_ID = "suggest";
    private static final String SUGGEST_SOURCE_NAME = "Suggested content";

    private final TurPersonaRepository personaRepository;
    private final TurPersonaContentFitEvaluator evaluator;
    private final TurLlmSummaryService llmSummaryService;

    public TurPersonaSuggestionService(TurPersonaRepository personaRepository,
            TurPersonaContentFitEvaluator evaluator,
            TurLlmSummaryService llmSummaryService) {
        this.personaRepository = personaRepository;
        this.evaluator = evaluator;
        this.llmSummaryService = llmSummaryService;
    }

    /**
     * Evaluate {@code content} against every candidate audience persona and
     * return them ranked best-fit first.
     *
     * @param content    the text to place; never indexed, only graded
     * @param personaIds optional explicit subset of persona ids — when null or
     *                   empty, every enabled audience persona is considered
     * @param regenerate bypass the evaluator cache for a fresh LLM verdict
     */
    public TurPersonaSuggestionReport suggest(String content, List<String> personaIds,
            boolean regenerate) {
        List<TurPersona> candidates = resolveCandidates(personaIds);

        List<TurPersonaSuggestion> scored = new ArrayList<>(candidates.size());
        for (TurPersona persona : candidates) {
            TurContentFitResult result = evaluator.evaluateText(persona, content,
                    SUGGEST_SOURCE_ID, SUGGEST_SOURCE_NAME, regenerate);
            scored.add(new TurPersonaSuggestion(persona.getId(), persona.getName(),
                    result.fitScore(), 0, result));
        }

        // Best fit first; ties broken by persona name so the order is stable.
        scored.sort(Comparator.comparingDouble(TurPersonaSuggestion::fitScore).reversed()
                .thenComparing(s -> s.personaName() == null ? "" : s.personaName()));

        List<TurPersonaSuggestion> ranked = new ArrayList<>(scored.size());
        int rank = 1;
        for (TurPersonaSuggestion s : scored) {
            ranked.add(new TurPersonaSuggestion(s.personaId(), s.personaName(),
                    s.fitScore(), rank++, s.result()));
        }

        TurPersonaSuggestion best = ranked.isEmpty() ? null : ranked.getFirst();
        return new TurPersonaSuggestionReport(
                best == null ? null : best.personaId(),
                best == null ? null : best.personaName(),
                best == null ? 0.0 : best.fitScore(),
                ranked.size(),
                llmSummaryService.isAvailable(),
                ranked);
    }

    private List<TurPersona> resolveCandidates(List<String> personaIds) {
        boolean explicit = personaIds != null && !personaIds.isEmpty();
        return personaRepository.findAll().stream()
                .filter(TurPersona::isUsableAsAudience)
                .filter(p -> explicit ? personaIds.contains(p.getId()) : p.getEnabled() == 1)
                .toList();
    }
}

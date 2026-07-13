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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.research.dto.TurResearchSaturationResultDto;
import com.viglet.turing.genai.research.dto.TurResearchSaturationStepDto;

/**
 * A <strong>pure</strong>, LLM-free theme-saturation scorer for synthetic-user
 * research (Block AW / §XLVI.3, T723). No IO, no Spring dependencies (the
 * {@code @Component} only lets it be injected; it is freely {@code new}-able in
 * tests). Mirrors the T385 NL-facet scorer / T466 readability-scorer discipline:
 * its tests run in CI for free and it produces a hard signal that does not depend
 * on an LLM agreeing.
 *
 * <p>The method: each participant's answers are reduced to a distinct set of
 * <em>theme tokens</em> (significant unigrams — stop-words and short words
 * dropped, diacritics folded so PT/ES/EN behave consistently). Folding personas
 * in <strong>roster order</strong>, a step's {@code newThemes} is how many of its
 * tokens no earlier persona had raised, and {@code noveltyRatio} is
 * {@code newThemes / personaThemeCount}. When the trailing personas stop adding
 * novelty (a dry streak of at least {@link #MIN_DRY_STREAK} whose ratio stays at
 * or below {@link #NOVELTY_THRESHOLD}), the cohort has <em>saturated</em> and we
 * report the honest "sample adequate at N."
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurResearchSaturationScorer {

    /** A marginal persona adding at or below this share of new themes is "dry". */
    static final double NOVELTY_THRESHOLD = 0.15;

    /** How many consecutive trailing dry personas confirm saturation. */
    static final int MIN_DRY_STREAK = 2;

    /** Minimum token length kept as a candidate theme token. */
    private static final int MIN_TOKEN_LENGTH = 4;

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    /**
     * Stop-words across EN / PT / ES (kept deliberately small and shared — the
     * scorer only needs to strip the highest-frequency function words so content
     * tokens dominate the novelty signal).
     */
    private static final Set<String> STOP_WORDS = Set.copyOf(List.of(
            // English
            "the", "and", "that", "this", "with", "have", "would", "could", "should",
            "there", "their", "they", "them", "what", "when", "which", "were", "been",
            "from", "your", "youre", "about", "just", "really", "very", "much", "more",
            "some", "than", "then", "into", "will", "want", "like", "because", "dont",
            "cant", "doesnt", "thing", "things", "make", "made", "also", "even", "only",
            // Portuguese
            "para", "como", "mais", "mas", "porque", "quando", "muito", "isso", "esse",
            "essa", "este", "esta", "com", "sem", "por", "que", "uma", "dos", "das",
            "nao", "sim", "voce", "eles", "elas", "seu", "sua", "meu", "minha", "tambem",
            "sobre", "quero", "acho", "sempre", "nunca", "coisa", "coisas",
            // Spanish
            "pero", "porque", "cuando", "muy", "eso", "este", "esta", "estos", "con",
            "sin", "los", "las", "una", "por", "para", "que", "como", "mas", "tambien",
            "sobre", "quiero", "creo", "siempre", "nunca", "cosa", "cosas", "usted"));

    /**
     * A single participant's material for scoring — {@code text} is the concatenated
     * answers (or any transcript text worth mining) in roster order.
     */
    public record Participant(String personaId, String personaName, String text) {
    }

    /**
     * Score the ordered cohort. Fewer than two participants with any theme tokens
     * cannot demonstrate saturation, so the result is {@code unavailable}.
     *
     * @param participants participants in <strong>roster order</strong>
     */
    public TurResearchSaturationResultDto score(List<Participant> participants) {
        List<Participant> cohort = participants == null ? List.of() : participants;
        List<Set<String>> perPersona = new ArrayList<>(cohort.size());
        int withThemes = 0;
        for (Participant p : cohort) {
            Set<String> tokens = themeTokens(p.text());
            perPersona.add(tokens);
            if (!tokens.isEmpty()) {
                withThemes++;
            }
        }
        if (withThemes < 2) {
            return TurResearchSaturationResultDto.unavailable(
                    "Need at least two completed interviews with content to measure saturation.",
                    cohort.size());
        }

        List<TurResearchSaturationStepDto> steps = new ArrayList<>(cohort.size());
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < cohort.size(); i++) {
            Participant p = cohort.get(i);
            Set<String> tokens = perPersona.get(i);
            int newThemes = 0;
            for (String token : tokens) {
                if (seen.add(token)) {
                    newThemes++;
                }
            }
            double noveltyRatio = tokens.isEmpty() ? 0.0 : (double) newThemes / tokens.size();
            steps.add(new TurResearchSaturationStepDto(i + 1, p.personaId(), p.personaName(),
                    tokens.size(), newThemes, seen.size(), round(noveltyRatio)));
        }

        int adequateAtN = detectSaturation(steps);
        return new TurResearchSaturationResultDto(true, null, cohort.size(), seen.size(),
                adequateAtN >= 0, adequateAtN, NOVELTY_THRESHOLD, MIN_DRY_STREAK, steps);
    }

    /**
     * The smallest N (1-based) such that every persona beyond the Nth was dry
     * (noveltyRatio ≤ threshold), given a trailing dry streak of at least
     * {@link #MIN_DRY_STREAK}. {@code -1} if the tail never went dry.
     */
    private int detectSaturation(List<TurResearchSaturationStepDto> steps) {
        int total = steps.size();
        for (int n = 1; n <= total - MIN_DRY_STREAK; n++) {
            boolean allDry = true;
            for (int k = n; k < total; k++) {
                if (steps.get(k).noveltyRatio() > NOVELTY_THRESHOLD) {
                    allDry = false;
                    break;
                }
            }
            if (allDry) {
                return n;
            }
        }
        return -1;
    }

    /** Distinct significant theme tokens in {@code text} (diacritics folded). */
    private Set<String> themeTokens(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String folded = DIACRITICS.matcher(
                Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        Set<String> tokens = new LinkedHashSet<>();
        for (String raw : TOKEN_SPLIT.split(folded.toLowerCase())) {
            if (raw.length() >= MIN_TOKEN_LENGTH && !STOP_WORDS.contains(raw)) {
                tokens.add(raw);
            }
        }
        return tokens;
    }

    private double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}

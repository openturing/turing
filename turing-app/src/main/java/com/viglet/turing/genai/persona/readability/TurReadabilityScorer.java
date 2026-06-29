/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.readability;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.persona.TurPersonaAudience;
import com.viglet.turing.persistence.model.persona.TurPersonaReadingLevel;

/**
 * A <strong>pure</strong> multilingual readability / complexity scorer
 * (Block AA / §XXVI.3) — no LLM, no IO, no Spring dependencies (the
 * {@code @Component} only lets it be injected; it is freely {@code new}-able in
 * tests). Mirrors the T385 deterministic-scorer discipline: its tests are free
 * and reproducible, and it produces a hard signal that does not depend on an
 * LLM agreeing.
 *
 * <p>Computes language-adapted Flesch Reading Ease / Flesch–Kincaid grade
 * (EN / PT / ES coefficient variants), average sentence length, average
 * syllables per word, a complex-word ratio (polysyllabic words not covered by
 * the persona's {@code vocabularyCeiling} allow-list), and a passive-voice
 * heuristic — then fuses them into a 0–100 fit score against the reader's
 * {@link TurPersonaReadingLevel} grade ceiling.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurReadabilityScorer {

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("[.!?\\n]+");
    private static final Pattern WORD_SPLIT = Pattern.compile("[\\s]+");
    private static final Pattern WORD_CHARS =
            Pattern.compile("[^\\p{L}\\p{M}]");
    private static final Pattern TERM_SPLIT = Pattern.compile("[|,;\\n]+");
    // Passive-voice heuristics per language (auxiliary + past participle shape).
    private static final Pattern PASSIVE_EN = Pattern.compile(
            "\\b(is|are|was|were|be|been|being)\\s+\\w+(ed|en)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSIVE_PT = Pattern.compile(
            "\\b(é|foi|foram|são|ser|sido|está|estão)\\s+\\w+(ad[oa]s?|id[oa]s?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSIVE_ES = Pattern.compile(
            "\\b(es|fue|fueron|son|ser|sido|está|están)\\s+\\w+(ad[oa]s?|id[oa]s?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final int COMPLEX_SYLLABLES = 3;

    /** Language profile selecting Flesch coefficients + passive heuristic. */
    private enum Lang {
        EN(PASSIVE_EN), PT(PASSIVE_PT), ES(PASSIVE_ES);

        final Pattern passive;

        Lang(Pattern passive) {
            this.passive = passive;
        }
    }

    /**
     * Score {@code text} for the given {@code audience}. Blank text yields a
     * neutral 50 with zeroed metrics (nothing to judge).
     */
    public TurReadabilityResult score(String text, TurPersonaAudience audience) {
        Lang lang = resolveLang(audience);
        String normalized = text == null ? "" : text.strip();
        if (normalized.isEmpty()) {
            return new TurReadabilityResult(50.0,
                    new TurReadabilityMetrics(0, 0, 0, 0, 0, 0, 0, 0),
                    List.of("No text to evaluate."));
        }

        List<String> sentences = splitSentences(normalized);
        List<String> words = splitWords(normalized);
        int sentenceCount = Math.max(1, sentences.size());
        int wordCount = Math.max(1, words.size());

        Set<String> allowList = parseAllowList(audience);
        int totalSyllables = 0;
        int complexWords = 0;
        for (String word : words) {
            int syl = countSyllables(word, lang);
            totalSyllables += syl;
            if (syl >= COMPLEX_SYLLABLES && !allowList.contains(word.toLowerCase())) {
                complexWords++;
            }
        }

        double avgSentenceLength = (double) wordCount / sentenceCount;
        double avgSyllablesPerWord = (double) totalSyllables / wordCount;
        double complexWordRatio = (double) complexWords / wordCount;
        double passiveRatio = passiveRatio(sentences, lang);

        double flesch = fleschReadingEase(lang, avgSentenceLength, avgSyllablesPerWord);
        double fkGrade = fleschKincaidGrade(avgSentenceLength, avgSyllablesPerWord);

        TurReadabilityMetrics metrics = new TurReadabilityMetrics(
                round(flesch), round(fkGrade), round(avgSentenceLength),
                round(avgSyllablesPerWord), round(complexWordRatio),
                round(passiveRatio), wordCount, sentenceCount);

        return new TurReadabilityResult(
                round(fitScore(metrics, audience)), metrics,
                buildNotes(metrics, audience));
    }

    // ---- fit score ---------------------------------------------------------

    private double fitScore(TurReadabilityMetrics m, TurPersonaAudience audience) {
        double score = 100.0;
        int ceiling = gradeCeiling(audience);
        double over = m.fleschKincaidGrade() - ceiling;
        if (over > 0) {
            // Smooth, asymptotic penalty (never fully saturates) so the score
            // stays strictly monotonic in the grade gap — two very-hard texts
            // still rank by the reader's ceiling instead of both flooring to 0,
            // which would erase the audience signal.
            score -= 70.0 * over / (over + 10.0);
        }
        // dense vocabulary the reader likely can't follow.
        if (m.complexWordRatio() > 0.10) {
            score -= Math.min(25.0, (m.complexWordRatio() - 0.10) * 120.0);
        }
        // passive constructions hurt comprehension, especially for low readers.
        double passiveWeight = ceiling <= 8 ? 25.0 : 12.0;
        score -= Math.min(passiveWeight, m.passiveVoiceRatio() * passiveWeight);
        return clamp(score);
    }

    private List<String> buildNotes(TurReadabilityMetrics m, TurPersonaAudience audience) {
        List<String> notes = new ArrayList<>();
        int ceiling = gradeCeiling(audience);
        if (m.fleschKincaidGrade() > ceiling) {
            notes.add("Reading grade %.0f exceeds the audience ceiling of %d."
                    .formatted(m.fleschKincaidGrade(), ceiling));
        } else {
            notes.add("Reading grade %.0f is within the audience ceiling of %d."
                    .formatted(m.fleschKincaidGrade(), ceiling));
        }
        if (m.complexWordRatio() > 0.10) {
            notes.add("%.0f%% of words are complex (3+ syllables)."
                    .formatted(m.complexWordRatio() * 100));
        }
        if (m.passiveVoiceRatio() > 0.20) {
            notes.add("%.0f%% of sentences use passive voice."
                    .formatted(m.passiveVoiceRatio() * 100));
        }
        if (m.avgSentenceLength() > 25) {
            notes.add("Average sentence length is %.0f words (long)."
                    .formatted(m.avgSentenceLength()));
        }
        return notes;
    }

    private int gradeCeiling(TurPersonaAudience audience) {
        TurPersonaReadingLevel level = audience == null ? null : audience.getReadingLevel();
        return level == null
                ? TurPersonaReadingLevel.SECONDARY.getApproxGradeCeiling()
                : level.getApproxGradeCeiling();
    }

    // ---- Flesch formulas (language-adapted) --------------------------------

    private double fleschReadingEase(Lang lang, double asl, double aspw) {
        return switch (lang) {
            // Martins et al. adaptation for Brazilian Portuguese.
            case PT -> 248.835 - 1.015 * asl - 84.6 * aspw;
            // Fernández-Huerta for Spanish: 206.84 - 60*P - 1.02*F.
            case ES -> 206.84 - 60.0 * aspw - 1.02 * asl;
            // Classic English Flesch Reading Ease.
            case EN -> 206.835 - 1.015 * asl - 84.6 * aspw;
        };
    }

    private double fleschKincaidGrade(double asl, double aspw) {
        return 0.39 * asl + 11.8 * aspw - 15.59;
    }

    // ---- tokenization + syllables ------------------------------------------

    private List<String> splitSentences(String text) {
        return Arrays.stream(SENTENCE_SPLIT.split(text))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<String> splitWords(String text) {
        return Arrays.stream(WORD_SPLIT.split(text))
                .map(w -> WORD_CHARS.matcher(w).replaceAll(""))
                .filter(w -> !w.isEmpty())
                .toList();
    }

    /**
     * Vowel-group syllable estimate. For PT/ES every vowel group (including
     * accented vowels) counts; for EN we additionally drop a trailing silent
     * "e". Always at least 1.
     */
    private int countSyllables(String word, Lang lang) {
        String w = word.toLowerCase();
        int groups = 0;
        boolean prevVowel = false;
        for (int i = 0; i < w.length(); i++) {
            boolean vowel = isVowel(w.charAt(i));
            if (vowel && !prevVowel) {
                groups++;
            }
            prevVowel = vowel;
        }
        if (lang == Lang.EN && w.endsWith("e") && groups > 1) {
            groups--;
        }
        return Math.max(1, groups);
    }

    private boolean isVowel(char c) {
        return "aeiouáéíóúâêôãõàèìòùäëïöüy".indexOf(c) >= 0;
    }

    private double passiveRatio(List<String> sentences, Lang lang) {
        if (sentences.isEmpty()) {
            return 0.0;
        }
        long passive = sentences.stream()
                .filter(s -> lang.passive.matcher(s).find())
                .count();
        return (double) passive / sentences.size();
    }

    private Set<String> parseAllowList(TurPersonaAudience audience) {
        if (audience == null || audience.getVocabularyCeiling() == null
                || audience.getVocabularyCeiling().isBlank()) {
            return Set.of();
        }
        Set<String> set = new HashSet<>();
        for (String term : TERM_SPLIT.split(audience.getVocabularyCeiling())) {
            String t = term.strip().toLowerCase();
            if (!t.isEmpty() && !t.contains(" ")) {
                set.add(t);
            }
        }
        return set;
    }

    private Lang resolveLang(TurPersonaAudience audience) {
        String code = audience == null ? null : audience.getPrimaryLanguage();
        if (code == null) {
            return Lang.EN;
        }
        String lc = code.trim().toLowerCase();
        if (lc.startsWith("pt")) {
            return Lang.PT;
        }
        if (lc.startsWith("es")) {
            return Lang.ES;
        }
        return Lang.EN;
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

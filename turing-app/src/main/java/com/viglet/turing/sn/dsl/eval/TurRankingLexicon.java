/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.dsl.eval;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;

/**
 * T814 / §LVIII.1 · T815 / §LVIII.2 (Block BJ) — the <strong>runtime ranking
 * lexicon</strong>: the vocabulary {@link TurNLRankingPlanner} used to carry as
 * {@code private static final} fields, now a value built from a
 * {@link TurRankingLexiconBundle}.
 *
 * <p>The bundle is the file and this is what the planner reads. Direction words
 * are kept twice on purpose: the <em>surface</em> forms are what the planner
 * strips out of the user's text, and the <em>stems</em> are what it recognises a
 * direction by, because {@code affordable} analyses to {@code afford} and a set of
 * surface words could never match it. The {@code sort by} triggers arrive as
 * phrases and leave as one compiled {@link Pattern}, so {@code detect} never pays
 * for a regex compile.
 *
 * <p>{@link #tokens(String)} is the single tokenisation path — the Lucene
 * {@link Analyzer} T815 replaced the hand-rolled split/expand/stopword code with,
 * run over the query and each declared field alike.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurRankingLexicon(
        String language,
        Set<String> ascWords,
        Set<String> descWords,
        Set<String> ascStems,
        Set<String> descStems,
        Pattern sortTriggerPattern,
        Analyzer analyzer) {

    /** The language every deployment gets, and the fallback for any other (T816). */
    public static final String DEFAULT_LANGUAGE = "en";

    /**
     * Builds the runtime lexicon from a (possibly already merged) bundle: the
     * direction words are lower-cased and stemmed once here rather than at every
     * comparison, the {@code sort by} phrases are compiled into a single
     * alternation whose inner whitespace matches any run of spaces, and the
     * matching analyzer is told to protect the ranking vocabulary from its own
     * stopword list.
     */
    public static TurRankingLexicon of(TurRankingLexiconBundle bundle) {
        String language = bundle.language() == null || bundle.language().isBlank()
                ? DEFAULT_LANGUAGE
                : bundle.language().toLowerCase(Locale.ROOT);
        Set<String> ascWords = words(bundle.ascWords());
        Set<String> descWords = words(bundle.descWords());
        Set<String> protectedWords = new LinkedHashSet<>(ascWords);
        protectedWords.addAll(descWords);
        protectedWords.addAll(words(List.copyOf(bundle.synonyms().keySet())));
        Analyzer stemOnly = TurRankingAnalyzers.stemOnly(bundle);
        return new TurRankingLexicon(language, ascWords, descWords,
                stems(stemOnly, ascWords), stems(stemOnly, descWords),
                sortTriggerPattern(bundle.sortTriggers()),
                TurRankingAnalyzers.matching(bundle, protectedWords));
    }

    /**
     * The distinct terms {@code value} analyses to — stemmed, stopword-filtered and
     * synonym-expanded by the one analyzer both the query and the declared fields
     * go through.
     */
    public Set<String> tokens(String value) {
        return TurRankingAnalyzers.tokens(analyzer, value);
    }

    private static Set<String> stems(Analyzer stemOnly, Set<String> surfaceWords) {
        Set<String> stemmed = new LinkedHashSet<>();
        surfaceWords.forEach(word -> stemmed.addAll(TurRankingAnalyzers.tokens(stemOnly, word)));
        return Collections.unmodifiableSet(stemmed);
    }

    private static Set<String> words(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String word = normalize(value);
            if (word != null) {
                normalized.add(word);
            }
        }
        // Insertion order is kept (Set.copyOf would not): the sort-trigger alternation
        // is built from this set, and a declared order is the author's disambiguation.
        return Collections.unmodifiableSet(normalized);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * {@code ["sorted by", "order by"]} → {@code \b(?:sorted\s+by|order\s+by)\b}.
     * Returns {@code null} when the bundle declares no trigger, which the planner
     * reads as "this language has no explicit sort phrasing".
     */
    private static Pattern sortTriggerPattern(List<String> triggers) {
        Set<String> phrases = words(triggers);
        if (phrases.isEmpty()) {
            return null;
        }
        StringBuilder alternation = new StringBuilder();
        for (String phrase : phrases) {
            if (!alternation.isEmpty()) {
                alternation.append('|');
            }
            String[] parts = phrase.split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    alternation.append("\\s+");
                }
                alternation.append(Pattern.quote(parts[i]));
            }
        }
        return Pattern.compile("\\b(?:" + alternation + ")\\b", Pattern.CASE_INSENSITIVE);
    }
}

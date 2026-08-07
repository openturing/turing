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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * T814 / §LVIII.1 (Block BJ) — the <strong>runtime ranking lexicon</strong>: the
 * vocabulary {@link TurNLRankingPlanner} used to carry as {@code private static
 * final} fields, now a value built from a {@link TurRankingLexiconBundle}.
 *
 * <p>The bundle is the file and this is what the planner reads: every word is
 * lower-cased once here rather than at every comparison, and the {@code sort by}
 * triggers arrive as phrases and leave as one compiled {@link Pattern}, so
 * {@code detect} never pays for a regex compile.
 *
 * <p>Behaviour is unchanged from T811 — the shipped {@code en} bundle is the old
 * word sets verbatim. What changed is that the vocabulary is now data, which is
 * the seam T815/T816/T817 hang off.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurRankingLexicon(
        String language,
        Set<String> ascWords,
        Set<String> descWords,
        Pattern sortTriggerPattern,
        Set<String> stopwords,
        Map<String, Set<String>> synonyms) {

    /** The language every deployment gets, and the fallback for any other (T816). */
    public static final String DEFAULT_LANGUAGE = "en";

    /**
     * Builds the runtime lexicon from a (possibly already merged) bundle:
     * lower-cases every word, drops blanks, and compiles the {@code sort by}
     * phrases into a single alternation whose inner whitespace matches any run of
     * spaces.
     */
    public static TurRankingLexicon of(TurRankingLexiconBundle bundle) {
        String language = bundle.language() == null || bundle.language().isBlank()
                ? DEFAULT_LANGUAGE
                : bundle.language().toLowerCase(Locale.ROOT);
        Map<String, Set<String>> synonyms = new LinkedHashMap<>();
        bundle.synonyms().forEach((trigger, concepts) -> {
            String key = normalize(trigger);
            if (key != null) {
                synonyms.put(key, words(concepts));
            }
        });
        return new TurRankingLexicon(language, words(bundle.ascWords()), words(bundle.descWords()),
                sortTriggerPattern(bundle.sortTriggers()), words(bundle.stopwords()),
                Map.copyOf(synonyms));
    }

    /** The concept tokens a query word injects, or an empty set when it bridges nothing. */
    public Set<String> synonymsOf(String token) {
        return synonyms.getOrDefault(token, Set.of());
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

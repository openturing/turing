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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * T814 / §LVIII.1 (Block BJ) — the <strong>on-disk shape</strong> of a ranking
 * lexicon: the four word sets {@link TurNLRankingPlanner} used to hard-code, as a
 * JSON resource one bundle per language ({@code ranking/lexicon-<lang>.json}).
 *
 * <p>This record is the file, not the runtime value — it stays plain data so
 * Jackson can bind it and so a future per-language / per-deployment override
 * (T816) or a per-site one (T817) can be <em>merged</em> before anything is
 * compiled. {@link TurRankingLexicon#of(TurRankingLexiconBundle)} is what turns
 * the merged result into the value the planner reads.
 *
 * <p>Every field is optional: a bundle that declares only {@code synonyms} is a
 * legal override, and a missing list is an empty one rather than a failure.
 *
 * <p>{@code stemmer} (T815) is a Snowball language name — {@code English},
 * {@code Portuguese}, {@code Spanish} — and addresses both the stemmer and
 * Snowball's stopword list for that language, so a new language is a resource and
 * not a rebuild. Absent, nothing is stemmed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurRankingLexiconBundle(
        String language,
        String stemmer,
        List<String> ascWords,
        List<String> descWords,
        List<String> sortTriggers,
        List<String> stopwords,
        Map<String, List<String>> synonyms) {

    /** An empty bundle — what an unreadable or absent resource degrades to. */
    public static TurRankingLexiconBundle empty(String language) {
        return new TurRankingLexiconBundle(language, null, List.of(), List.of(), List.of(),
                List.of(), Map.of());
    }

    /**
     * T816 / §LVIII.3 — this bundle with {@code override} laid over it: every list
     * is the union (this one's order first), the synonym map is merged key by key,
     * and a declared {@code language}/{@code stemmer} wins. Extending is the
     * default because an override that had to restate the shipped vocabulary to add
     * one word would be a fork, not a tune.
     */
    public TurRankingLexiconBundle mergedWith(TurRankingLexiconBundle override) {
        if (override == null) {
            return this;
        }
        Map<String, List<String>> synonyms = new LinkedHashMap<>(synonyms());
        override.synonyms().forEach((trigger, concepts) ->
                synonyms.merge(trigger, concepts, TurRankingLexiconBundle::union));
        return new TurRankingLexiconBundle(
                override.language() == null ? language : override.language(),
                override.stemmer() == null ? stemmer : override.stemmer(),
                union(ascWords(), override.ascWords()),
                union(descWords(), override.descWords()),
                union(sortTriggers(), override.sortTriggers()),
                union(stopwords(), override.stopwords()),
                Map.copyOf(synonyms));
    }

    private static List<String> union(List<String> first, List<String> second) {
        Set<String> merged = new LinkedHashSet<>(first == null ? List.of() : first);
        merged.addAll(second == null ? List.of() : second);
        return List.copyOf(merged);
    }

    /** Null-safe accessors so a partially-declared bundle needs no defensive caller. */
    @Override
    public List<String> ascWords() {
        return ascWords == null ? List.of() : ascWords;
    }

    @Override
    public List<String> descWords() {
        return descWords == null ? List.of() : descWords;
    }

    @Override
    public List<String> sortTriggers() {
        return sortTriggers == null ? List.of() : sortTriggers;
    }

    @Override
    public List<String> stopwords() {
        return stopwords == null ? List.of() : stopwords;
    }

    @Override
    public Map<String, List<String>> synonyms() {
        return synonyms == null ? Map.of() : synonyms;
    }
}

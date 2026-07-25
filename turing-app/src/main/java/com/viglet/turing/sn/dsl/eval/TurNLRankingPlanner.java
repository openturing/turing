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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * T811 / §LVII.1 (Block BI) — the <strong>deterministic ranking-intent
 * planner</strong>: a pure, LLM-free component that recognises superlative /
 * comparative / {@code sorted by} phrasing in a natural-language catalog query and
 * resolves it to a numeric {@code sort} over the site's <em>declared</em> schema.
 *
 * <p>It exists because NL→DSL query planning is otherwise 100% LLM
 * ({@link TurLlmNLFacetParser}), and a cheap default model is unreliable at the
 * ranking dimension: a superlative / interrogative phrasing ("which chat model
 * scores <em>highest</em> on the intelligence index?") makes the model return an
 * empty query, dropping even the valid facet filter. Handling ranking procedurally
 * lets the LLM focus on faceting (which it does well) while this planner owns the
 * sort — and lets the caller <em>strip</em> the ranking clause from the text the
 * LLM sees, so the facet ({@code kind=CHAT}) survives.
 *
 * <p>Everything is generic: direction comes from a small superlative lexicon, and
 * the target field is resolved by scoring the query's tokens against each numeric
 * field's <em>own</em> name + description tokens (plus a compact adjective→noun
 * synonym seed to bridge "cheapest"→"price", "intelligent"→"intelligence",
 * "fastest"→"latency"). No catalog-specific vocabulary lives here. When nothing
 * resolves the planner returns {@link RankingIntent#none} and the caller's path is
 * unchanged — additive and fail-open by construction.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurNLRankingPlanner {

    /** Superlative / comparative words that mean "smallest first" (ascending). */
    private static final Set<String> ASC_WORDS = Set.of(
            "cheapest", "cheaper", "lowest", "lower", "smallest", "least", "minimum", "min",
            "fastest", "faster", "quickest", "shortest", "inexpensive", "affordable");

    /** Superlative / comparative words that mean "largest first" (descending). */
    private static final Set<String> DESC_WORDS = Set.of(
            "most", "highest", "higher", "largest", "larger", "biggest", "bigger", "greatest",
            "best", "top", "strongest", "maximum", "max", "longest", "longer", "widest", "deepest");

    /**
     * Explicit "sort/order/rank by …" triggers. Direction defaults to descending
     * (list the top first) unless an ASC word is also present in the phrase.
     */
    private static final Pattern SORT_BY = Pattern.compile(
            "\\b(?:sorted|sort|order|ordered|ranked|rank)\\s+by\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Adjective/verb → concept-noun seed. A query word on the left injects the
     * right-hand tokens into the query's token bag so a pure-adjective query
     * ("cheapest", "most intelligent", "fastest") still overlaps the declared
     * field's name/description tokens ("pricing", "intelligence", "latency").
     */
    private static final Map<String, Set<String>> SYNONYMS = Map.ofEntries(
            Map.entry("cheap", Set.of("price", "pricing", "cost")),
            Map.entry("cheapest", Set.of("price", "pricing", "cost")),
            Map.entry("cheaper", Set.of("price", "pricing", "cost")),
            Map.entry("expensive", Set.of("price", "pricing", "cost")),
            Map.entry("affordable", Set.of("price", "pricing", "cost")),
            Map.entry("inexpensive", Set.of("price", "pricing", "cost")),
            Map.entry("price", Set.of("pricing", "cost")),
            Map.entry("priced", Set.of("price", "pricing", "cost")),
            Map.entry("cost", Set.of("price", "pricing")),
            Map.entry("intelligent", Set.of("intelligence", "quality")),
            Map.entry("intelligence", Set.of("quality")),
            Map.entry("smart", Set.of("intelligence", "quality")),
            Map.entry("smartest", Set.of("intelligence", "quality")),
            Map.entry("capable", Set.of("intelligence", "quality", "capability")),
            Map.entry("fast", Set.of("latency", "throughput", "speed", "performance")),
            Map.entry("fastest", Set.of("latency", "throughput", "speed", "performance")),
            Map.entry("faster", Set.of("latency", "throughput", "speed", "performance")),
            Map.entry("quick", Set.of("latency", "speed", "performance")),
            Map.entry("quickest", Set.of("latency", "speed", "performance")),
            Map.entry("speed", Set.of("latency", "throughput", "performance")),
            Map.entry("big", Set.of("context", "window", "length", "size")),
            Map.entry("biggest", Set.of("context", "window", "length", "size")),
            Map.entry("large", Set.of("context", "window", "length", "size")),
            Map.entry("largest", Set.of("context", "window", "length", "size")),
            Map.entry("long", Set.of("context", "window", "length")),
            Map.entry("longest", Set.of("context", "window", "length")),
            Map.entry("output", Set.of("output", "tokens")));

    /** Words carrying no signal for field resolution (kept out of the token bag). */
    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "of", "by", "on", "in", "to", "for", "with", "and", "or", "is",
            "are", "was", "has", "have", "had", "which", "what", "that", "this", "these", "those",
            "model", "models", "me", "show", "find", "give", "list", "per", "it", "its", "their",
            "as", "at", "be", "do", "does", "get", "you", "your", "can", "we", "how", "one", "sorted",
            "sort", "order", "ordered", "ranked", "rank");

    /** Splits camelCase boundaries so {@code intelligenceIndex} → two tokens. */
    private static final Pattern CAMEL = Pattern.compile("([a-z0-9])([A-Z])");

    /**
     * Detect any ranking intent in {@code query} and resolve it against the
     * declared {@code schema}. Never throws; returns {@link RankingIntent#none}
     * (carrying the query unchanged as its stripped form) when no superlative /
     * comparative / {@code sort by} phrasing is present or no numeric field
     * resolves.
     */
    public RankingIntent detect(String query, List<TurNLFacetField> schema) {
        if (!StringUtils.hasText(query) || schema == null || schema.isEmpty()) {
            return RankingIntent.none(query);
        }
        Set<String> queryTokens = expand(tokenize(query));
        String direction = resolveDirection(query, queryTokens);
        if (direction == null) {
            return RankingIntent.none(query);
        }
        TurNLFacetField field = resolveField(queryTokens, schema);
        if (field == null) {
            return RankingIntent.none(query);
        }
        String stripped = strip(query, field);
        return new RankingIntent(true, field.name(), direction, stripped);
    }

    // ─────────────────────────── Direction ───────────────────────────

    /**
     * Resolves the sort direction, or {@code null} when the query carries no
     * ranking intent. An explicit ASC/DESC superlative wins; a bare "sort by …"
     * defaults to descending (top first) unless an ASC word rides along.
     */
    private String resolveDirection(String query, Set<String> queryTokens) {
        boolean asc = queryTokens.stream().anyMatch(ASC_WORDS::contains);
        boolean desc = queryTokens.stream().anyMatch(DESC_WORDS::contains);
        if (asc) {
            return "asc";
        }
        if (desc) {
            return "desc";
        }
        // "sorted by <metric>" with no explicit adjective → top-first (descending).
        return SORT_BY.matcher(query).find() ? "desc" : null;
    }

    // ─────────────────────────── Field resolution ───────────────────────────

    /**
     * Picks the numeric declared field whose name + description tokens best overlap
     * the (synonym-expanded) query tokens. Name matches weigh double. Returns
     * {@code null} when nothing overlaps (so the caller emits no sort rather than
     * guessing a field).
     */
    private TurNLFacetField resolveField(Set<String> queryTokens, List<TurNLFacetField> schema) {
        TurNLFacetField best = null;
        int bestScore = 0;
        for (TurNLFacetField field : schema) {
            if (field == null || field.name() == null || !field.numeric()) {
                continue;
            }
            int score = score(field, queryTokens);
            if (score > bestScore) {
                bestScore = score;
                best = field;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private int score(TurNLFacetField field, Set<String> queryTokens) {
        Set<String> nameTokens = tokenize(field.name());
        Set<String> descTokens = tokenize(field.description());
        int score = 0;
        for (String token : queryTokens) {
            if (nameTokens.contains(token)) {
                score += 2;
            } else if (descTokens.contains(token)) {
                score += 1;
            }
        }
        return score;
    }

    /** The query tokens (minus stopwords) that actually matched the resolved field. */
    private Set<String> matchedTokens(TurNLFacetField field, Set<String> queryTokens) {
        Set<String> fieldTokens = new LinkedHashSet<>(tokenize(field.name()));
        fieldTokens.addAll(tokenize(field.description()));
        Set<String> matched = new LinkedHashSet<>();
        for (String token : queryTokens) {
            if (fieldTokens.contains(token)) {
                matched.add(token);
            }
        }
        return matched;
    }

    // ─────────────────────────── Stripping ───────────────────────────

    /**
     * Removes the ranking clause from {@code query} so the LLM faceting pass sees a
     * clean query and keeps the facet filter. Cuts everything from a "sort by …"
     * trigger onward, then drops standalone direction words and the metric words
     * that resolved the field. The remainder ("chat models") is what the caller
     * hands to the parser; a blank remainder means "sort-only, no facet".
     */
    private String strip(String query, TurNLFacetField field) {
        String text = query;
        Matcher sortBy = SORT_BY.matcher(text);
        if (sortBy.find()) {
            text = text.substring(0, sortBy.start());
        }
        Set<String> remove = new LinkedHashSet<>();
        remove.addAll(ASC_WORDS);
        remove.addAll(DESC_WORDS);
        remove.addAll(matchedTokens(field, expand(tokenize(query))));
        for (String word : remove) {
            text = text.replaceAll("(?i)\\b" + Pattern.quote(word) + "\\b", " ");
        }
        String cleaned = text.replaceAll("[?!.,;:]+", " ").replaceAll("\\s+", " ").trim();
        // A remnant of only stopwords ("which model has the") carries no facet — treat
        // it as blank so the caller runs a sort-only match_all rather than asking the
        // LLM to facet on nothing.
        return tokenize(cleaned).isEmpty() ? "" : cleaned;
    }

    // ─────────────────────────── Tokenising ───────────────────────────

    /** Lower-cased alnum tokens (camelCase split), stopwords and 1-char noise removed. */
    static Set<String> tokenize(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        String spaced = CAMEL.matcher(value).replaceAll("$1 $2").toLowerCase(Locale.ROOT);
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : spaced.split("[^a-z0-9]+")) {
            if (part.length() >= 2 && !STOPWORDS.contains(part)) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    /** Adds synonym concept-tokens for any trigger word present in {@code tokens}. */
    private Set<String> expand(Set<String> tokens) {
        Set<String> expanded = new LinkedHashSet<>(tokens);
        for (String token : tokens) {
            Set<String> extra = SYNONYMS.get(token);
            if (extra != null) {
                expanded.addAll(extra);
            }
        }
        return expanded;
    }

    /**
     * A resolved ranking intent: the numeric {@code field} to sort on, the
     * {@code order} ({@code asc}/{@code desc}), and the {@code strippedQuery} the
     * caller should hand to the LLM faceting pass (the original query minus the
     * ranking clause). {@link #none} carries the query through unchanged.
     *
     * @author Alexandre Oliveira
     * @since 2026.3.4
     */
    public record RankingIntent(boolean present, String field, String order, String strippedQuery) {

        /** No ranking intent — the query is passed through unchanged for faceting. */
        public static RankingIntent none(String query) {
            return new RankingIntent(false, null, null, query);
        }

        /** True when a numeric sort field resolved (a sort should be injected). */
        public boolean hasSort() {
            return present && field != null && order != null;
        }
    }
}

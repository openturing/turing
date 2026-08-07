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
 * <p>T814 / §LVIII.1 (Block BJ) — that vocabulary is no longer compiled in: the
 * four word sets are a {@link TurRankingLexicon} served by
 * {@link TurRankingLexiconProvider} from a resource bundle. English behaviour is
 * unchanged; the difference is that the lexicon is now data, which is what
 * per-language resolution and deployment overrides hang off.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurNLRankingPlanner {

    /** Splits camelCase boundaries so {@code intelligenceIndex} → two tokens. */
    private static final Pattern CAMEL = Pattern.compile("([a-z0-9])([A-Z])");

    private final TurRankingLexiconProvider lexiconProvider;

    public TurNLRankingPlanner(TurRankingLexiconProvider lexiconProvider) {
        this.lexiconProvider = lexiconProvider;
    }

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
        TurRankingLexicon lexicon = lexiconProvider.lexicon();
        Set<String> queryTokens = expand(tokenize(query, lexicon), lexicon);
        String direction = resolveDirection(query, queryTokens, lexicon);
        if (direction == null) {
            return RankingIntent.none(query);
        }
        TurNLFacetField field = resolveField(queryTokens, schema, lexicon);
        if (field == null) {
            return RankingIntent.none(query);
        }
        String stripped = strip(query, field, lexicon);
        return new RankingIntent(true, field.name(), direction, stripped);
    }

    // ─────────────────────────── Direction ───────────────────────────

    /**
     * Resolves the sort direction, or {@code null} when the query carries no
     * ranking intent. An explicit ASC/DESC superlative wins; a bare "sort by …"
     * defaults to descending (top first) unless an ASC word rides along.
     */
    private String resolveDirection(String query, Set<String> queryTokens,
            TurRankingLexicon lexicon) {
        if (queryTokens.stream().anyMatch(lexicon.ascWords()::contains)) {
            return "asc";
        }
        if (queryTokens.stream().anyMatch(lexicon.descWords()::contains)) {
            return "desc";
        }
        // "sorted by <metric>" with no explicit adjective → top-first (descending).
        Pattern sortBy = lexicon.sortTriggerPattern();
        return sortBy != null && sortBy.matcher(query).find() ? "desc" : null;
    }

    // ─────────────────────────── Field resolution ───────────────────────────

    /**
     * Picks the numeric declared field whose name + description tokens best overlap
     * the (synonym-expanded) query tokens. Name matches weigh double. Returns
     * {@code null} when nothing overlaps (so the caller emits no sort rather than
     * guessing a field).
     */
    private TurNLFacetField resolveField(Set<String> queryTokens, List<TurNLFacetField> schema,
            TurRankingLexicon lexicon) {
        TurNLFacetField best = null;
        int bestScore = 0;
        for (TurNLFacetField field : schema) {
            if (field == null || field.name() == null || !field.numeric()) {
                continue;
            }
            int score = score(field, queryTokens, lexicon);
            if (score > bestScore) {
                bestScore = score;
                best = field;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private int score(TurNLFacetField field, Set<String> queryTokens, TurRankingLexicon lexicon) {
        Set<String> nameTokens = tokenize(field.name(), lexicon);
        Set<String> descTokens = tokenize(field.description(), lexicon);
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
    private Set<String> matchedTokens(TurNLFacetField field, Set<String> queryTokens,
            TurRankingLexicon lexicon) {
        Set<String> fieldTokens = new LinkedHashSet<>(tokenize(field.name(), lexicon));
        fieldTokens.addAll(tokenize(field.description(), lexicon));
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
    private String strip(String query, TurNLFacetField field, TurRankingLexicon lexicon) {
        String text = query;
        Pattern sortBy = lexicon.sortTriggerPattern();
        if (sortBy != null) {
            Matcher matcher = sortBy.matcher(text);
            if (matcher.find()) {
                text = text.substring(0, matcher.start());
            }
        }
        Set<String> remove = new LinkedHashSet<>();
        remove.addAll(lexicon.ascWords());
        remove.addAll(lexicon.descWords());
        remove.addAll(matchedTokens(field, expand(tokenize(query, lexicon), lexicon), lexicon));
        for (String word : remove) {
            text = text.replaceAll("(?i)\\b" + Pattern.quote(word) + "\\b", " ");
        }
        String cleaned = text.replaceAll("[?!.,;:]+", " ").replaceAll("\\s+", " ").trim();
        // A remnant of only stopwords ("which model has the") carries no facet — treat
        // it as blank so the caller runs a sort-only match_all rather than asking the
        // LLM to facet on nothing.
        return tokenize(cleaned, lexicon).isEmpty() ? "" : cleaned;
    }

    // ─────────────────────────── Tokenising ───────────────────────────

    /** Lower-cased alnum tokens (camelCase split), stopwords and 1-char noise removed. */
    static Set<String> tokenize(String value, TurRankingLexicon lexicon) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        String spaced = CAMEL.matcher(value).replaceAll("$1 $2").toLowerCase(Locale.ROOT);
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : spaced.split("[^a-z0-9]+")) {
            if (part.length() >= 2 && !lexicon.stopwords().contains(part)) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    /** Adds synonym concept-tokens for any trigger word present in {@code tokens}. */
    private Set<String> expand(Set<String> tokens, TurRankingLexicon lexicon) {
        Set<String> expanded = new LinkedHashSet<>(tokens);
        for (String token : tokens) {
            expanded.addAll(lexicon.synonymsOf(token));
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

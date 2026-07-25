/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.catalog.planning;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.sn.dsl.TurDslQuery;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetField;

/**
 * T819–T820 / §LIX (Block BK) — the <strong>deterministic</strong> checks the LLM
 * planning strategies lean on. Everything here is pure and LLM-free, which is what
 * makes the multi-pass planner testable in CI: the LLM proposes, this validates.
 *
 * <ul>
 *   <li>{@link #referencedFields(TurDslQueryRequest)} — every field name a query
 *       body touches (filters, ranges, sorts), so an <em>ungrounded</em> field the
 *       model invented can be detected instead of failing at the engine.</li>
 *   <li>{@link #ungroundedFields(TurDslQueryRequest, List)} — those names that are
 *       not in the site's declared schema.</li>
 *   <li>{@link #isDegenerate(TurDslQueryRequest)} — "the parse came back empty":
 *       a bare {@code match_all} with no sort and no filter, which retrieves the
 *       catalog in arbitrary order and is the exact symptom the block exists to
 *       repair.</li>
 *   <li>{@link #isSpecific(String)} — whether the question was specific enough that
 *       a degenerate plan is suspicious (a broad "what do you have?" legitimately
 *       plans to {@code match_all}).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurCopilotPlanValidator {

    /**
     * Minimum number of meaning-carrying words a question must have before a
     * degenerate {@code match_all} plan counts as suspect. Below it the user really
     * did ask something broad ("what do you have?") and {@code match_all} is right.
     */
    private static final int SPECIFIC_WORD_THRESHOLD = 3;

    /**
     * Words that carry no retrieval intent when deciding if a question is specific.
     * Deliberately bilingual (en + pt) — this is a coarse "did the user ask something
     * specific" gate, not the T811 ranking lexicon, so a couple of languages' filler
     * words are enough and no per-language resource is warranted.
     */
    private static final Set<String> FILLER = Set.of(
            "the", "an", "of", "in", "on", "to", "for", "with", "and", "or", "is", "are",
            "what", "which", "who", "how", "do", "does", "you", "have", "there", "me", "show",
            "list", "give", "any", "all", "your", "it", "its", "que", "qual", "quais", "os",
            "as", "um", "uma", "de", "da", "ou", "tem", "mostre", "quero");

    /** Every field name {@code request} references in its query clauses and sorts. */
    public Set<String> referencedFields(TurDslQueryRequest request) {
        Set<String> fields = new LinkedHashSet<>();
        if (request == null) {
            return fields;
        }
        collect(request.query(), fields);
        collectSortFields(request.sort(), fields);
        fields.remove(TurSNFieldName.DEFAULT);
        return fields;
    }

    /**
     * The referenced field names that are <em>not</em> declared in {@code schema} —
     * the "ungrounded field" the §LIX judge pass is meant to catch. An empty or null
     * schema grounds nothing, so it returns an empty set (fail-open: never accuse a
     * plan when we have no schema to check it against).
     */
    public Set<String> ungroundedFields(TurDslQueryRequest request, List<TurNLFacetField> schema) {
        if (schema == null || schema.isEmpty()) {
            return Set.of();
        }
        Set<String> declared = new LinkedHashSet<>();
        for (TurNLFacetField field : schema) {
            if (field != null && field.name() != null) {
                declared.add(field.name());
            }
        }
        Set<String> ungrounded = new LinkedHashSet<>(referencedFields(request));
        ungrounded.removeIf(declared::contains);
        return ungrounded;
    }

    /**
     * True when the plan carries no retrieval signal at all: a null query, or a bare
     * {@code match_all} (or an empty {@code bool}) with no sort. That is the shape a
     * weak model returns when it gives up — it retrieves the catalog in arbitrary
     * order, so it looks like a success while answering nothing.
     */
    public boolean isDegenerate(TurDslQueryRequest request) {
        if (request == null || request.query() == null) {
            return true;
        }
        boolean hasSort = request.sort() != null && !request.sort().isEmpty();
        return !hasSort && isEmptyClause(request.query());
    }

    /**
     * True when {@code query} has at least {@link #SPECIFIC_WORD_THRESHOLD}
     * meaning-carrying words — i.e. specific enough that a degenerate plan means the
     * planner failed rather than the user asking something deliberately broad.
     */
    public boolean isSpecific(String query) {
        if (!StringUtils.hasText(query)) {
            return false;
        }
        int words = 0;
        for (String raw : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (raw.length() >= 2 && !FILLER.contains(raw)) {
                words++;
            }
        }
        return words >= SPECIFIC_WORD_THRESHOLD;
    }

    // ─────────────────────────── internals ───────────────────────────

    /** True for a clause that constrains nothing ({@code match_all} / empty bool). */
    private boolean isEmptyClause(TurDslQuery query) {
        return switch (query) {
            case TurDslQuery.MatchAll ignored -> true;
            case TurDslQuery.Bool b -> isEmpty(b.filter()) && isEmpty(b.must())
                    && isEmpty(b.should()) && isEmpty(b.mustNot());
            case TurDslQuery.Match m -> TurSNFieldName.DEFAULT.equals(m.field())
                    && !StringUtils.hasText(m.query());
            default -> false;
        };
    }

    private static boolean isEmpty(List<TurDslQuery> clauses) {
        return clauses == null || clauses.isEmpty();
    }

    /**
     * Walks the clause tree collecting field names. Only the clause types the copilot
     * path can actually produce are enumerated; anything else contributes nothing
     * (fail-open — an unknown clause is never reported as ungrounded).
     */
    private void collect(TurDslQuery query, Set<String> fields) {
        if (query == null) {
            return;
        }
        switch (query) {
            case TurDslQuery.Bool b -> {
                collectAll(b.filter(), fields);
                collectAll(b.must(), fields);
                collectAll(b.should(), fields);
                collectAll(b.mustNot(), fields);
            }
            case TurDslQuery.Term(String field, Object ignored) -> add(fields, field);
            case TurDslQuery.Terms(String field, var ignored) -> add(fields, field);
            case TurDslQuery.Range r -> add(fields, r.field());
            case TurDslQuery.Match m -> add(fields, m.field());
            case TurDslQuery.MatchPhrase m -> add(fields, m.field());
            case TurDslQuery.Prefix p -> add(fields, p.field());
            case TurDslQuery.Wildcard w -> add(fields, w.field());
            case TurDslQuery.Exists e -> add(fields, e.field());
            case TurDslQuery.ConstantScore c -> collect(c.filter(), fields);
            case TurDslQuery.Nested n -> collect(n.query(), fields);
            default -> {
                // clause type the copilot never emits — nothing to ground
            }
        }
    }

    private void collectAll(List<TurDslQuery> clauses, Set<String> fields) {
        if (clauses != null) {
            clauses.forEach(c -> collect(c, fields));
        }
    }

    /**
     * Sort entries arrive as either a bare field name or a single-entry
     * {@code {field: order}} map (both shapes the DSL accepts).
     */
    private void collectSortFields(List<Object> sort, Set<String> fields) {
        if (sort == null) {
            return;
        }
        for (Object entry : sort) {
            if (entry instanceof String name) {
                add(fields, name);
            } else if (entry instanceof java.util.Map<?, ?> map) {
                map.keySet().forEach(key -> add(fields, key == null ? null : key.toString()));
            }
        }
    }

    private static void add(Set<String> fields, String field) {
        if (StringUtils.hasText(field)) {
            fields.add(field);
        }
    }
}

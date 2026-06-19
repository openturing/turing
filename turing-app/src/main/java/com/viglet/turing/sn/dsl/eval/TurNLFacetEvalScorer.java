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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.viglet.turing.sn.dsl.TurDslQuery;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalReport.CaseResult;
import com.viglet.turing.sn.dsl.eval.TurNLFacetExpectation.ExpectedFilter;
import com.viglet.turing.sn.dsl.eval.TurNLFacetExpectation.ExpectedRange;
import com.viglet.turing.sn.dsl.eval.TurNLFacetExpectation.ExpectedSort;

/**
 * The deterministic heart of the NL→facet eval pack (T385 / §XX.5): given an
 * expectation and the structured {@link TurDslQueryRequest} a parser produced, it
 * decides whether each expected filter / range / sort is present and whether the
 * parser stayed inside the declared schema.
 *
 * <p>It is pure (no LLM, no DB), so it runs in CI and is exhaustively unit-tested.
 * Matching is tolerant by design — see {@link TurNLFacetExpectation}. Two
 * properties are enforced as hard failures:
 * <ul>
 *   <li><b>grounding</b> — any term/range/sort field outside the declared schema
 *       (an LLM hallucination) fails the case, regardless of the expectations met;</li>
 *   <li><b>completeness</b> — every expected clause must be present.</li>
 * </ul>
 *
 * <p>Special engine fields ({@code _text_}, {@code _score}, {@code _id},
 * {@code id}, {@code _all}) are always considered grounded.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurNLFacetEvalScorer {

    private static final Set<String> SPECIAL_FIELDS =
            Set.of("_text_", "_score", "score", "_id", "id", "_all", "*");

    /** Numeric equality tolerance for range-bound comparison. */
    private static final double EPSILON = 1e-6;

    /**
     * Score one case.
     *
     * @param caseName    the case label (for the result).
     * @param expectation the golden expected output.
     * @param actual      the parser's structured query (may be null on parse fail).
     * @param schema      the declared fields the parser was allowed to use.
     */
    public CaseResult score(String caseName, TurNLFacetExpectation expectation,
            TurDslQueryRequest actual, List<TurNLFacetField> schema) {
        if (actual == null) {
            return new CaseResult(caseName, false, 0d, List.of("parser returned no query"),
                    List.of(), "no query");
        }

        Collected collected = collect(actual);
        Set<String> declared = declaredFieldNames(schema);

        List<String> findings = new ArrayList<>();
        int[] tally = scoreExpectations(expectation, collected, findings);
        int expectedCount = tally[0];
        int matchedCount = tally[1];

        List<String> ungrounded = findUngroundedFields(collected, declared);
        if (!ungrounded.isEmpty()) {
            findings.add("ungrounded fields (not in schema): " + ungrounded);
        }

        double score = expectedCount == 0 ? 1d : (double) matchedCount / expectedCount;
        boolean passed = matchedCount == expectedCount && ungrounded.isEmpty();
        return new CaseResult(caseName, passed, score, findings, ungrounded, null);
    }

    /** Lower-cased set of the schema's declared field names. */
    private Set<String> declaredFieldNames(List<TurNLFacetField> schema) {
        Set<String> declared = new LinkedHashSet<>();
        for (TurNLFacetField f : schema) {
            if (f != null && f.name() != null) {
                declared.add(f.name().toLowerCase(Locale.ROOT));
            }
        }
        return declared;
    }

    /**
     * Scores the expected filters, ranges and sort against {@code collected},
     * appending a finding per miss. Returns {@code [expectedCount, matchedCount]}.
     */
    private int[] scoreExpectations(TurNLFacetExpectation expectation, Collected collected,
            List<String> findings) {
        int expectedCount = 0;
        int matchedCount = 0;

        for (ExpectedFilter ef : expectation.filters()) {
            expectedCount++;
            if (filterMatches(ef, collected)) {
                matchedCount++;
            } else {
                findings.add("missing filter %s=%s".formatted(ef.field(), ef.value()));
            }
        }

        for (ExpectedRange er : expectation.ranges()) {
            expectedCount++;
            if (rangeMatches(er, collected)) {
                matchedCount++;
            } else {
                findings.add("missing range on %s".formatted(er.field()));
            }
        }

        ExpectedSort es = expectation.sort();
        if (es != null && es.field() != null) {
            expectedCount++;
            if (sortMatches(es, collected.sorts)) {
                matchedCount++;
            } else {
                findings.add("missing sort %s %s".formatted(es.field(), es.order()));
            }
        }
        return new int[] { expectedCount, matchedCount };
    }

    /** Referenced fields that are neither special nor declared in the schema. */
    private List<String> findUngroundedFields(Collected collected, Set<String> declared) {
        List<String> ungrounded = new ArrayList<>();
        for (String field : collected.referencedFields) {
            String lower = field.toLowerCase(Locale.ROOT);
            if (!SPECIAL_FIELDS.contains(lower) && !declared.contains(lower)) {
                ungrounded.add(field);
            }
        }
        return ungrounded;
    }

    // ─────────────────────────── Matching ───────────────────────────

    private boolean filterMatches(ExpectedFilter ef, Collected collected) {
        String wantField = ef.field();
        String wantValue = norm(ef.value());
        // Term / terms equality (the canonical facet filter).
        List<String> values = collected.termValues.get(wantField);
        if (values != null && values.stream().anyMatch(v -> norm(v).equals(wantValue))) {
            return true;
        }
        // Lenient fallback: a match clause on the field whose text contains the value.
        List<String> matchText = collected.matchText.get(wantField);
        return matchText != null && matchText.stream()
                .anyMatch(t -> norm(t).contains(wantValue) || wantValue.contains(norm(t)));
    }

    private boolean rangeMatches(ExpectedRange er, Collected collected) {
        List<TurDslQuery.Range> ranges = collected.ranges.get(er.field());
        if (ranges == null || ranges.isEmpty()) {
            return false;
        }
        Double wantLower = er.gte() != null ? er.gte() : er.gt();
        Double wantUpper = er.lte() != null ? er.lte() : er.lt();
        for (TurDslQuery.Range r : ranges) {
            Double actualLower = num(r.gte()) != null ? num(r.gte()) : num(r.gt());
            Double actualUpper = num(r.lte()) != null ? num(r.lte()) : num(r.lt());
            boolean lowerOk = wantLower == null || approx(wantLower, actualLower);
            boolean upperOk = wantUpper == null || approx(wantUpper, actualUpper);
            if (lowerOk && upperOk) {
                return true;
            }
        }
        return false;
    }

    private boolean sortMatches(ExpectedSort es, List<SortClause> sorts) {
        for (SortClause sc : sorts) {
            if (sc.field.equalsIgnoreCase(es.field())
                    && (es.order() == null || sc.order.equalsIgnoreCase(es.order()))) {
                return true;
            }
        }
        return false;
    }

    // ─────────────────────────── Collection ───────────────────────────

    /** Flattened view of everything a query body asserts. */
    private static final class Collected {
        final Map<String, List<String>> termValues = new LinkedHashMap<>();
        final Map<String, List<String>> matchText = new LinkedHashMap<>();
        final Map<String, List<TurDslQuery.Range>> ranges = new LinkedHashMap<>();
        final Set<String> referencedFields = new LinkedHashSet<>();
        final List<SortClause> sorts = new ArrayList<>();
    }

    private record SortClause(String field, String order) {
    }

    private Collected collect(TurDslQueryRequest request) {
        Collected c = new Collected();
        walk(request.query(), c);
        walk(request.postFilter(), c);
        collectSort(request.sort(), c);
        return c;
    }

    /** Recursively flatten a query tree into term / range / match references. */
    private void walk(TurDslQuery q, Collected c) {
        if (q == null) {
            return;
        }
        switch (q) {
            case TurDslQuery.Term(String field, Object value) -> addTerm(c, field, String.valueOf(value));
            case TurDslQuery.Terms t when t.values() != null -> {
                for (Object v : t.values()) {
                    addTerm(c, t.field(), String.valueOf(v));
                }
            }
            case TurDslQuery.Range r -> {
                c.referencedFields.add(r.field());
                c.ranges.computeIfAbsent(r.field(), k -> new ArrayList<>()).add(r);
            }
            case TurDslQuery.Match m -> addMatch(c, m.field(), m.query());
            case TurDslQuery.MatchPhrase m -> addMatch(c, m.field(), m.query());
            case TurDslQuery.MatchPhrasePrefix m -> addMatch(c, m.field(), m.query());
            case TurDslQuery.MatchBoolPrefix(String field, String query) -> addMatch(c, field, query);
            case TurDslQuery.Wildcard(String field, String value) -> addMatch(c, field, value);
            case TurDslQuery.Prefix(String field, String value) -> addMatch(c, field, value);
            case TurDslQuery.Fuzzy f -> addMatch(c, f.field(), f.value());
            case TurDslQuery.MultiMatch m when m.fields() != null ->
                m.fields().forEach(f -> addMatch(c, stripBoost(f), m.query()));
            case TurDslQuery.Exists(String field) -> c.referencedFields.add(field);
            case TurDslQuery.Bool b -> {
                walkAll(b.must(), c);
                walkAll(b.should(), c);
                walkAll(b.filter(), c);
                walkAll(b.mustNot(), c);
            }
            case TurDslQuery.ConstantScore cs -> walk(cs.filter(), c);
            case TurDslQuery.FunctionScore fs -> walk(fs.query(), c);
            case TurDslQuery.Nested n -> walk(n.query(), c);
            default -> {
                // match_all, ids, knn, etc. assert no facet/range field — ignore.
            }
        }
    }

    private void walkAll(List<TurDslQuery> qs, Collected c) {
        if (qs != null) {
            qs.forEach(q -> walk(q, c));
        }
    }

    private void addTerm(Collected c, String field, String value) {
        c.referencedFields.add(field);
        c.termValues.computeIfAbsent(field, k -> new ArrayList<>()).add(value);
    }

    private void addMatch(Collected c, String field, String text) {
        c.referencedFields.add(field);
        if (text != null) {
            c.matchText.computeIfAbsent(field, k -> new ArrayList<>()).add(text);
        }
    }

    private void collectSort(List<Object> sort, Collected c) {
        if (sort == null) {
            return;
        }
        for (Object entry : sort) {
            if (entry instanceof String field) {
                c.referencedFields.add(field);
                c.sorts.add(new SortClause(field, "desc"));
            } else if (entry instanceof Map<?, ?> map) {
                map.forEach((key, val) -> {
                    String field = String.valueOf(key);
                    c.referencedFields.add(field);
                    String order = "asc";
                    if (val instanceof String s) {
                        order = s;
                    } else if (val instanceof Map<?, ?> opts && opts.get("order") != null) {
                        order = String.valueOf(opts.get("order"));
                    }
                    c.sorts.add(new SortClause(field, order));
                });
            }
        }
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private static String stripBoost(String field) {
        if (field == null) {
            return "";
        }
        int caret = field.indexOf('^');
        return caret >= 0 ? field.substring(0, caret) : field;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static Double num(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean approx(Double a, Double b) {
        return a != null && b != null && Math.abs(a - b) <= EPSILON;
    }
}

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

import java.util.List;
import java.util.Map;

import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.sn.dsl.TurDslQuery;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T818 / §LIX.1 (Block BK) — the small set of {@link TurDslQueryRequest} bodies
 * and immutable rewrites every copilot planner shares: a bounded
 * {@code match_all}, a free-text fallback, and the sort/size injection lifted out
 * of {@code TurCatalogCopilotService} when planning became a seam.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurCopilotQueryBodies {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private TurCopilotQueryBodies() {
        // static helpers only
    }

    /** A {@code match_all} body bounded to {@code size} rows. */
    public static TurDslQueryRequest matchAll(int size) {
        return OBJECT_MAPPER.readValue(
                "{\"query\":{\"match_all\":{}},\"size\":%d}".formatted(size),
                TurDslQueryRequest.class);
    }

    /**
     * A plain full-text query over the default text field — the fail-open body used
     * when the parser is unavailable, fails, or returns nothing usable. Quotes and
     * backslashes are neutralised so the assembled JSON always parses.
     */
    public static TurDslQueryRequest freeText(String query, int size) {
        String safe = query == null ? "" : query.replace("\"", " ").replace("\\", " ");
        return OBJECT_MAPPER.readValue(
                "{\"query\":{\"match\":{\"%s\":\"%s\"}},\"size\":%d}"
                        .formatted(TurSNFieldName.DEFAULT, safe, size),
                TurDslQueryRequest.class);
    }

    /**
     * A fresh request carrying exactly {@code query} + {@code sort} + {@code size} and
     * nothing else. Built by copying a canned {@code match_all} body rather than by
     * calling the 29-component record constructor literally, so adding a DSL feature
     * can never silently shift an argument here.
     */
    public static TurDslQueryRequest of(TurDslQuery query, List<Object> sort, int size) {
        return copyWith(matchAll(size), query, size, sort);
    }

    /**
     * T811 / §LVII.1 — injects a ranking sort into a parsed request and guarantees a
     * top-k {@code size} (so a sort-only {@code match_all} query returns a bounded
     * ranked page, not an unbounded scan). A {@code sort} the LLM already produced is
     * respected (never overridden); a null query degrades to {@code match_all} so a
     * sort-only request still executes. {@link TurDslQueryRequest} is immutable, so
     * this copies it field-for-field with the two changed slots.
     */
    public static TurDslQueryRequest withSort(TurDslQueryRequest r, String field, String order,
            int defaultSize) {
        TurDslQuery query = r.query() != null ? r.query() : new TurDslQuery.MatchAll();
        List<Object> sort = (r.sort() != null && !r.sort().isEmpty())
                ? r.sort()
                : List.of(Map.of(field, order));
        Integer size = r.size() != null ? r.size() : defaultSize;
        return copyWith(r, query, size, sort);
    }

    /**
     * Guarantees the request carries a bounded {@code size} (and a non-null query),
     * without touching any sort the planner produced. Used by the LLM strategies,
     * whose parse may omit {@code size} entirely.
     */
    public static TurDslQueryRequest withBoundedSize(TurDslQueryRequest r, int defaultSize) {
        TurDslQuery query = r.query() != null ? r.query() : new TurDslQuery.MatchAll();
        Integer size = r.size() != null ? r.size() : defaultSize;
        return copyWith(r, query, size, r.sort());
    }

    /** Field-for-field copy of {@code r} with the query, size and sort replaced. */
    private static TurDslQueryRequest copyWith(TurDslQueryRequest r, TurDslQuery query, Integer size,
            List<Object> sort) {
        return new TurDslQueryRequest(query, r.from(), size, sort, r.source(), r.highlight(),
                r.aggs(), r.postFilter(), r.minScore(), r.searchAfter(), r.collapse(), r.suggest(),
                r.rescore(), r.timeout(), r.explain(), r.scriptFields(), r.indicesBoost(),
                r.trackTotalHits(), r.storedFields(), r.scroll(), r.profile(), r.pit(),
                r.docvalueFields(), r.version(), r.seqNoPrimaryTerm(), r.preference(), r.routing(),
                r.searchType(), r.terminateAfter());
    }
}

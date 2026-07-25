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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.sn.dsl.TurDslQuery;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetField;

/**
 * T819 / §LIX.2 (Block BK) — compiles a {@link TurCopilotPlanRepair} into a valid,
 * <strong>grounded</strong> {@link TurDslQueryRequest}, deterministically.
 *
 * <p>This is the half of the multi-pass planner that is <em>not</em> an LLM: the model
 * only names constraints, and every one of them is checked against the site's declared
 * schema before it reaches the query. A field the model invented is dropped (never
 * passed to the engine), a range on a non-numeric field is dropped, and a sort is only
 * emitted for a declared numeric field with a valid direction. So the block's grounding
 * invariant holds by construction rather than by prompt discipline — and the whole step
 * is unit-testable without an LLM.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurCopilotPlanCompiler {

    private static final String ASC = "asc";
    private static final String DESC = "desc";

    /**
     * Compile {@code repair} into a query body bounded to {@code size} rows, or
     * {@link Optional#empty()} when nothing survived grounding (no clause and no
     * sort) — in which case the caller keeps the plan it already had.
     */
    public Optional<TurDslQueryRequest> compile(TurCopilotPlanRepair repair,
            List<TurNLFacetField> schema, int size) {
        if (repair == null) {
            return Optional.empty();
        }
        Map<String, TurNLFacetField> declared = index(schema);
        List<TurDslQuery> filters = new ArrayList<>();
        collectTermFilters(repair.termFilters(), declared, filters);
        collectRangeFilters(repair.rangeFilters(), declared, filters);
        List<Object> sort = resolveSort(repair, declared);

        if (filters.isEmpty() && !StringUtils.hasText(repair.freeText()) && sort.isEmpty()) {
            return Optional.empty();
        }
        if (StringUtils.hasText(repair.freeText())) {
            filters.add(new TurDslQuery.Match(TurSNFieldName.DEFAULT, repair.freeText().trim(), null));
        }
        TurDslQuery query = filters.isEmpty()
                ? new TurDslQuery.MatchAll()
                : new TurDslQuery.Bool(null, null, null, List.copyOf(filters), null);
        return Optional.of(TurCopilotQueryBodies.of(query,
                sort.isEmpty() ? null : List.copyOf(sort), size));
    }

    /** Declared fields by name; an absent/blank name is skipped. */
    private static Map<String, TurNLFacetField> index(List<TurNLFacetField> schema) {
        Map<String, TurNLFacetField> declared = new LinkedHashMap<>();
        if (schema != null) {
            for (TurNLFacetField field : schema) {
                if (field != null && StringUtils.hasText(field.name())) {
                    declared.put(field.name(), field);
                }
            }
        }
        return declared;
    }

    /** Term clauses for grounded fields with a non-blank value; the rest are dropped. */
    private void collectTermFilters(List<TurCopilotPlanRepair.TermFilter> terms,
            Map<String, TurNLFacetField> declared, List<TurDslQuery> filters) {
        if (terms == null) {
            return;
        }
        for (TurCopilotPlanRepair.TermFilter term : terms) {
            if (term == null || !declared.containsKey(term.field())
                    || !StringUtils.hasText(term.value())) {
                continue;
            }
            filters.add(new TurDslQuery.Term(term.field(), term.value().trim()));
        }
    }

    /**
     * Range clauses for grounded <em>numeric</em> fields carrying at least one bound.
     * A range on a text field would silently match nothing in the engine, so it is
     * dropped here instead.
     */
    private void collectRangeFilters(List<TurCopilotPlanRepair.RangeFilter> ranges,
            Map<String, TurNLFacetField> declared, List<TurDslQuery> filters) {
        if (ranges == null) {
            return;
        }
        for (TurCopilotPlanRepair.RangeFilter range : ranges) {
            if (range == null || (range.gte() == null && range.lte() == null)) {
                continue;
            }
            TurNLFacetField field = declared.get(range.field());
            if (field == null || !field.numeric()) {
                continue;
            }
            filters.add(new TurDslQuery.Range(range.field(), range.gte(), null, range.lte(), null));
        }
    }

    /**
     * The sort clause, emitted only for a declared numeric field with an {@code asc}
     * / {@code desc} direction. Anything else yields no sort — the copilot would
     * rather return relevance order than sort on a field that cannot be ordered.
     */
    private List<Object> resolveSort(TurCopilotPlanRepair repair,
            Map<String, TurNLFacetField> declared) {
        TurNLFacetField field = declared.get(repair.sortField());
        if (field == null || !field.numeric()) {
            return List.of();
        }
        String order = repair.sortOrder() == null ? "" : repair.sortOrder().trim().toLowerCase(Locale.ROOT);
        if (!ASC.equals(order) && !DESC.equals(order)) {
            return List.of();
        }
        return List.of(Map.of(field.name(), order));
    }
}

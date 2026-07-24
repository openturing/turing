/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedSetSortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Unit coverage for the type-aware range and sort translation in
 * {@link TurDslLuceneExecutor} (the fix surfaced by T407). Numeric / currency /
 * date fields are indexed as Lucene {@code *Point} + {@code NumericDocValues}, so
 * a range must become a point range (a lexicographic {@code TermRangeQuery} matches
 * nothing) and a sort must use a typed {@link SortField} (a
 * {@link SortedSetSortField} throws on a numeric-doc-values field). String/text and
 * unknown fields keep the lexicographic / sorted-set behavior.
 *
 * <p>The translation helpers don't touch the instance collaborators, so they're
 * exercised directly with a {@code null}-wired executor and an explicit field-type
 * map — no Lucene index, no Spring context.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurDslLuceneExecutorTest {

    private final TurDslLuceneExecutor executor = new TurDslLuceneExecutor(null, null, null);

    // ─────────────── Range translation ───────────────

    @Test
    void doubleAndCurrencyRangesBecomePointRangesNotStringRanges() {
        var range = new TurDslQuery.Range("tuition", null, null, 20000, null);

        var asCurrency = executor.translateQuery(range, Map.of("tuition", TurSEFieldType.CURRENCY));
        var asDouble = executor.translateQuery(range, Map.of("tuition", TurSEFieldType.DOUBLE));

        assertThat(asCurrency).isNotInstanceOf(TermRangeQuery.class);
        assertThat(asDouble).isNotInstanceOf(TermRangeQuery.class);
        // PointRangeQuery#toString renders the field and the (inclusive) bounds.
        assertThat(asCurrency.toString()).contains("tuition").contains("20000.0");
    }

    @Test
    void intRangeHonorsExclusiveAndInclusiveBounds() {
        // gte 12, lte 24 (inclusive) vs gt 12, lt 24 (exclusive → 13..23).
        var inclusive = executor.translateQuery(
                new TurDslQuery.Range("durationMonths", 12, null, 24, null),
                Map.of("durationMonths", TurSEFieldType.INT));
        var exclusive = executor.translateQuery(
                new TurDslQuery.Range("durationMonths", null, 12, null, 24),
                Map.of("durationMonths", TurSEFieldType.INT));

        assertThat(inclusive).isNotInstanceOf(TermRangeQuery.class);
        assertThat(inclusive.toString()).contains("durationMonths").contains("12").contains("24");
        assertThat(exclusive.toString()).contains("13").contains("23");
    }

    @Test
    void stringAndUnknownRangesStayLexicographicTermRanges() {
        var range = new TurDslQuery.Range("degree", "a", null, "m", null);

        assertThat(executor.translateQuery(range, Map.of("degree", TurSEFieldType.STRING)))
                .isInstanceOf(TermRangeQuery.class);
        // No declared type → conservative string-range fallback (interface entry point).
        assertThat(executor.translateQuery(range, Map.of()))
                .isInstanceOf(TermRangeQuery.class);
        assertThat(executor.translateQuery(range)).isInstanceOf(TermRangeQuery.class);
    }

    // ─────────────── Sort translation ───────────────

    @Test
    void numericSortUsesTypedSortFieldNotSortedSet() {
        SortField tuition = sortField(TurSEFieldType.CURRENCY, "tuition");
        assertThat(tuition).isNotInstanceOf(SortedSetSortField.class);
        assertThat(tuition.getType()).isEqualTo(SortField.Type.DOUBLE);

        assertThat(sortField(TurSEFieldType.INT, "durationMonths").getType())
                .isEqualTo(SortField.Type.INT);
        assertThat(sortField(TurSEFieldType.DATE, "publishedAt").getType())
                .isEqualTo(SortField.Type.LONG);
    }

    @Test
    void stringAndUnknownSortUseSortedSetSortField() {
        assertThat(sortField(TurSEFieldType.STRING, "degree")).isInstanceOf(SortedSetSortField.class);
        // No declared type → SortedSet fallback (interface entry point).
        Sort fallback = executor.buildSort(List.of(Map.of("degree", "asc")), Map.of());
        assertThat(fallback.getSort()[0]).isInstanceOf(SortedSetSortField.class);
    }

    @Test
    void sortDirectionFollowsOrder() {
        SortField asc = sortField(TurSEFieldType.CURRENCY, "tuition", "asc");
        SortField desc = sortField(TurSEFieldType.CURRENCY, "tuition", "desc");
        assertThat(asc.getReverse()).isFalse();
        assertThat(desc.getReverse()).isTrue();
    }

    // ─────────────── T802: case-insensitive STRING facet term matching ───────────────

    @Test
    void stringTermIsCaseInsensitiveButIdStaysExact() {
        // STRING facet fields translate to a case-insensitive RegexpQuery; id keeps
        // its exact TermQuery so unique-id lookups are unaffected.
        assertThat(executor.translateQuery(new TurDslQuery.Term("kind", "embedding"),
                Map.of("kind", TurSEFieldType.STRING))).isInstanceOf(RegexpQuery.class);
        assertThat(executor.translateQuery(new TurDslQuery.Term("id", "abc"),
                Map.of("id", TurSEFieldType.STRING))).isInstanceOf(TermQuery.class);
        // No declared type → conservative exact TermQuery (interface entry point).
        assertThat(executor.translateQuery(new TurDslQuery.Term("kind", "embedding")))
                .isInstanceOf(TermQuery.class);
    }

    @Test
    void caseInsensitiveStringTermActuallyMatchesUppercaseIndexedValue() throws IOException {
        // The real bug: the planner emits kind=embedding but the value is indexed as
        // EMBEDDING. Prove the translated query matches against a live index.
        try (Directory dir = new ByteBuffersDirectory()) {
            try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
                writer.addDocument(docOf("m1", "EMBEDDING", "openai"));
                writer.addDocument(docOf("m2", "CHAT", "openai"));
            }
            try (DirectoryReader reader = DirectoryReader.open(dir)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                Map<String, TurSEFieldType> types = Map.of(
                        "kind", TurSEFieldType.STRING, "vendor", TurSEFieldType.STRING);

                // lowercase query → matches the uppercase-indexed enum value.
                Query lower = executor.translateQuery(new TurDslQuery.Term("kind", "embedding"), types);
                assertThat(searcher.count(lower)).isEqualTo(1);
                // uppercase query against lowercase-indexed data also matches.
                Query upper = executor.translateQuery(new TurDslQuery.Term("vendor", "OPENAI"), types);
                assertThat(searcher.count(upper)).isEqualTo(2);
                // a terms filter is case-insensitive per value too.
                Query terms = executor.translateQuery(
                        new TurDslQuery.Terms("kind", List.of("embedding", "chat")), types);
                assertThat(searcher.count(terms)).isEqualTo(2);
            }
        }
    }

    private static Document docOf(String id, String kind, String vendor) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new StringField("kind", kind, Field.Store.YES));
        doc.add(new StringField("vendor", vendor, Field.Store.YES));
        return doc;
    }

    private SortField sortField(TurSEFieldType type, String field) {
        return sortField(type, field, "asc");
    }

    private SortField sortField(TurSEFieldType type, String field, String order) {
        Sort sort = executor.buildSort(List.of(Map.of(field, order)), Map.of(field, type));
        return sort.getSort()[0];
    }
}

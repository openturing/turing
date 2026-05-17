/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the dedup-and-fallback helpers in {@link TurCustomToolSearchHelper}.
 * No Spring context, no vector store — exercises the pure-function bits that
 * decide which chunks survive into the final result.
 *
 * <p>Three concerns covered:
 * <ul>
 *   <li>{@link TurCustomToolSearchHelper#resolveDedupKeys(Object)} — the API
 *       for {@code dedupBy: ...} parameter on Custom Tool scripts.</li>
 *   <li>{@link TurCustomToolSearchHelper#firstNonBlank(Map, List)} — picks
 *       a document key out of a chunk's metadata using a priority list.</li>
 *   <li>{@link TurCustomToolSearchHelper#collapseToTopKDocuments(List, int, List)}
 *       — collapses chunks to unique docs, preserving order, capped at limit.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
class TurCustomToolSearchHelperTest {

    private static final List<String> DEFAULT_KEYS = List.of("source_id", "url", "title", "id");

    // ─────────────────────────── resolveDedupKeys ───────────────────────────

    @Test
    void resolveDedupKeys_nullReturnsDefaults() {
        assertThat(TurCustomToolSearchHelper.resolveDedupKeys(null))
                .as("null falls back to the default priority list")
                .isEqualTo(DEFAULT_KEYS);
    }

    @Test
    void resolveDedupKeys_falseDisablesDedup() {
        assertThat(TurCustomToolSearchHelper.resolveDedupKeys(Boolean.FALSE))
                .as("Boolean.FALSE explicitly disables dedup")
                .isEmpty();
    }

    @Test
    void resolveDedupKeys_noneStringDisablesDedup() {
        assertThat(TurCustomToolSearchHelper.resolveDedupKeys("none"))
                .as("the literal 'none' string disables dedup")
                .isEmpty();
        assertThat(TurCustomToolSearchHelper.resolveDedupKeys("NONE"))
                .as("'none' is case-insensitive")
                .isEmpty();
    }

    @Test
    void resolveDedupKeys_blankStringDisablesDedup() {
        assertThat(TurCustomToolSearchHelper.resolveDedupKeys(""))
                .as("empty string disables dedup")
                .isEmpty();
        assertThat(TurCustomToolSearchHelper.resolveDedupKeys("   "))
                .as("whitespace-only string disables dedup")
                .isEmpty();
    }

    @Test
    void resolveDedupKeys_singleStringPrependsToDefaults() {
        // Custom field first, then the defaults — script's preferred key wins
        // when present, but missing values still find a fallback.
        List<String> resolved = TurCustomToolSearchHelper.resolveDedupKeys("url");
        assertThat(resolved.get(0)).isEqualTo("url");
        assertThat(resolved).containsExactly("url", "source_id", "title", "id");
    }

    @Test
    void resolveDedupKeys_customKeyAlreadyInDefaultsIsNotDuplicated() {
        List<String> resolved = TurCustomToolSearchHelper.resolveDedupKeys("source_id");
        assertThat(resolved)
                .as("'source_id' was already first in defaults — no duplicates")
                .containsExactly("source_id", "url", "title", "id");
    }

    @Test
    void resolveDedupKeys_listReturnsListPreservingOrder() {
        List<String> resolved = TurCustomToolSearchHelper.resolveDedupKeys(
                List.of("docKey", "fallback"));
        assertThat(resolved).containsExactly("docKey", "fallback");
    }

    @Test
    void resolveDedupKeys_listDeduplicatesCaseInsensitive() {
        List<String> resolved = TurCustomToolSearchHelper.resolveDedupKeys(
                List.of("DocKey", "dockey", "DOCKEY", "other"));
        assertThat(resolved)
                .as("case-only differences collapse to the first occurrence")
                .containsExactly("DocKey", "other");
    }

    @Test
    void resolveDedupKeys_emptyListFallsBackToDefaults() {
        assertThat(TurCustomToolSearchHelper.resolveDedupKeys(List.of()))
                .as("empty list is treated like null — use defaults")
                .isEqualTo(DEFAULT_KEYS);
    }

    // ─────────────────────────── firstNonBlank ───────────────────────────

    @Test
    void firstNonBlank_returnsFirstMatchingKey() {
        Map<String, Object> hit = Map.of("source_id", "doc-42", "url", "x");
        assertThat(TurCustomToolSearchHelper.firstNonBlank(hit, DEFAULT_KEYS))
                .isEqualTo("doc-42");
    }

    @Test
    void firstNonBlank_skipsNullAndBlankToNextKey() {
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("source_id", null);
        hit.put("url", "   ");
        hit.put("title", "  CFO Program  ");
        assertThat(TurCustomToolSearchHelper.firstNonBlank(hit, DEFAULT_KEYS))
                .as("null + whitespace are skipped; trimmed value is returned")
                .isEqualTo("CFO Program");
    }

    @Test
    void firstNonBlank_returnsNullWhenAllKeysMissing() {
        Map<String, Object> hit = Map.of("unrelated", "value");
        assertThat(TurCustomToolSearchHelper.firstNonBlank(hit, DEFAULT_KEYS))
                .as("no priority key found — caller should skip this hit")
                .isNull();
    }

    @Test
    void firstNonBlank_honorsCustomKeyOrder() {
        Map<String, Object> hit = Map.of("source_id", "src", "url", "u");
        // url-first list should pick url even though source_id is also present
        assertThat(TurCustomToolSearchHelper.firstNonBlank(hit, List.of("url", "source_id")))
                .isEqualTo("u");
    }

    // ─────────────────────────── collapseToTopKDocuments ───────────────────────────

    @Test
    void collapseToTopKDocuments_dropsDuplicateSourceIdsKeepingFirstByOrder() {
        List<Map<String, Object>> hits = List.of(
                hit("doc-1", "Chunk 1 of doc-1"),
                hit("doc-1", "Chunk 2 of doc-1"),
                hit("doc-2", "Only chunk of doc-2"),
                hit("doc-1", "Chunk 3 of doc-1"),
                hit("doc-3", "Only chunk of doc-3"));

        List<Map<String, Object>> result = TurCustomToolSearchHelper
                .collapseToTopKDocuments(hits, 3, DEFAULT_KEYS);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(h -> h.get("source_id"))
                .as("first chunk per source_id wins; order preserved")
                .containsExactly("doc-1", "doc-2", "doc-3");
        assertThat(result.get(0))
                .as("the FIRST chunk of doc-1 represents the document (highest similarity)")
                .containsEntry("content", "Chunk 1 of doc-1");
    }

    @Test
    void collapseToTopKDocuments_stopsAtLimit() {
        List<Map<String, Object>> hits = List.of(
                hit("doc-1", "x"),
                hit("doc-2", "x"),
                hit("doc-3", "x"),
                hit("doc-4", "x"));

        List<Map<String, Object>> result = TurCustomToolSearchHelper
                .collapseToTopKDocuments(hits, 2, DEFAULT_KEYS);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(h -> h.get("source_id"))
                .containsExactly("doc-1", "doc-2");
    }

    @Test
    void collapseToTopKDocuments_returnsAllUniqueWhenFewerThanLimit() {
        List<Map<String, Object>> hits = List.of(
                hit("doc-1", "x"),
                hit("doc-1", "y"));

        List<Map<String, Object>> result = TurCustomToolSearchHelper
                .collapseToTopKDocuments(hits, 5, DEFAULT_KEYS);

        assertThat(result)
                .as("limit is a cap, not a quota; only 1 unique doc → 1 hit")
                .hasSize(1);
    }

    @Test
    void collapseToTopKDocuments_skipsHitsWithoutKeyableMetadata() {
        Map<String, Object> orphan = Map.of("content", "no identifying metadata here");
        List<Map<String, Object>> hits = List.of(
                orphan,
                hit("doc-1", "real"),
                orphan,
                hit("doc-2", "real"));

        List<Map<String, Object>> result = TurCustomToolSearchHelper
                .collapseToTopKDocuments(hits, 5, DEFAULT_KEYS);

        assertThat(result)
                .as("hits without any dedup key are skipped — they can't be grouped safely")
                .hasSize(2);
    }

    @Test
    void collapseToTopKDocuments_emptyInputReturnsEmpty() {
        assertThat(TurCustomToolSearchHelper.collapseToTopKDocuments(List.of(), 5, DEFAULT_KEYS))
                .isEmpty();
    }

    @Test
    void collapseToTopKDocuments_zeroLimitReturnsEmpty() {
        assertThat(TurCustomToolSearchHelper.collapseToTopKDocuments(
                List.of(hit("doc-1", "x")), 0, DEFAULT_KEYS))
                .as("limit=0 means caller wants nothing back")
                .isEmpty();
    }

    @Test
    void collapseToTopKDocuments_fallsBackToUrlWhenSourceIdMissing() {
        List<Map<String, Object>> hits = List.of(
                Map.of("url", "https://x.example/a", "content", "chunk 1 of a"),
                Map.of("url", "https://x.example/b", "content", "chunk 1 of b"),
                Map.of("url", "https://x.example/a", "content", "chunk 2 of a"));

        List<Map<String, Object>> result = TurCustomToolSearchHelper
                .collapseToTopKDocuments(hits, 5, DEFAULT_KEYS);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(h -> h.get("url"))
                .containsExactly("https://x.example/a", "https://x.example/b");
    }

    @Test
    void collapseToTopKDocuments_emptyDedupKeysReturnsEmpty() {
        // Empty key list = nothing to group by → every hit is a no-key skip.
        List<Map<String, Object>> hits = List.of(hit("doc-1", "x"), hit("doc-2", "y"));
        assertThat(TurCustomToolSearchHelper.collapseToTopKDocuments(hits, 5, List.of()))
                .as("no keys means no way to identify docs — skip everything")
                .isEmpty();
    }

    /**
     * Shorthand for building a hit map with {@code source_id} + {@code content}.
     * The dedup logic also reads {@code id}, {@code score}, but we don't need
     * those here.
     */
    private static Map<String, Object> hit(String sourceId, String content) {
        return Map.ofEntries(
                entry("source_id", sourceId),
                entry("content", content));
    }

    // ─────────────────────────── toSnHitList ───────────────────────────

    @Test
    void toSnHitList_emptyOrNullReturnsEmpty() {
        assertThat(TurCustomToolSearchHelper.toSnHitList(null)).isEmpty();
        assertThat(TurCustomToolSearchHelper.toSnHitList(List.of())).isEmpty();
    }

    @Test
    void toSnHitList_flattensFieldsAndKeepsSourceAndElevate() {
        var doc = new com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean();
        doc.setSource("solr");
        doc.setElevate(true);
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("id", "doc-42");
        fields.put("title", "MBA Executivo");
        fields.put("preco", 49_900);
        doc.setFields(fields);

        List<Map<String, Object>> hits = TurCustomToolSearchHelper.toSnHitList(List.of(doc));

        assertThat(hits).hasSize(1);
        Map<String, Object> hit = hits.get(0);
        // Field flattening: script can write `hit.title` / `hit.preco` directly.
        assertThat(hit)
                .containsEntry("title", "MBA Executivo")
                .containsEntry("preco", 49_900)
                .containsEntry("id", "doc-42")
                .containsEntry("source", "solr")
                .containsEntry("elevate", true);
    }

    @Test
    void toSnHitList_idAliasResolvesFromFieldsMap() {
        // When the stored {@code id} field is present, the helper aliases it
        // to the top-level "id" key so {@code hit.id} works identically to
        // {@code hit.fields.id} — matches ann() behavior.
        var doc = new com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean();
        doc.setSource("solr");
        doc.setElevate(false);
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("id", "doc-7");
        fields.put("title", "Pricing Strategy");
        doc.setFields(fields);

        List<Map<String, Object>> hits = TurCustomToolSearchHelper.toSnHitList(List.of(doc));

        assertThat(hits.get(0)).containsEntry("id", "doc-7");
    }

    @Test
    void toSnHitList_missingFieldsDoesNotThrow() {
        var doc = new com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean();
        doc.setSource("elastic");
        doc.setElevate(false);
        // fields == null → hit gets only the reserved keys; no NPE.

        List<Map<String, Object>> hits = TurCustomToolSearchHelper.toSnHitList(List.of(doc));

        assertThat(hits).hasSize(1);
        Map<String, Object> hit = hits.get(0);
        assertThat(hit)
                .containsEntry("source", "elastic")
                .containsEntry("elevate", false)
                .doesNotContainKey("id");
    }

    @Test
    void toSnHitList_reservedKeysWinOverCollidingFieldName() {
        // A stored field named "source" would collide with the reserved
        // top-level "source" we add. Contract: reserved key wins (matches
        // ann()'s same-named conflict rule).
        var doc = new com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean();
        doc.setSource("solr");
        doc.setElevate(false);
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("source", "should-be-overwritten");
        fields.put("title", "Test");
        doc.setFields(fields);

        List<Map<String, Object>> hits = TurCustomToolSearchHelper.toSnHitList(List.of(doc));

        assertThat(hits.get(0).get("source"))
                .as("reserved 'source' wins over a stored field with the same name")
                .isEqualTo("solr");
    }
}

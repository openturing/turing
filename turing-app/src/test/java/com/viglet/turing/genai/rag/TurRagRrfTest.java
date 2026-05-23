/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import com.viglet.turing.genai.provider.store.lucene.TurLuceneVectorStore;

/**
 * Pins {@link TurRagRrf#fuse} semantics shared between the embedded Lucene
 * hybrid path (T24) and the SE_INSTANCE hybrid path (T24b). The fuser is
 * pure — no mocks, just list math + metadata bookkeeping.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
class TurRagRrfTest {

    private static Document doc(String id, String text, Map<String, Object> metadata) {
        return Document.builder().id(id).text(text).metadata(metadata == null ? Map.of() : metadata).build();
    }

    @Test
    void fuseEmptyListsReturnsEmpty() {
        assertThat(TurRagRrf.fuse(List.of(), List.of(), 10)).isEmpty();
        assertThat(TurRagRrf.fuse(null, null, 10)).isEmpty();
    }

    @Test
    void fuseZeroTopKReturnsEmpty() {
        List<Document> vec = List.of(doc("a", "A", null));
        List<Document> bm = List.of(doc("b", "B", null));
        assertThat(TurRagRrf.fuse(vec, bm, 0)).isEmpty();
        assertThat(TurRagRrf.fuse(vec, bm, -3)).isEmpty();
    }

    @Test
    void fuseUnionsDistinctDocs() {
        List<Document> vec = List.of(doc("v1", "V1", null), doc("v2", "V2", null));
        List<Document> bm = List.of(doc("b1", "B1", null));
        List<Document> fused = TurRagRrf.fuse(vec, bm, 10);
        assertThat(fused).hasSize(3);
        assertThat(fused).extracting(Document::getId)
                .containsExactlyInAnyOrder("v1", "v2", "b1");
    }

    @Test
    void fuseRanksSharedDocsHigher() {
        // A doc that appears in BOTH lists must score higher than docs
        // that appear in only one (its RRF score is the sum of both ranks).
        Document shared = doc("shared", "shared", null);
        Document vecOnly = doc("v-only", "vector exclusive", null);
        Document bmOnly = doc("b-only", "bm25 exclusive", null);

        List<Document> vec = List.of(shared, vecOnly);
        List<Document> bm = List.of(shared, bmOnly);

        List<Document> fused = TurRagRrf.fuse(vec, bm, 10);
        assertThat(fused).extracting(Document::getId).first().isEqualTo("shared");
        assertThat(fused.get(0).getScore()).isGreaterThan(fused.get(1).getScore());
    }

    @Test
    void fuseLimitsToTopK() {
        List<Document> vec = List.of(
                doc("a", "A", null), doc("b", "B", null), doc("c", "C", null));
        List<Document> bm = List.of(
                doc("d", "D", null), doc("e", "E", null), doc("f", "F", null));
        List<Document> fused = TurRagRrf.fuse(vec, bm, 2);
        assertThat(fused).hasSize(2);
    }

    @Test
    void fuseStripsKeywordOnlyTagWhenDocAppearsInVectorList() {
        // The BM25 indexer / SE adapter pre-tags every BM25 hit with the
        // keyword-only-fallback marker. RRF must strip that tag from any
        // doc the vector pass ALSO returned, since vector confirmed the
        // match — the prompt builder shouldn't soft-warn the LLM about it.
        Document vec = doc("shared", "vector",
                Map.of("source", "vector"));
        Document bm = doc("shared", "bm25",
                Map.of("source", "bm25",
                        TurLuceneVectorStore.METADATA_KEYWORD_ONLY_FALLBACK, Boolean.TRUE));

        List<Document> fused = TurRagRrf.fuse(List.of(vec), List.of(bm), 5);
        assertThat(fused).hasSize(1);
        assertThat(fused.get(0).getMetadata())
                .doesNotContainKey(TurLuceneVectorStore.METADATA_KEYWORD_ONLY_FALLBACK);
    }

    @Test
    void fuseKeepsKeywordOnlyTagOnBm25ExclusiveHit() {
        Document vec = doc("vec-only", "vector", null);
        Document bm = doc("bm-only", "bm25",
                Map.of(TurLuceneVectorStore.METADATA_KEYWORD_ONLY_FALLBACK, Boolean.TRUE));

        List<Document> fused = TurRagRrf.fuse(List.of(vec), List.of(bm), 5);
        Document bmFused = fused.stream().filter(d -> "bm-only".equals(d.getId())).findFirst().orElseThrow();
        assertThat(bmFused.getMetadata())
                .containsEntry(TurLuceneVectorStore.METADATA_KEYWORD_ONLY_FALLBACK, Boolean.TRUE);
    }

    @Test
    void fuseIgnoresDocsWithoutId() {
        // Defensive: nulls and id-less docs are dropped without raising —
        // partial indexer failures (e.g. SE returned a hit with no id field)
        // shouldn't take the whole fuse down.
        Document withId = doc("ok", "OK", null);
        // `Document.builder()` requires an id, so emulate the "id missing"
        // case by passing a doc constructed via the public ctor that
        // accepts a null id internally. We approximate by using a List
        // that contains nulls — TurRagRrf.fuse must skip both.
        java.util.List<Document> vec = new java.util.ArrayList<>();
        vec.add(withId);
        vec.add(null);

        List<Document> fused = TurRagRrf.fuse(vec, List.of(), 5);
        assertThat(fused).hasSize(1).first().extracting(Document::getId).isEqualTo("ok");
    }

    @Test
    void fuseRrfScoreFormulaMatchesPaper() {
        // For a single doc at rank-1 in both lists: score = 2 * (1 / (K+1))
        // with K=60 → 2/61 ≈ 0.03278688... Use a tolerance because doubles.
        Document only = doc("x", "x", null);
        List<Document> fused = TurRagRrf.fuse(List.of(only), List.of(only), 5);
        assertThat(fused).hasSize(1);
        assertThat(fused.get(0).getScore()).isCloseTo(2.0 / 61.0, org.assertj.core.data.Offset.offset(1e-9));
    }
}

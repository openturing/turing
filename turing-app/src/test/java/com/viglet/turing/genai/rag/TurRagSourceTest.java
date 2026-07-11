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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import com.viglet.turing.genai.TurSNGenAi;
import com.viglet.turing.genai.provider.store.lucene.TurLuceneVectorStore;

/**
 * T292 — unit coverage for {@link TurRagSource#fromDocument}, the projection
 * that powers the {@code sources[]} SSE event for both the
 * {@code search_knowledge_base} tool path and the public SN chat path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurRagSourceTest {

    @Test
    void extractsSnContentMetadata() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(TurSNGenAi.SOURCE_ID, "doc-42");
        meta.put("title", "Refund Policy");
        meta.put("url", "https://example.com/refunds");
        meta.put("chunkIndex", 3);
        Document doc = Document.builder().id("chunk-1").text("body").metadata(meta).score(0.87).build();

        TurRagSource source = TurRagSource.fromDocument(doc);

        assertThat(source.sourceId()).isEqualTo("doc-42");
        assertThat(source.title()).isEqualTo("Refund Policy");
        assertThat(source.url()).isEqualTo("https://example.com/refunds");
        assertThat(source.chunkIndex()).isEqualTo(3);
        assertThat(source.score()).isEqualTo(0.87);
        assertThat(source.keywordOnly()).isFalse();
    }

    @Test
    void extractsAssetMetadataWithDownloadUrl() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("objectName", "handbook/policies.pdf");
        meta.put("contentType", "application/pdf");
        Document doc = Document.builder().id("chunk-9").text("body").metadata(meta).score(0.42).build();

        TurRagSource source = TurRagSource.fromDocument(doc);

        assertThat(source.sourceId()).isEqualTo("handbook/policies.pdf");
        assertThat(source.title()).isEqualTo("handbook/policies.pdf");
        assertThat(source.url()).isEqualTo(
                "/api/asset/download?objectName=handbook%2Fpolicies.pdf");
        assertThat(source.chunkIndex()).isNull();
    }

    @Test
    void flagsKeywordOnlyFallbackChunks() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(TurSNGenAi.SOURCE_ID, "doc-7");
        meta.put(TurLuceneVectorStore.METADATA_KEYWORD_ONLY_FALLBACK, Boolean.TRUE);
        Document doc = Document.builder().id("chunk-3").text("body").metadata(meta).score(1.5).build();

        TurRagSource source = TurRagSource.fromDocument(doc);

        assertThat(source.keywordOnly()).isTrue();
        assertThat(source.sourceId()).isEqualTo("doc-7");
    }

    @Test
    void fallsBackToDocumentIdWhenNoSourceMetadata() {
        Document doc = Document.builder().id("bare-chunk").text("body")
                .metadata(new LinkedHashMap<>()).score(0.1).build();

        TurRagSource source = TurRagSource.fromDocument(doc);

        assertThat(source.sourceId()).isEqualTo("bare-chunk");
        assertThat(source.title()).isEqualTo("bare-chunk");
        assertThat(source.url()).isEmpty();
    }

    @Test
    void dedupeByDocumentUnifiesChunksOfTheSameUrlKeepingBestChunk() {
        // Three chunks: two from the same doc (same url), one from another.
        List<TurRagSource> raw = List.of(
                new TurRagSource("doc-1", "Guide", "https://ex.com/guide", 0, 0.90, false),
                new TurRagSource("doc-2", "FAQ", "https://ex.com/faq", 0, 0.70, false),
                new TurRagSource("doc-1", "Guide", "https://ex.com/guide", 5, 0.95, false));

        List<TurRagSource> deduped = TurRagSource.dedupeByDocument(raw);

        // One entry per url, first-seen ranking order preserved (guide before faq).
        assertThat(deduped).extracting(TurRagSource::url)
                .containsExactly("https://ex.com/guide", "https://ex.com/faq");
        // The higher-scoring chunk of the guide (0.95, chunkIndex 5) wins.
        assertThat(deduped.get(0).score()).isEqualTo(0.95);
        assertThat(deduped.get(0).chunkIndex()).isEqualTo(5);
    }

    @Test
    void dedupeByDocumentFallsBackToSourceIdWhenUrlBlank() {
        List<TurRagSource> raw = List.of(
                new TurRagSource("doc-1", "A", "", 0, 0.5, false),
                new TurRagSource("doc-1", "A", "", 1, 0.8, false));

        List<TurRagSource> deduped = TurRagSource.dedupeByDocument(raw);

        assertThat(deduped).hasSize(1);
        assertThat(deduped.get(0).sourceId()).isEqualTo("doc-1");
        assertThat(deduped.get(0).score()).isEqualTo(0.8);
    }

    @Test
    void dedupeByDocumentKeepsAnonymousChunksDistinct() {
        // No url and no sourceId on either chunk — must NOT collapse into one.
        List<TurRagSource> raw = List.of(
                new TurRagSource("", "", "", null, 0.4, false),
                new TurRagSource("", "", "", null, 0.6, false));

        assertThat(TurRagSource.dedupeByDocument(raw)).hasSize(2);
    }

    @Test
    void dedupeByDocumentHandlesNullAndSingletonWithoutCopying() {
        assertThat(TurRagSource.dedupeByDocument(null)).isEmpty();
        List<TurRagSource> single = List.of(new TurRagSource("d", "D", "u", 0, 0.1, false));
        assertThat(TurRagSource.dedupeByDocument(single)).isSameAs(single);
    }

    @Test
    void collectorSnapshotIsImmutableAndOrdered() {
        TurRagSourceCollector collector = new TurRagSourceCollector();
        assertThat(collector.isEmpty()).isTrue();
        collector.add(new TurRagSource("a", "A", null, null, 0.9, false));
        collector.add(new TurRagSource("b", "B", null, null, 0.5, true));
        collector.add(null); // ignored

        assertThat(collector.isEmpty()).isFalse();
        assertThat(collector.snapshot()).extracting(TurRagSource::sourceId)
                .containsExactly("a", "b");
    }
}

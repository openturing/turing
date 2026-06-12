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

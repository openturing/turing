/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.viglet.turing.genai.provider.store.lucene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;

class TurLuceneQuantizationTest {

    @Test
    void fromOption_parsesKnownModesCaseInsensitive() {
        assertEquals(TurLuceneQuantization.INT8, TurLuceneQuantization.fromOption("int8"));
        assertEquals(TurLuceneQuantization.INT4, TurLuceneQuantization.fromOption("INT4"));
        assertEquals(TurLuceneQuantization.BINARY, TurLuceneQuantization.fromOption(" binary "));
        assertEquals(TurLuceneQuantization.SEVEN_BIT, TurLuceneQuantization.fromOption("seven_bit"));
    }

    @Test
    void fromOption_defaultsToNoneForBlankOrUnknown() {
        assertEquals(TurLuceneQuantization.NONE, TurLuceneQuantization.fromOption(null));
        assertEquals(TurLuceneQuantization.NONE, TurLuceneQuantization.fromOption(""));
        assertEquals(TurLuceneQuantization.NONE, TurLuceneQuantization.fromOption("garbage"));
    }

    @Test
    void buildCodec_emptyForNone_presentForQuantized() {
        assertTrue(TurLuceneQuantization.NONE.buildCodec().isEmpty());
        assertTrue(TurLuceneQuantization.INT8.buildCodec().isPresent());
        assertTrue(TurLuceneQuantization.BINARY.buildCodec().isPresent());
    }

    @Test
    void int8QuantizedIndex_roundTripsAddAndSearch(@TempDir Path tmp) throws IOException {
        TurLuceneVectorIndex index = new TurLuceneVectorIndex(tmp.resolve("col"), TurLuceneQuantization.INT8);
        try {
            TurLuceneVectorStore store = new TurLuceneVectorStore(index, new StubEmbeddingModel(8), "col");
            store.add(List.of(
                    Document.builder().id("a").text("alpha").metadata(Map.of()).build(),
                    Document.builder().id("b").text("bravo").metadata(Map.of()).build()));

            List<Document> hits = store.similaritySearch(
                    SearchRequest.builder().query("alpha").topK(1).similarityThreshold(0.0).build());

            assertFalse(hits.isEmpty());
            assertEquals("a", hits.get(0).getId());
        } finally {
            index.close();
        }
    }

    @Test
    void int8QuantizedIndex_handlesHighDimensionVectorsAboveTheDefaultCap(@TempDir Path tmp) throws IOException {
        // The scalar-quantized format advertises a 1024-dim cap; the per-field
        // wrapper raises it so 1536/3072-dim models still index. Use 1536.
        TurLuceneVectorIndex index = new TurLuceneVectorIndex(tmp.resolve("hi"), TurLuceneQuantization.INT8);
        try {
            TurLuceneVectorStore store = new TurLuceneVectorStore(index, new StubEmbeddingModel(1536), "hi");
            store.add(List.of(Document.builder().id("a").text("alpha").metadata(Map.of()).build()));
            List<Document> hits = store.similaritySearch(
                    SearchRequest.builder().query("alpha").topK(1).similarityThreshold(0.0).build());
            assertFalse(hits.isEmpty());
            assertEquals("a", hits.get(0).getId());
        } finally {
            index.close();
        }
    }

    /**
     * Deterministic embedding model: derives a fixed unit-ish vector from the
     * text hash, so the same text always maps to the same vector (a query that
     * matches a stored document's text scores highest under cosine).
     */
    private static final class StubEmbeddingModel implements EmbeddingModel {
        private final int dims;

        StubEmbeddingModel(int dims) {
            this.dims = dims;
        }

        private float[] vectorFor(String text) {
            float[] v = new float[dims];
            int seed = text == null ? 0 : text.hashCode();
            for (int i = 0; i < dims; i++) {
                v[i] = (float) Math.sin(seed + i * 0.123);
            }
            return v;
        }

        @Override
        public float[] embed(String text) {
            return vectorFor(text);
        }

        @Override
        public float[] embed(Document document) {
            return vectorFor(document.getText());
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int dimensions() {
            return dims;
        }
    }
}

/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.genai.provider.store.lucene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * Unit tests for {@link TurLuceneVectorStore}. Uses an embedded Lucene index in
 * a temporary directory plus a mocked {@link EmbeddingModel} that maps each
 * known text to a fixed unit vector — so cosine similarity is deterministic
 * and the assertions don't depend on a real embedding service.
 *
 * <p>No Docker required; runs as a regular {@code mvn test}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
class TurLuceneVectorStoreTest {

    @TempDir
    Path tempDir;

    private TurLuceneVectorIndex index;
    private EmbeddingModel embeddingModel;
    private TurLuceneVectorStore store;

    @BeforeEach
    void setUp() throws IOException {
        index = new TurLuceneVectorIndex(tempDir);
        embeddingModel = mock(EmbeddingModel.class);
        // Map known texts to orthogonal unit vectors so cosine similarity is
        // 1.0 for the matching pair, ~0.0 for unrelated.
        lenient().when(embeddingModel.embed(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            return vectorFor(d.getText());
        });
        lenient().when(embeddingModel.embed(anyString())).thenAnswer(inv ->
                vectorFor(inv.getArgument(0)));
        store = new TurLuceneVectorStore(index, embeddingModel, "test-collection");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (index != null) {
            index.close();
        }
    }

    @Test
    void getName_includesCollectionName() {
        assertThat(store.getName()).isEqualTo("LuceneVectorStore[test-collection]");
    }

    @Test
    void add_emptyOrNullList_isNoOp() {
        store.add(List.of());
        store.add(null);

        List<Document> results = store.similaritySearch(
                SearchRequest.builder().query("apple").topK(5).build());
        assertThat(results).isEmpty();
    }

    @Test
    void add_then_similaritySearch_returnsClosestDoc() {
        store.add(List.of(
                new Document("d1", "apple", Map.of()),
                new Document("d2", "banana", Map.of()),
                new Document("d3", "cherry", Map.of())));

        List<Document> results = store.similaritySearch(
                SearchRequest.builder().query("apple").topK(1).build());

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getId()).isEqualTo("d1");
        assertThat(results.getFirst().getText()).isEqualTo("apple");
    }

    @Test
    void delete_byId_removesFromIndex() {
        store.add(List.of(
                new Document("keep", "apple", Map.of()),
                new Document("drop", "banana", Map.of())));

        store.delete(List.of("drop"));

        List<Document> results = store.similaritySearch(
                SearchRequest.builder().query("banana").topK(5).build());
        assertThat(results.stream().map(Document::getId)).doesNotContain("drop");
    }

    @Test
    void delete_emptyList_isNoOp() {
        store.add(List.of(new Document("d1", "apple", Map.of())));

        store.delete(List.<String>of());
        store.delete((List<String>) null);

        List<Document> results = store.similaritySearch(
                SearchRequest.builder().query("apple").topK(1).build());
        assertThat(results).hasSize(1);
    }

    @Test
    void delete_byFilter_removesMatchingDocs() {
        store.add(List.of(
                new Document("a1", "apple", metaCategory("fruit")),
                new Document("v1", "carrot", metaCategory("vegetable"))));

        Filter.Expression filter = new FilterExpressionBuilder()
                .eq("category", "fruit").build();
        store.delete(filter);

        List<Document> remaining = store.similaritySearch(
                SearchRequest.builder().query("carrot").topK(5).build());
        assertThat(remaining.stream().map(Document::getId)).contains("v1");
        assertThat(remaining.stream().map(Document::getId)).doesNotContain("a1");
    }

    @Test
    void similaritySearch_emptyQuery_returnsEmptyList() {
        store.add(List.of(new Document("d1", "apple", Map.of())));

        List<Document> emptyQueryResult = store.similaritySearch(
                SearchRequest.builder().query("").topK(1).build());
        assertThat(emptyQueryResult).isEmpty();
    }

    @Test
    void similaritySearch_belowThreshold_isFilteredOut() {
        store.add(List.of(new Document("d1", "apple", Map.of())));

        // Querying with "banana" (orthogonal vector) → cosine similarity ≈ 0.5
        // after Lucene's KNN normalization. Threshold 0.99 should reject it.
        List<Document> results = store.similaritySearch(
                SearchRequest.builder()
                        .query("banana")
                        .topK(5)
                        .similarityThreshold(0.99)
                        .build());

        assertThat(results).isEmpty();
    }

    private Map<String, Object> metaCategory(String category) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("category", category);
        return meta;
    }

    /**
     * Deterministic 3-dimensional unit vectors, one per known text. Anything
     * unknown maps to a zero-ish vector (Lucene KNN handles that gracefully).
     */
    private static float[] vectorFor(String text) {
        if (text == null) return new float[] { 0.001f, 0.001f, 0.001f };
        return switch (text) {
            case "apple" -> new float[] { 1f, 0f, 0f };
            case "banana" -> new float[] { 0f, 1f, 0f };
            case "cherry" -> new float[] { 0f, 0f, 1f };
            case "carrot" -> new float[] { 0f, 0.7071f, 0.7071f };
            default -> new float[] { 0.001f, 0.001f, 0.001f };
        };
    }
}

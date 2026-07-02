/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import com.viglet.turing.properties.TurRetrievalProperty;

import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultLocation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultS3Location;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;

/**
 * T520 — the Bedrock KB backend is inert without a knowledge-base id, and maps a
 * {@code Retrieve} response onto Spring AI documents carrying provenance so they
 * flow through the existing sources/citation paths.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurBedrockKnowledgeBaseBackendTest {

    private TurBedrockKnowledgeBaseBackend backend(String knowledgeBaseId) {
        TurRetrievalProperty property = new TurRetrievalProperty();
        property.getBedrockKb().setKnowledgeBaseId(knowledgeBaseId);
        return new TurBedrockKnowledgeBaseBackend(property);
    }

    @Test
    void inertWithoutKnowledgeBaseId() {
        TurBedrockKnowledgeBaseBackend b = backend(null);
        assertThat(b.isAvailable()).isFalse();
        assertThat(b.retrieve(new TurRetrievalRequest("q", 5))).isEmpty();
        assertThat(b.getType()).isEqualTo(TurRetrievalBackendType.BEDROCK_KB);
    }

    @Test
    void availableWhenKnowledgeBaseIdSet() {
        assertThat(backend("kb-123").isAvailable()).isTrue();
    }

    @Test
    void mapsResultsToDocumentsWithProvenance() {
        RetrieveResponse response = RetrieveResponse.builder()
                .retrievalResults(
                        KnowledgeBaseRetrievalResult.builder()
                                .content(RetrievalResultContent.builder().text("The sun is a star.").build())
                                .location(RetrievalResultLocation.builder()
                                        .s3Location(RetrievalResultS3Location.builder()
                                                .uri("s3://corpus/astro/sun.txt").build())
                                        .build())
                                .score(0.91)
                                .build(),
                        // a result with no text is skipped
                        KnowledgeBaseRetrievalResult.builder()
                                .content(RetrievalResultContent.builder().text("  ").build())
                                .score(0.4)
                                .build())
                .build();

        List<Document> docs = backend("kb-123").mapResults(response);

        assertThat(docs).hasSize(1);
        Document doc = docs.get(0);
        assertThat(doc.getText()).isEqualTo("The sun is a star.");
        assertThat(doc.getMetadata()).containsEntry("source_id", "s3://corpus/astro/sun.txt");
        assertThat(doc.getMetadata()).containsEntry("url", "s3://corpus/astro/sun.txt");
        assertThat(doc.getMetadata()).containsEntry("title", "sun.txt");
        assertThat(doc.getScore()).isEqualTo(0.91);
    }

    @Test
    void mapsEmptyResponseToEmptyList() {
        assertThat(backend("kb-123").mapResults(RetrieveResponse.builder().build())).isEmpty();
        assertThat(backend("kb-123").mapResults(null)).isEmpty();
    }
}

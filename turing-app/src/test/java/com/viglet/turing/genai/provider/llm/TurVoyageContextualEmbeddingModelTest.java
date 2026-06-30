/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.viglet.turing.genai.provider.llm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TurVoyageContextualEmbeddingModelTest {

    @Test
    void toDocumentVectors_parsesNestedPerChunkEmbeddings() {
        String json = """
                {
                  "object": "list",
                  "data": [
                    {
                      "object": "list",
                      "data": [
                        { "object": "embedding", "embedding": [0.1, 0.2], "index": 0 },
                        { "object": "embedding", "embedding": [0.3, 0.4], "index": 1 }
                      ],
                      "index": 0
                    }
                  ],
                  "model": "voyage-context-3"
                }
                """;
        JsonNode response = JsonMapper.builder().build().readTree(json);

        List<List<float[]>> docs = TurVoyageContextualEmbeddingModel.toDocumentVectors(response);

        assertEquals(1, docs.size());
        List<float[]> chunks = docs.get(0);
        assertEquals(2, chunks.size());
        assertArrayEquals(new float[] { 0.1f, 0.2f }, chunks.get(0), 0.0001f);
        assertArrayEquals(new float[] { 0.3f, 0.4f }, chunks.get(1), 0.0001f);
    }

    @Test
    void toDocumentVectors_emptyOnMissingData() {
        JsonNode response = JsonMapper.builder().build().readTree("{}");
        assertTrue(TurVoyageContextualEmbeddingModel.toDocumentVectors(response).isEmpty());
    }

    @Test
    void dimensions_honoursMatryoshkaTruncation() {
        TurVoyageContextualEmbeddingModel withTruncation =
                new TurVoyageContextualEmbeddingModel("key", "voyage-context-3", 256);
        assertEquals(256, withTruncation.dimensions());

        TurVoyageContextualEmbeddingModel nativeDims =
                new TurVoyageContextualEmbeddingModel("key", "voyage-context-3", null);
        assertEquals(TurVoyageContextualEmbeddingModel.DEFAULT_DIMENSIONS, nativeDims.dimensions());
    }
}

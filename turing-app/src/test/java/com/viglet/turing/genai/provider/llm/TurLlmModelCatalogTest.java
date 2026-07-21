/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.genai.provider.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.openviglet.modelscatalog.ModelCatalogClient;

/**
 * Verifies {@link TurLlmModelCatalog} maps the canonical <em>envelope</em> catalog
 * ({@code {version, lastUpdated, vendors:{...}}}) served by the public endpoint —
 * fetched through the {@code models-catalog-client} library — into the vendor→models
 * map, honouring each entry's {@code kind} (or the heuristic when absent). The catalog
 * is remote-only (no bundled resource): with the remote fetch disabled the served map
 * stays empty, and a failed/empty refresh keeps the last-good map.
 */
class TurLlmModelCatalogTest {

    private final TurLlmModelCatalog catalog = new TurLlmModelCatalog();

    /** A catalog client whose transport returns the given envelope JSON for any URL. */
    private static ModelCatalogClient clientServing(String envelopeJson) {
        return ModelCatalogClient.builder().fetcher(url -> envelopeJson).build();
    }

    @Test
    void refreshMapsEnvelopeHonouringKinds() {
        String envelope = """
                {
                  "version": 1,
                  "lastUpdated": "2026-07-21",
                  "vendors": {
                    "openai": [
                      { "id": "gpt-4o", "label": "GPT-4o", "kind": "CHAT" },
                      { "id": "text-embedding-3-small", "label": "text-embedding-3-small", "kind": "EMBEDDING" }
                    ],
                    "cohere": [
                      { "id": "rerank-v3.5", "label": "Rerank v3.5", "kind": "RERANK" }
                    ]
                  }
                }
                """;
        catalog.refreshFrom(clientServing(envelope));
        assertThat(catalog.staticModels("openai")).extracting(TurLlmModelOption::id)
                .containsExactly("gpt-4o", "text-embedding-3-small");
        assertThat(catalog.staticModels("openai"))
                .anyMatch(o -> o.kind() == TurLlmModelKind.CHAT)
                .anyMatch(o -> o.kind() == TurLlmModelKind.EMBEDDING);
        assertThat(catalog.staticModels("cohere")).anyMatch(o -> o.kind() == TurLlmModelKind.RERANK);
    }

    @Test
    void refreshHandlesRemotePublishedShapeWithEnrichedFields() {
        // The public endpoint serves per-entry `vendor` + enriched fields; only
        // id/label/kind are consumed, the extras are ignored.
        String remote = """
                {
                  "version": 1,
                  "lastUpdated": "2026-07-21",
                  "vendors": {
                    "openai": [
                      { "id": "gpt-4o", "label": "GPT-4o", "kind": "CHAT", "vendor": "openai",
                        "contextWindow": 128000, "capabilities": ["vision"], "sources": ["litellm"] },
                      { "id": "text-embedding-3-small", "label": "text-embedding-3-small",
                        "kind": "EMBEDDING", "vendor": "openai", "embeddingDimensions": 1536 }
                    ]
                  }
                }
                """;
        catalog.refreshFrom(clientServing(remote));
        assertThat(catalog.staticModels("openai")).extracting(TurLlmModelOption::id)
                .containsExactly("gpt-4o", "text-embedding-3-small");
        assertThat(catalog.staticModels("openai")).anyMatch(o -> o.kind() == TurLlmModelKind.EMBEDDING);
    }

    @Test
    void refreshFallsBackToHeuristicWhenKindAbsent() {
        // No explicit kind → the id/label heuristic classifies (embedding id → EMBEDDING).
        String envelope = """
                {
                  "version": 1,
                  "vendors": {
                    "openai": [
                      { "id": "gpt-4o", "label": "GPT-4o" },
                      { "id": "text-embedding-3-large", "label": "text-embedding-3-large" }
                    ]
                  }
                }
                """;
        catalog.refreshFrom(clientServing(envelope));
        assertThat(catalog.staticModels("openai"))
                .anyMatch(o -> o.id().equals("gpt-4o") && o.kind() == TurLlmModelKind.CHAT)
                .anyMatch(o -> o.id().equals("text-embedding-3-large") && o.kind() == TurLlmModelKind.EMBEDDING);
    }

    @Test
    void refreshKeepsLastGoodOnEmptyResult() {
        catalog.refreshFrom(clientServing("""
                { "version": 1, "vendors": { "openai": [ { "id": "gpt-4o", "label": "GPT-4o" } ] } }
                """));
        // A subsequent empty envelope must not wipe the last-good map.
        catalog.refreshFrom(clientServing("""
                { "version": 1, "vendors": {} }
                """));
        assertThat(catalog.staticModels("openai")).extracting(TurLlmModelOption::id).containsExactly("gpt-4o");
    }

    @Test
    void staticModelsIsEmptyBeforeAnyRefresh() {
        // Remote-only: nothing is served until the first successful remote fetch.
        assertThat(catalog.staticModels("openai")).isEmpty();
        assertThat(catalog.staticModels("does-not-exist")).isEmpty();
        assertThat(catalog.staticModels(null)).isEmpty();
    }

    @Test
    void remoteRefreshDisabledIsNoOp() {
        // In a plain unit (no Spring) remoteEnabled defaults to false → no network,
        // the served map stays empty, and the call does not throw.
        catalog.refreshFromRemote();
        assertThat(catalog.staticModels("openai")).isEmpty();
    }
}

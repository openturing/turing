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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Verifies {@link TurLlmModelCatalog} parses the canonical <em>envelope</em>
 * catalog ({@code {version, lastUpdated, vendors:{...}}}) served by the public
 * endpoint, honouring the per-entry {@code kind}. The catalog is remote-only (no
 * bundled resource): with the remote fetch disabled the served map stays empty.
 */
class TurLlmModelCatalogTest {

    private final TurLlmModelCatalog catalog = new TurLlmModelCatalog();

    @Test
    void parseCatalogParsesEnvelopeWithKinds() throws Exception {
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
        var parsed = catalog.parseCatalog(new ObjectMapper().readTree(envelope));
        assertThat(parsed.get("openai")).extracting(TurLlmModelOption::id)
                .containsExactly("gpt-4o", "text-embedding-3-small");
        assertThat(parsed.get("openai"))
                .allSatisfy(o -> assertThat(o.kind()).isNotNull())
                .anyMatch(o -> o.kind() == TurLlmModelKind.CHAT)
                .anyMatch(o -> o.kind() == TurLlmModelKind.EMBEDDING);
        assertThat(parsed.get("cohere")).anyMatch(o -> o.kind() == TurLlmModelKind.RERANK);
    }

    @Test
    void parseCatalogHandlesRemotePublishedShape() throws Exception {
        // The public endpoint serves the envelope with per-entry `vendor` + enriched
        // fields; only id/label/kind are consumed, extras ignored.
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
        var parsed = catalog.parseCatalog(new ObjectMapper().readTree(remote));
        assertThat(parsed.get("openai")).extracting(TurLlmModelOption::id)
                .containsExactly("gpt-4o", "text-embedding-3-small");
        assertThat(parsed.get("openai")).anyMatch(o -> o.kind() == TurLlmModelKind.EMBEDDING);
    }

    @Test
    void parseCatalogToleratesLegacyFlatShape() throws Exception {
        String legacy = """
                { "openai": [ { "id": "gpt-4o", "label": "GPT-4o" } ] }
                """;
        var parsed = catalog.parseCatalog(new ObjectMapper().readTree(legacy));
        assertThat(parsed.get("openai")).extracting(TurLlmModelOption::id).containsExactly("gpt-4o");
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

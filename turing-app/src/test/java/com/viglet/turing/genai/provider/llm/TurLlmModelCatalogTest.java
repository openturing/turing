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

import io.github.openviglet.modelcatalog.ModelCatalogClient;

/**
 * Verifies {@link TurLlmModelCatalog} maps the canonical <em>envelope</em> catalog
 * ({@code {version, lastUpdated, vendors:{...}}}) served by the public endpoint —
 * fetched through the {@code model-catalog-client} library — into the vendor→models
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
    void refreshCarriesRichCatalogMetadata() {
        // T776: the rich ModelEntry fields must survive the catalog boundary as
        // TurLlmModelMetadata instead of being collapsed to id/label/kind.
        String remote = """
                {
                  "version": 1,
                  "lastUpdated": "2026-07-21",
                  "vendors": {
                    "openai": [
                      { "id": "gpt-4o", "label": "GPT-4o", "kind": "CHAT", "vendor": "openai",
                        "contextWindow": 128000, "maxOutputTokens": 16384,
                        "capabilities": ["tools", "vision"],
                        "modalities": { "input": ["text", "image"], "output": ["text"] },
                        "pricing": { "inputPer1M": 2.5, "outputPer1M": 10.0, "currency": "USD",
                                     "indicative": true, "source": "litellm", "lastVerified": "2026-07-20" },
                        "benchmarks": { "intelligenceIndex": 72.5, "arenaElo": 1310.0 },
                        "performance": { "throughputTps": 95.0, "latencyTtftSec": 0.42 },
                        "knowledgeCutoff": "2024-10", "releaseDate": "2024-05-13",
                        "status": "GA" },
                      { "id": "text-embedding-3-small", "label": "text-embedding-3-small",
                        "kind": "EMBEDDING", "vendor": "openai", "embeddingDimensions": 1536 }
                    ]
                  }
                }
                """;
        catalog.refreshFrom(clientServing(remote));

        TurLlmModelOption chat = catalog.staticModels("openai").stream()
                .filter(o -> o.id().equals("gpt-4o")).findFirst().orElseThrow();
        assertThat(chat.metadata()).isNotNull();
        assertThat(chat.metadata().contextWindow()).isEqualTo(128000);
        assertThat(chat.metadata().maxOutputTokens()).isEqualTo(16384);
        assertThat(chat.metadata().capabilities()).containsExactly("tools", "vision");
        assertThat(chat.metadata().modalities().input()).containsExactly("text", "image");
        assertThat(chat.metadata().pricing().inputPer1M()).isEqualTo(2.5);
        assertThat(chat.metadata().pricing().outputPer1M()).isEqualTo(10.0);
        assertThat(chat.metadata().pricing().indicative()).isTrue();
        assertThat(chat.metadata().benchmarks().intelligenceIndex()).isEqualTo(72.5);
        assertThat(chat.metadata().performance().latencyTtftSec()).isEqualTo(0.42);
        assertThat(chat.metadata().knowledgeCutoff()).isEqualTo("2024-10");
        assertThat(chat.metadata().tier()).isNotBlank();

        TurLlmModelOption embed = catalog.staticModels("openai").stream()
                .filter(o -> o.id().equals("text-embedding-3-small")).findFirst().orElseThrow();
        assertThat(embed.metadata()).isNotNull();
        assertThat(embed.metadata().embeddingDimensions()).isEqualTo(1536);
        assertThat(embed.metadata().pricing()).isNull();
    }

    @Test
    void refreshCarriesClassifierTagsForBareEntry() {
        // A bare id/label/kind entry still gains the client Classifier's tags (it
        // always tags at least the kind), but invents no numeric facts: pricing,
        // context window and tier (which needs benchmarks) stay null.
        catalog.refreshFrom(clientServing("""
                { "version": 1, "vendors": { "openai": [ { "id": "gpt-4o", "label": "GPT-4o", "kind": "CHAT" } ] } }
                """));
        assertThat(catalog.staticModels("openai")).singleElement().satisfies(o -> {
            assertThat(o.metadata()).isNotNull();
            assertThat(o.metadata().tags()).isNotEmpty();
            assertThat(o.metadata().contextWindow()).isNull();
            assertThat(o.metadata().pricing()).isNull();
        });
    }

    @Test
    void liveVendorOptionHasNullMetadata() {
        // A model surfaced by a live vendor listing (no catalog entry) carries no
        // metadata — absence must never be mistaken for a catalog default.
        assertThat(new TurLlmModelOption("gpt-4o", "GPT-4o").metadata()).isNull();
        assertThat(new TurLlmModelOption("gpt-4o", "GPT-4o", TurLlmModelKind.CHAT).metadata()).isNull();
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
    void refreshPopulatesProviderPricingLinks() {
        // T778: the providers() registry (providers.json) yields the per-vendor
        // "verify at vendor" pricing links, keyed by lower-cased catalog vendor.
        String providersJson = """
                {
                  "version": 1,
                  "providers": [
                    { "id": "openai", "name": "OpenAI", "catalogVendor": "openai",
                      "apiPricingUrl": "https://openai.com/api/pricing/" },
                    { "id": "anthropic", "name": "Anthropic", "catalogVendor": "anthropic",
                      "apiPricingUrl": "https://claude.com/pricing" },
                    { "id": "no-url", "catalogVendor": "acme" }
                  ]
                }
                """;
        catalog.refreshProviderLinks(clientServing(providersJson));
        assertThat(catalog.providerPricingUrls())
                .containsEntry("openai", "https://openai.com/api/pricing/")
                .containsEntry("anthropic", "https://claude.com/pricing")
                .doesNotContainKey("acme");
    }

    @Test
    void providerPricingUrlsToleratesGarbageShapes() {
        // Untyped 1.0.5 client: anything that is not the expected list-of-maps is
        // skipped, never thrown.
        assertThat(catalog.toProviderPricingUrls(null)).isEmpty();
        assertThat(catalog.toProviderPricingUrls(java.util.Map.of("providers", "not-a-list"))).isEmpty();
        assertThat(catalog.toProviderPricingUrls(java.util.Map.of("other", 1))).isEmpty();
    }

    @Test
    void refreshParsesCatalogChangeFeed() {
        // T788: the changes() registry (changes.json) is parsed into added/removed.
        String changesJson = """
                {
                  "version": 1,
                  "counts": { "added": 1, "removed": 1, "changed": 0 },
                  "added": [ { "vendor": "anthropic", "id": "claude-haiku-4-5", "kind": "CHAT", "label": "Claude Haiku 4.5" } ],
                  "removed": [ { "vendor": "anthropic", "id": "claude-3-5-haiku", "kind": "CHAT", "label": "Claude Haiku 3.5" } ],
                  "changed": []
                }
                """;
        catalog.refreshCatalogChanges(clientServing(changesJson));
        assertThat(catalog.catalogChanges().added()).extracting(e -> e.id()).containsExactly("claude-haiku-4-5");
        assertThat(catalog.catalogChanges().removed()).extracting(e -> e.id()).containsExactly("claude-3-5-haiku");
        assertThat(catalog.catalogChanges().changed()).isEmpty();
    }

    @Test
    void catalogChangesToleratesGarbageShapes() {
        assertThat(catalog.toCatalogChanges(null)).isSameAs(com.viglet.turing.genai.provider.llm.TurCatalogChanges.EMPTY);
        assertThat(catalog.toCatalogChanges(java.util.Map.of("added", "not-a-list")).added()).isEmpty();
    }

    @Test
    void refreshParsesConsumerPlans() {
        // T789: the plans() registry (plans.json) is parsed per-vendor.
        String plansJson = """
                {
                  "version": 1,
                  "plans": {
                    "anthropic": [
                      { "id": "claude-pro", "name": "Claude Pro", "product": "Claude", "tier": "pro",
                        "priceMonthlyUSD": 20, "annualMonthlyUSD": 17, "currency": "USD",
                        "url": "https://claude.com/pricing", "indicative": true }
                    ]
                  }
                }
                """;
        catalog.refreshConsumerPlans(clientServing(plansJson));
        assertThat(catalog.consumerPlans().byVendor()).containsKey("anthropic");
        assertThat(catalog.consumerPlans().byVendor().get("anthropic")).singleElement().satisfies(p -> {
            assertThat(p.id()).isEqualTo("claude-pro");
            assertThat(p.priceMonthlyUsd()).isEqualTo(20.0);
            assertThat(p.annualMonthlyUsd()).isEqualTo(17.0);
            assertThat(p.url()).isEqualTo("https://claude.com/pricing");
        });
    }

    @Test
    void consumerPlansToleratesGarbageShapes() {
        assertThat(catalog.toConsumerPlans(null)).isSameAs(com.viglet.turing.genai.provider.llm.TurCatalogPlans.EMPTY);
        assertThat(catalog.toConsumerPlans(java.util.Map.of("plans", "not-a-map")))
                .isSameAs(com.viglet.turing.genai.provider.llm.TurCatalogPlans.EMPTY);
    }

    @Test
    void remoteRefreshDisabledIsNoOp() {
        // In a plain unit (no Spring) remoteEnabled defaults to false → no network,
        // the served map stays empty, and the call does not throw.
        catalog.refreshFromRemote();
        assertThat(catalog.staticModels("openai")).isEmpty();
    }
}

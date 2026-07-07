/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.genai.batch.gemini.TurGeminiBatchProvider;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * T496 / §X.19 — verifies the full Spring context registers
 * {@link TurGeminiBatchProvider} as an <em>embeddings</em> batch provider, so a
 * Gemini-backed instance now resolves an embeddings Batch leg (T158 re-embedding
 * at the Batch discount) instead of falling back to synchronous full price. Also
 * pins the vendor-routing matrix: Gemini and OpenAI support embeddings batch,
 * Anthropic does not (no embedding API).
 *
 * <p>No live API key is touched — this exercises only bean wiring and the
 * vendor-to-provider resolution, which is exactly the regression risk introduced
 * by making the provider implement a second interface.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurGeminiEmbeddingBatchProviderIT extends AbstractTuringSpringIT {

    @Autowired
    private TurBatchProviderFactory providerFactory;

    @Autowired
    private TurBatchInferenceService batchInferenceService;

    private static TurLLMInstance instanceFor(String pluginType) {
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId(pluginType);
        vendor.setPlugin(pluginType);
        vendor.setTitle(pluginType);

        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-" + pluginType);
        instance.setTitle(pluginType + " instance");
        instance.setTurLLMVendor(vendor);
        return instance;
    }

    @Test
    void geminiResolvesAnEmbeddingsBatchProvider() {
        Optional<TurBatchEmbeddingProvider> provider =
                providerFactory.getEmbeddingProvider(instanceFor("gemini"));

        assertThat(provider).isPresent();
        assertThat(provider.get()).isInstanceOf(TurGeminiBatchProvider.class);
        assertThat(provider.get().getPluginType()).isEqualTo("gemini");
    }

    @Test
    void geminiStillResolvesAChatBatchProvider() {
        Optional<TurBatchProvider> provider = providerFactory.getProvider(instanceFor("gemini"));

        assertThat(provider).isPresent();
        assertThat(provider.get()).isInstanceOf(TurGeminiBatchProvider.class);
    }

    @Test
    void isEmbeddingSupportedMatchesVendorMatrix() {
        // Gemini and OpenAI have an embeddings Batch API; Anthropic has no embedding API at all.
        assertThat(batchInferenceService.isEmbeddingSupported(instanceFor("gemini"))).isTrue();
        assertThat(batchInferenceService.isEmbeddingSupported(instanceFor("openai"))).isTrue();
        assertThat(batchInferenceService.isEmbeddingSupported(instanceFor("anthropic"))).isFalse();
    }

    @Test
    void anthropicHasChatButNotEmbeddingsBatch() {
        TurLLMInstance anthropic = instanceFor("anthropic");
        assertThat(providerFactory.getProvider(anthropic)).isPresent();
        assertThat(providerFactory.getEmbeddingProvider(anthropic)).isEmpty();
    }
}

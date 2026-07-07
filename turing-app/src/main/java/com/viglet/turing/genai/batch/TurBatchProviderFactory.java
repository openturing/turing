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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * F.7 / §X.8.a — resolves the {@link TurBatchProvider} for a
 * {@link TurLLMInstance} by its plugin type, mirroring
 * {@link TurGenAiLlmProviderFactory}.
 *
 * <p>OpenAI, Anthropic, and Gemini register a chat batch provider; OpenAI and
 * Gemini also register an embeddings batch provider. An instance on any vendor
 * without the relevant provider resolves to {@link Optional#empty()}, which
 * callers treat as "batch not supported, fall back to the synchronous path".
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurBatchProviderFactory {

    private final Map<String, TurBatchProvider> providerMap;
    private final Map<String, TurBatchEmbeddingProvider> embeddingProviderMap;
    private final TurGenAiLlmProviderFactory llmProviderFactory;

    public TurBatchProviderFactory(List<TurBatchProvider> providers,
            List<TurBatchEmbeddingProvider> embeddingProviders,
            TurGenAiLlmProviderFactory llmProviderFactory) {
        this.providerMap = providers.stream().collect(Collectors.toMap(
                provider -> provider.getPluginType().toLowerCase(Locale.ROOT),
                Function.identity()));
        this.embeddingProviderMap = embeddingProviders.stream().collect(Collectors.toMap(
                provider -> provider.getPluginType().toLowerCase(Locale.ROOT),
                Function.identity()));
        this.llmProviderFactory = llmProviderFactory;
    }

    /** The chat-batch provider for the instance's vendor, if one exists. */
    public Optional<TurBatchProvider> getProvider(TurLLMInstance instance) {
        return Optional.ofNullable(providerMap.get(pluginType(instance)));
    }

    /** The embeddings-batch provider for the instance's vendor, if one exists (OpenAI / Gemini). */
    public Optional<TurBatchEmbeddingProvider> getEmbeddingProvider(TurLLMInstance instance) {
        return Optional.ofNullable(embeddingProviderMap.get(pluginType(instance)));
    }

    private String pluginType(TurLLMInstance instance) {
        if (instance == null) {
            return null;
        }
        TurGenAiLlmProvider llmProvider = llmProviderFactory.getProvider(instance);
        return llmProvider == null ? null : llmProvider.getPluginType().toLowerCase(Locale.ROOT);
    }
}

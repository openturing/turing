/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.resilience.llm;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.observability.TurLlmObservation;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.resilience.TurResilienceExecutor;
import com.viglet.turing.resilience.TurResilienceRegistry;
import com.viglet.turing.tenant.TurTenantQuotaService;

/**
 * Single boundary for obtaining LLM chat and embedding models with the
 * resilience pipeline already applied. Call sites should depend on this factory
 * rather than on {@link TurGenAiLlmProviderFactory} directly so that retry,
 * circuit breaker and time limiter are uniformly enforced.
 *
 * <p>When {@code turing.resilience.llm.enabled=false} (or the global toggle is
 * off), the returned models are still wrapped, but the executor short-circuits
 * to direct delegation, so the cost is one extra method call.
 *
 * <p>T963 / §LXXII — and because it is that boundary, it is also where the
 * tenant's monthly token budget is admitted. The alternative was a list of the
 * ~45 endpoints that reach an LLM, which is a list that goes stale the first
 * time somebody adds the forty-sixth: the two filters that already try to
 * enumerate "LLM-cost surfaces" by path regex have both drifted, missing
 * {@code /answer} and the {@code /v1} gateway. Every one of those endpoints
 * comes through here.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurLlmModelFactory {

    private final TurGenAiLlmProviderFactory providerFactory;
    private final TurResilienceExecutor executor;
    private final TurResilienceRegistry registry;
    private final TurLlmObservation observation;
    private final TurTenantQuotaService turTenantQuotaService;

    public TurLlmModelFactory(TurGenAiLlmProviderFactory providerFactory,
            TurResilienceExecutor executor,
            TurResilienceRegistry registry,
            TurLlmObservation observation,
            TurTenantQuotaService turTenantQuotaService) {
        this.providerFactory = providerFactory;
        this.executor = executor;
        this.registry = registry;
        this.observation = observation;
        this.turTenantQuotaService = turTenantQuotaService;
    }

    public ChatModel createChatModel(TurLLMInstance llmInstance, String decryptedApiKey) {
        turTenantQuotaService.checkLlmAdmission();
        TurGenAiLlmProvider provider = providerFactory.getProvider(llmInstance);
        ChatModel raw = provider.createChatModel(llmInstance, decryptedApiKey);
        return new TurResilientChatModel(raw, executor, registry, observation, provider.getPluginType());
    }

    /**
     * T18 / §IV.1.b — companion to {@link #createChatModel} for the STREAM
     * code path. Delegates to {@link TurGenAiLlmProvider#createStreamingChatModel}
     * which lets the OpenAI provider swap to a WebClient-based transport that
     * publishes SSE deltas per chunk instead of buffering them through the
     * SDK iterator. Wrapped in the same resilience pipeline so retry /
     * circuit breaker / time limiter apply uniformly across both shapes.
     *
     * <p>Non-OpenAI providers inherit the default and return the same
     * model as {@link #createChatModel}; the resilience wrap is identical.
     *
     * @since 2026.2.7
     */
    public ChatModel createStreamingChatModel(TurLLMInstance llmInstance, String decryptedApiKey) {
        // Admitted here rather than inside the returned Flux: a refusal raised
        // once the stream is open reaches the client as a 200 carrying an error
        // event, and the console's banner reads HTTP status.
        turTenantQuotaService.checkLlmAdmission();
        TurGenAiLlmProvider provider = providerFactory.getProvider(llmInstance);
        ChatModel raw = provider.createStreamingChatModel(llmInstance, decryptedApiKey);
        return new TurResilientChatModel(raw, executor, registry, observation, provider.getPluginType());
    }

    /**
     * Deliberately <b>not</b> admitted against the token budget. Embeddings are
     * what indexing and retrieval run on, so refusing them turns an exhausted
     * chat credit into a search outage and a half-indexed corpus — a failure the
     * operator did not cause and cannot read as "the plan ran out". The budget
     * bites where the spending is interactive and the refusal is legible.
     */
    public EmbeddingModel createEmbeddingModel(TurLLMInstance llmInstance, String decryptedApiKey) {
        TurGenAiLlmProvider provider = providerFactory.getProvider(llmInstance);
        EmbeddingModel raw = provider.createEmbeddingModel(llmInstance, decryptedApiKey);
        return new TurResilientEmbeddingModel(raw, executor, observation, provider.getPluginType());
    }
}

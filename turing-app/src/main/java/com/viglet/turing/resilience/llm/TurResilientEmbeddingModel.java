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

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import com.viglet.turing.genai.provider.llm.TurContextualEmbeddingModel;
import com.viglet.turing.genai.provider.llm.TurMultimodalEmbeddingModel;
import com.viglet.turing.observability.TurLlmObservation;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.resilience.TurResilienceExecutor;
import com.viglet.turing.resilience.TurResilienceRegistry.Kind;

/**
 * Wraps a Spring AI {@link EmbeddingModel} with retry + circuit breaker + time limiter.
 *
 * <p>Default methods like {@code embed(String)} and {@code embed(List)} delegate
 * internally to {@code embed(Document)} and {@code call(EmbeddingRequest)}, both
 * of which we override here, so the resilience pipeline applies transitively.
 *
 * <p>T511 / §XXVIII.7 — when the wrapped delegate is a
 * {@link TurMultimodalEmbeddingModel}, this wrapper transparently exposes the
 * image side too (and applies the same resilience pipeline to it), so the
 * multimodal capability is not hidden by the resilience boundary.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public class TurResilientEmbeddingModel
        implements EmbeddingModel, TurMultimodalEmbeddingModel, TurContextualEmbeddingModel {

    private final EmbeddingModel delegate;
    private final TurResilienceExecutor executor;
    private final TurLlmObservation observation;
    private final String providerType;

    public TurResilientEmbeddingModel(EmbeddingModel delegate, TurResilienceExecutor executor,
            TurLlmObservation observation, String providerType) {
        this.delegate = delegate;
        this.executor = executor;
        this.observation = observation;
        this.providerType = providerType;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return observation.observeCall(providerType, TurMeterNames.OP_EMBED,
                () -> executor.execute(Kind.LLM, providerType, () -> delegate.call(request)));
    }

    @Override
    public float[] embed(Document document) {
        return observation.observeCall(providerType, TurMeterNames.OP_EMBED,
                () -> executor.execute(Kind.LLM, providerType, () -> delegate.embed(document)));
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    // --- T511 multimodal pass-through --------------------------------------

    @Override
    public boolean supportsImageEmbedding() {
        return TurMultimodalEmbeddingModel.of(delegate) != null;
    }

    @Override
    public float[] embedImage(byte[] imageBytes, String mimeType) {
        TurMultimodalEmbeddingModel multimodal = TurMultimodalEmbeddingModel.of(delegate);
        if (multimodal == null) {
            throw new UnsupportedOperationException(
                    "Embedding provider '" + providerType + "' does not support image embedding");
        }
        return observation.observeCall(providerType, TurMeterNames.OP_EMBED,
                () -> executor.execute(Kind.LLM, providerType, () -> multimodal.embedImage(imageBytes, mimeType)));
    }

    // --- T512 contextual chunk pass-through --------------------------------

    @Override
    public boolean supportsContextualChunks() {
        return TurContextualEmbeddingModel.of(delegate) != null;
    }

    @Override
    public List<float[]> embedDocumentChunks(List<String> chunks) {
        TurContextualEmbeddingModel contextual = TurContextualEmbeddingModel.of(delegate);
        if (contextual == null) {
            throw new UnsupportedOperationException(
                    "Embedding provider '" + providerType + "' does not support contextualized chunk embedding");
        }
        return observation.observeCall(providerType, TurMeterNames.OP_EMBED,
                () -> executor.execute(Kind.LLM, providerType, () -> contextual.embedDocumentChunks(chunks)));
    }
}

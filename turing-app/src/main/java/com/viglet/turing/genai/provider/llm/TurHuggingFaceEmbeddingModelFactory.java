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

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds in-process ONNX {@link EmbeddingModel}s for the {@value #PROVIDER_TYPE}
 * embedding-model provider type — the "pick a model from a list" ergonomic twin
 * of {@link TurLocalEmbeddingModelFactory} (§XXXVI, T623).
 *
 * <p>
 * A {@link TurEmbeddingModel} whose {@code providerType} is
 * {@value #PROVIDER_TYPE} has no LLM instance; instead its
 * {@code modelReference} column holds a HuggingFace <b>repo id</b> (e.g.
 * {@code sentence-transformers/all-MiniLM-L6-v2}) — <b>no new entity
 * columns</b>. {@link TurHuggingFaceRepoResolver} turns that repo id into the
 * {@code resolve/main/…onnx} + {@code tokenizer.json} download URLs (handling
 * the {@code onnx/} subfolder vs root layout), and the <i>same</i> cached
 * {@link TurTransformersEmbeddingModelBuilder} the local path owns loads it
 * in-process. So this provider is the same runtime as
 * {@code TRANSFORMERS_LOCAL}; only the model-selection UX differs.
 *
 * <p>
 * Fail-open, exactly like the local path: the resolver falls back to the
 * conventional layout when the repo tree can't be probed, and a genuinely
 * broken repo id surfaces as an {@link IllegalStateException} the RAG builder
 * already logs-and-skips.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurHuggingFaceEmbeddingModelFactory {

    /** {@code providerType} sentinel identifying a HuggingFace-picked ONNX model. */
    public static final String PROVIDER_TYPE = "HUGGINGFACE";

    private final TurHuggingFaceRepoResolver repoResolver;
    private final TurTransformersEmbeddingModelBuilder builder;

    public TurHuggingFaceEmbeddingModelFactory(TurHuggingFaceRepoResolver repoResolver,
            TurTransformersEmbeddingModelBuilder builder) {
        this.repoResolver = repoResolver;
        this.builder = builder;
    }

    /** @return {@code true} when {@code providerType} is the HuggingFace sentinel. */
    public boolean supports(String providerType) {
        return PROVIDER_TYPE.equalsIgnoreCase(providerType);
    }

    /**
     * Resolves (building + caching on first use) the ONNX embedding model whose
     * HuggingFace repo id lives in {@code modelReference}. Callers must have
     * checked {@link #supports(String)}.
     *
     * @throws IllegalStateException when the repo id is missing or the model
     *                               fails to initialize
     */
    public EmbeddingModel resolve(TurEmbeddingModel embModel) {
        String repoId = embModel.getModelReference();
        if (!StringUtils.hasText(repoId)) {
            throw new IllegalStateException(
                    "HuggingFace embedding model '%s' (%s) requires a repo id in modelReference."
                            .formatted(embModel.getModelName(), PROVIDER_TYPE));
        }
        TurHuggingFaceRepoResolver.HfArtifacts artifacts = repoResolver.resolve(repoId);
        log.info("Resolving HuggingFace embedding model '{}' (repo='{}') → model='{}', tokenizer='{}'.",
                embModel.getModelName(), repoId, artifacts.onnxUrl(), artifacts.tokenizerUrl());
        return builder.resolve(artifacts.onnxUrl(), artifacts.tokenizerUrl());
    }
}

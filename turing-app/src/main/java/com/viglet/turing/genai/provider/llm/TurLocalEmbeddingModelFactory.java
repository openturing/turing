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
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds and caches in-process ONNX {@link EmbeddingModel}s for the
 * {@value #PROVIDER_TYPE} embedding-model provider type.
 *
 * <p>
 * A {@link TurEmbeddingModel} whose {@code providerType} is
 * {@value #PROVIDER_TYPE} has no LLM instance (it is not a cloud provider);
 * instead its {@code modelPath} points at a sentence-transformers ONNX model
 * ({@code .onnx}) and its {@code tokenizerPath} at the matching
 * {@code tokenizer.json}. Both accept {@code classpath:}, {@code file:} or
 * {@code https:} URIs — Spring AI's {@link TransformersEmbeddingModel} downloads
 * and caches remote (HTTP) resources on first use, so a public URL "just works"
 * offline after the first init.
 *
 * <p>
 * Loading an ONNX model + tokenizer is expensive (native session + model
 * download), so the built {@link TransformersEmbeddingModel} is cached by
 * ({@code modelPath}, {@code tokenizerPath}) and reused across every RAG build.
 * The cache holds the heavyweight model object — not a JPA entity — so it is
 * safe under the "never cache entities" rule.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurLocalEmbeddingModelFactory {

    /** {@code providerType} sentinel identifying a local ONNX embedding model. */
    public static final String PROVIDER_TYPE = "TRANSFORMERS_LOCAL";

    private final TurTransformersEmbeddingModelBuilder builder;

    public TurLocalEmbeddingModelFactory(TurTransformersEmbeddingModelBuilder builder) {
        this.builder = builder;
    }

    /** @return {@code true} when {@code providerType} is the local-ONNX sentinel. */
    public boolean supports(String providerType) {
        return PROVIDER_TYPE.equalsIgnoreCase(providerType);
    }

    /**
     * Resolves (building + caching on first use) the local ONNX embedding model
     * for the given entity. Callers must have checked {@link #supports(String)}.
     * The {@code modelPath} ({@code .onnx}) and {@code tokenizerPath}
     * ({@code tokenizer.json}) are typed by the admin.
     *
     * @throws IllegalStateException when {@code modelPath}/{@code tokenizerPath}
     *                               are missing or the model fails to initialize
     */
    public EmbeddingModel resolve(TurEmbeddingModel embModel) {
        String modelPath = embModel.getModelPath();
        String tokenizerPath = embModel.getTokenizerPath();
        if (!StringUtils.hasText(modelPath) || !StringUtils.hasText(tokenizerPath)) {
            throw new IllegalStateException(
                    "Local embedding model '%s' (%s) requires both modelPath (.onnx) and tokenizerPath (.json)."
                            .formatted(embModel.getModelName(), PROVIDER_TYPE));
        }
        return builder.resolve(modelPath, tokenizerPath);
    }
}

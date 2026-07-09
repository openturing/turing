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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds and caches in-process ONNX {@link EmbeddingModel}s from a resolved
 * ({@code .onnx} model, {@code tokenizer.json}) pair.
 *
 * <p>
 * This is the single runtime shared by every "local ONNX" embedding provider:
 * the manual {@code TRANSFORMERS_LOCAL} path
 * ({@link TurLocalEmbeddingModelFactory}, admin types the paths) and the
 * {@code HUGGINGFACE} path
 * ({@link TurHuggingFaceEmbeddingModelFactory}, the paths are resolved from a
 * picked repo id). Both doors reach the <i>same</i>
 * {@link TransformersEmbeddingModel} — only the model-selection UX differs
 * (§XXXVI).
 *
 * <p>
 * Loading an ONNX model + tokenizer is expensive (native session + model
 * download), so the built {@link TransformersEmbeddingModel} is cached by
 * ({@code modelPath}, {@code tokenizerPath}) and reused across every RAG build.
 * The cache holds the heavyweight model object — not a JPA entity — so it is
 * safe under the "never cache entities" rule. Two providers that resolve to the
 * same artifact URLs (e.g. the manual path and an HF repo id pointing at the
 * same files) share one cached instance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurTransformersEmbeddingModelBuilder {

    private final Map<String, EmbeddingModel> cache = new ConcurrentHashMap<>();

    /**
     * Directory where remote (HTTP) ONNX/tokenizer resources are cached. Blank
     * falls back to {@link TransformersEmbeddingModel}'s default
     * ({@code ${java.io.tmpdir}/spring-ai-onnx-model}). On the ephemeral demo
     * the tmpfs default is wiped on reset, so the model re-downloads on each
     * boot — set this to a persistent volume to keep it across restarts.
     */
    private final String cacheDirectory;

    public TurTransformersEmbeddingModelBuilder(
            @Value("${turing.genai.embedding.local.cache-dir:}") String cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    /**
     * Resolves (building + caching on first use) the ONNX embedding model for
     * the given ({@code modelPath}, {@code tokenizerPath}) pair. Both accept
     * {@code classpath:}, {@code file:} or {@code https:} URIs.
     *
     * @throws IllegalStateException when either path is blank or the model
     *                               fails to initialize
     */
    public EmbeddingModel resolve(String modelPath, String tokenizerPath) {
        if (!StringUtils.hasText(modelPath) || !StringUtils.hasText(tokenizerPath)) {
            throw new IllegalStateException(
                    "A local ONNX embedding model requires both a model (.onnx) and a tokenizer (.json) resource.");
        }
        return cache.computeIfAbsent(modelPath + "|" + tokenizerPath,
                k -> build(modelPath, tokenizerPath));
    }

    private EmbeddingModel build(String modelPath, String tokenizerPath) {
        try {
            TransformersEmbeddingModel model = new TransformersEmbeddingModel();
            model.setModelResource(modelPath);
            model.setTokenizerResource(tokenizerPath);
            if (StringUtils.hasText(cacheDirectory)) {
                model.setResourceCacheDirectory(cacheDirectory);
            }
            // sentence-transformers models are trained with padding on.
            model.setTokenizerOptions(Map.of("padding", "true"));
            // Downloads (once) + loads the ONNX session; may take seconds on a
            // cold cache. Done here so the cost is paid at warm-up, not per turn.
            model.afterPropertiesSet();
            log.info("Initialized ONNX embedding model (model='{}', tokenizer='{}').",
                    modelPath, tokenizerPath);
            return model;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to initialize ONNX embedding model '%s': %s"
                            .formatted(modelPath, e.getMessage()), e);
        }
    }
}

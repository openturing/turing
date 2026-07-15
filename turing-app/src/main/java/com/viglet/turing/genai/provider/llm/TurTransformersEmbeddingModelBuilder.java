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

import java.nio.file.Paths;
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
     * Owns resilient remote artifact acquisition (T656): bounded retry/backoff,
     * integrity check, actionable diagnostics, and a persistent cache directory.
     * Remote model/tokenizer URLs are resolved to local {@code file:} URIs
     * <i>before</i> they reach {@link TransformersEmbeddingModel}, so the ONNX
     * runtime never performs its own (unhardened) fetch.
     */
    private final TurOnnxModelDownloader downloader;

    /** Env var / system property DJL reads to locate its model + engine cache. */
    private static final String DJL_CACHE_ENV = "DJL_CACHE_DIR";
    /** Sub-directory (under {@code store/}) for the DJL cache when unconfigured. */
    private static final String DJL_CACHE_SUBDIR = "djl-cache";

    public TurTransformersEmbeddingModelBuilder(TurOnnxModelDownloader downloader,
            @Value("${turing.genai.embedding.local.djl-cache-dir:}") String djlCacheDir) {
        this.downloader = downloader;
        ensureDjlCacheDir(djlCacheDir);
    }

    /**
     * Default DJL's model/engine cache to {@code store/djl-cache} when the
     * operator hasn't pinned it via the {@code DJL_CACHE_DIR} env var / system
     * property. DJL (used by {@link TransformersEmbeddingModel} for the ONNX
     * runtime + tokenizer downloads) otherwise writes to {@code ~/.djl.ai}, which
     * is volatile in an ephemeral container. {@code store/djl-cache} lives next to
     * the H2 db, assets, queue, and the ONNX cache — and matches the
     * {@code /app/store/djl-cache} directory the runtime image pre-creates.
     */
    private static void ensureDjlCacheDir(String configured) {
        if (StringUtils.hasText(System.getProperty(DJL_CACHE_ENV))
                || StringUtils.hasText(System.getenv(DJL_CACHE_ENV))) {
            return; // operator pinned it explicitly — respect it
        }
        String dir = StringUtils.hasText(configured)
                ? configured.trim()
                : Paths.get(TurOnnxModelDownloader.DEFAULT_STORE_DIR, DJL_CACHE_SUBDIR).toString();
        System.setProperty(DJL_CACHE_ENV, dir);
        log.info("DJL_CACHE_DIR not set — defaulting DJL model/engine cache to '{}'.", dir);
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
        // Keyed by the ORIGINAL (URL) paths so two callers that resolve to the
        // same artifacts share one cached model, regardless of the local file.
        return cache.computeIfAbsent(modelPath + "|" + tokenizerPath,
                k -> build(modelPath, tokenizerPath));
    }

    private EmbeddingModel build(String modelPath, String tokenizerPath) {
        // Acquire remote artifacts ourselves (retry + integrity + persistent
        // cache) and hand the ONNX runtime local file: URIs; classpath:/file:
        // paths pass through.
        String localModel = downloader.ensureLocal(modelPath);
        String localTokenizer = downloader.ensureLocal(tokenizerPath);
        try {
            TransformersEmbeddingModel model = new TransformersEmbeddingModel();
            model.setModelResource(localModel);
            model.setTokenizerResource(localTokenizer);
            // Point Spring AI's own cache (used only when it must stage a
            // classpath: resource to disk) at the same persistent directory.
            model.setResourceCacheDirectory(downloader.cacheDirectory());
            // sentence-transformers models are trained with padding on.
            model.setTokenizerOptions(Map.of("padding", "true"));
            // Loads the ONNX session; may take seconds. Done here so the cost is
            // paid at warm-up, not per turn. The download already happened above.
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

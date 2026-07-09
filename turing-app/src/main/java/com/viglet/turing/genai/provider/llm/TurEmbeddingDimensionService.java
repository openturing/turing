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

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * Detects a candidate embedding model's dimension and warns when it differs
 * from the platform's current default (T627). Embedding dimension is
 * load-bearing: change it and every already-indexed vector is invalid until a
 * reindex, and the vector store's field must match — so the picker surfaces the
 * mismatch <b>before</b> the model is saved.
 *
 * <p>Dimension is probed from the HuggingFace {@code config.json}
 * ({@code hidden_size}) via {@link TurHuggingFaceRepoResolver#probeDimensions}
 * — free, no key, no download. The current default's dimension is probed the
 * same way when the default is itself a {@code HUGGINGFACE} model; otherwise it
 * is unknown ({@code null}) and no mismatch is asserted (remote LLM embedding
 * dimensions would need a billed one-shot embed — out of scope here).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurEmbeddingDimensionService {

    /**
     * @param dimensions        the candidate model's embedding dimension (null = unknown)
     * @param defaultDimensions the current default model's dimension (null = unknown)
     * @param differs           both known AND unequal — a save means reindex + matching store
     */
    public record DimensionCheck(Integer dimensions, Integer defaultDimensions, boolean differs) {
    }

    private final TurHuggingFaceRepoResolver repoResolver;
    private final TurGlobalSettingsService globalSettingsService;
    private final TurEmbeddingModelRepository embeddingModelRepository;

    public TurEmbeddingDimensionService(TurHuggingFaceRepoResolver repoResolver,
            TurGlobalSettingsService globalSettingsService,
            TurEmbeddingModelRepository embeddingModelRepository) {
        this.repoResolver = repoResolver;
        this.globalSettingsService = globalSettingsService;
        this.embeddingModelRepository = embeddingModelRepository;
    }

    /** Dimension of a HuggingFace repo vs. the current default embedding model. */
    public DimensionCheck checkHuggingFace(String repoId) {
        Integer dimensions = repoResolver.probeDimensions(repoId);
        Integer defaultDimensions = currentDefaultDimensions();
        boolean differs = dimensions != null && defaultDimensions != null
                && !dimensions.equals(defaultDimensions);
        return new DimensionCheck(dimensions, defaultDimensions, differs);
    }

    /**
     * Dimension of the current global default embedding model, or {@code null}
     * when there is none, it is not a {@code HUGGINGFACE} model, or the probe
     * fails. Best-effort — only used to warn, never to block.
     */
    private Integer currentDefaultDimensions() {
        String defaultId = globalSettingsService.getDefaultEmbeddingModelId();
        if (!StringUtils.hasText(defaultId)) {
            return null;
        }
        TurEmbeddingModel current = embeddingModelRepository.findById(defaultId).orElse(null);
        if (current == null
                || !TurHuggingFaceEmbeddingModelFactory.PROVIDER_TYPE.equalsIgnoreCase(current.getProviderType())
                || !StringUtils.hasText(current.getModelReference())) {
            return null;
        }
        return repoResolver.probeDimensions(current.getModelReference());
    }
}

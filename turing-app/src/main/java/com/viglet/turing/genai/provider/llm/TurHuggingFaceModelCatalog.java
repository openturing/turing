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

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the bundled, curated {@code llm/huggingface-embeddings-catalog.json}
 * once at startup — the static fallback for the HuggingFace embedding model
 * picker (T624). Served when the live {@code huggingface.co/api/models} query
 * is unavailable (offline, rate-limited). Mirrors {@link TurLlmModelCatalog}.
 *
 * <p>Every entry is a known-good ONNX sentence-transformers repo, so
 * {@code onnxVerified} is implicitly {@code true} for catalog entries (no live
 * tree probe needed). The catalog is a bundled resource — refreshed on
 * redeploy, not at runtime — so it is loaded once into an immutable list.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurHuggingFaceModelCatalog {

    private static final String CATALOG_RESOURCE = "llm/huggingface-embeddings-catalog.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<TurHuggingFaceModelOption> models = List.of();

    @PostConstruct
    void load() {
        List<TurHuggingFaceModelOption> parsed = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource(CATALOG_RESOURCE).getInputStream());
            JsonNode list = root.path("models");
            if (list.isArray()) {
                list.forEach(node -> {
                    String repoId = node.path("repoId").asText(null);
                    if (repoId != null && !repoId.isBlank()) {
                        Integer dims = node.has("dimensions") && node.get("dimensions").isInt()
                                ? node.get("dimensions").asInt() : null;
                        parsed.add(TurHuggingFaceModelOption.catalog(repoId, dims));
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Could not load HuggingFace embeddings catalog '{}': {}", CATALOG_RESOURCE, e.getMessage());
        }
        this.models = List.copyOf(parsed);
        log.info("Loaded static HuggingFace embeddings catalog with {} models", models.size());
    }

    /** The curated models, optionally filtered by a case-insensitive substring. */
    public List<TurHuggingFaceModelOption> staticModels(String query) {
        if (query == null || query.isBlank()) {
            return models;
        }
        String needle = query.toLowerCase();
        return models.stream()
                .filter(m -> m.repoId().toLowerCase().contains(needle))
                .toList();
    }
}

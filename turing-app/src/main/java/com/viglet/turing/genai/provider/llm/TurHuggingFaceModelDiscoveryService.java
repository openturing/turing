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
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the selectable models for the HuggingFace embedding model picker
 * (T624). Queries the public {@code huggingface.co/api/models} endpoint filtered
 * to {@code sentence-transformers} / feature-extraction repos, ranks by
 * downloads, and — crucially — <b>verifies ONNX availability</b> (via
 * {@link TurHuggingFaceRepoResolver#hasOnnxAndTokenizer(String)}) so the picker
 * only offers models that actually load in-process. When the live API is
 * unreachable it falls back to the bundled {@link TurHuggingFaceModelCatalog}.
 *
 * <p>Deliberately mirrors the T577 {@link TurLlmModelDiscoveryService} shape:
 * a {@link Result} carrying {@link Source} {@code LIVE}/{@code CATALOG}/
 * {@code NONE} plus the models, so the UI can badge the source.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurHuggingFaceModelDiscoveryService {

    /** Which source produced the model list, for UI badging. */
    public enum Source { LIVE, CATALOG, NONE }

    /** The picker payload: where the list came from + the models themselves. */
    public record Result(Source source, List<TurHuggingFaceModelOption> models) {
        static Result of(Source source, List<TurHuggingFaceModelOption> models) {
            return new Result(source, models);
        }
    }

    private final TurHuggingFaceRepoResolver repoResolver;
    private final TurHuggingFaceModelCatalog catalog;
    private final String baseUrl;
    /** How many top-ranked live candidates to fetch before ONNX-probing. */
    private final int candidateLimit;
    /** How many ONNX-verified models to return (and stop probing at). */
    private final int resultLimit;

    public TurHuggingFaceModelDiscoveryService(TurHuggingFaceRepoResolver repoResolver,
            TurHuggingFaceModelCatalog catalog,
            @Value("${turing.genai.embedding.huggingface.base-url:https://huggingface.co}") String baseUrl,
            @Value("${turing.genai.embedding.huggingface.candidate-limit:30}") int candidateLimit,
            @Value("${turing.genai.embedding.huggingface.result-limit:15}") int resultLimit) {
        this.repoResolver = repoResolver;
        this.catalog = catalog;
        this.baseUrl = normalize(baseUrl);
        this.candidateLimit = candidateLimit;
        this.resultLimit = resultLimit;
    }

    /**
     * Lists ONNX-verified HuggingFace embedding models matching {@code query}.
     * Live results are preferred; on an empty/unreachable live list the bundled
     * catalog is served (filtered by the same query).
     */
    public Result listModels(String query) {
        List<TurHuggingFaceModelOption> live = liveModels(query);
        if (!live.isEmpty()) {
            return Result.of(Source.LIVE, live);
        }
        List<TurHuggingFaceModelOption> staticModels = catalog.staticModels(query);
        return Result.of(staticModels.isEmpty() ? Source.NONE : Source.CATALOG, staticModels);
    }

    private List<TurHuggingFaceModelOption> liveModels(String query) {
        List<Map<String, Object>> candidates = fetchCandidates(query);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<TurHuggingFaceModelOption> verified = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            String repoId = asText(candidate.get("id"), asText(candidate.get("modelId"), null));
            if (!StringUtils.hasText(repoId)) {
                continue;
            }
            // Only offer models that actually expose a loadable ONNX + tokenizer.
            if (!repoResolver.hasOnnxAndTokenizer(repoId)) {
                continue;
            }
            verified.add(new TurHuggingFaceModelOption(repoId, repoId,
                    asLong(candidate.get("downloads")), asLong(candidate.get("likes")), null, true));
            if (verified.size() >= resultLimit) {
                break;
            }
        }
        return verified;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchCandidates(String query) {
        try {
            List<?> body = RestClient.create(baseUrl).get()
                    .uri(uriBuilder -> uriBuilder.path("/api/models")
                            .queryParam("filter", "sentence-transformers")
                            .queryParam("sort", "downloads")
                            .queryParam("direction", "-1")
                            .queryParam("limit", candidateLimit)
                            .queryParamIfPresent("search",
                                    StringUtils.hasText(query) ? java.util.Optional.of(query.trim())
                                            : java.util.Optional.empty())
                            .build())
                    .retrieve()
                    .body(List.class);
            if (body == null) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : body) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        } catch (Exception e) {
            log.debug("HuggingFace live model listing unavailable (query='{}'): {}", query, e.getMessage());
            return List.of();
        }
    }

    private static String asText(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    private static String normalize(String url) {
        String trimmed = url == null ? "https://huggingface.co" : url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}

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
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves a HuggingFace <b>repo id</b> (e.g.
 * {@code sentence-transformers/all-MiniLM-L6-v2}) into the canonical
 * {@code resolve/main/…} download URLs for its ONNX model artifact and
 * {@code tokenizer.json}, so the {@code HUGGINGFACE} embedding provider (T623)
 * can hand them to the shared {@link TurTransformersEmbeddingModelBuilder}.
 *
 * <p>
 * The core observation (§XXXVI): a "huggingface.co provider" is not a new
 * runtime — it is the <i>same</i> in-process
 * {@link org.springframework.ai.transformers.TransformersEmbeddingModel} the
 * local path already downloads from {@code huggingface.co} URLs, with the URL
 * typing replaced by picking a repo id. This resolver bridges the two: it
 * probes the repo tree (HF {@code /api/models/{repoId}/tree/main}) to locate the
 * real artifact paths, handling the two layouts sentence-transformers repos use
 * — the ONNX under an {@code onnx/} subfolder (preferred) vs at the repo root.
 * When the tree probe is unavailable (offline, private repo, rate-limited) it
 * falls back to the conventional {@code onnx/model.onnx} + {@code tokenizer.json}
 * layout — fail-open, exactly like the local path.
 *
 * <p>
 * The same tree listing powers T624's ONNX-availability probe (does the repo
 * actually expose a loadable {@code .onnx} + {@code tokenizer.json}?), so both
 * the runtime factory and the discovery service share one resolver.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurHuggingFaceRepoResolver {

    /** The two download URLs a repo id resolves to. */
    public record HfArtifacts(String onnxUrl, String tokenizerUrl) {
    }

    private final String baseUrl;

    public TurHuggingFaceRepoResolver(
            @Value("${turing.genai.embedding.huggingface.base-url:https://huggingface.co}") String baseUrl) {
        this.baseUrl = normalize(baseUrl);
    }

    /**
     * Resolves the repo id into ({@code .onnx}, {@code tokenizer.json})
     * {@code resolve/main/…} URLs, probing the repo tree to pick the real ONNX
     * path (preferring {@code onnx/model.onnx}), falling back to the
     * conventional layout when the tree is unavailable.
     */
    public HfArtifacts resolve(String repoId) {
        String repo = cleanRepoId(repoId);
        if (!StringUtils.hasText(repo)) {
            throw new IllegalStateException("HuggingFace embedding model requires a repo id in modelReference.");
        }
        List<String> tree = listTree(repo);
        String onnxPath = pickOnnxPath(tree).orElse("onnx/model.onnx");
        String tokenizerPath = pickTokenizerPath(tree).orElse("tokenizer.json");
        return new HfArtifacts(resolveUrl(repo, onnxPath), resolveUrl(repo, tokenizerPath));
    }

    /**
     * @return {@code true} when the repo tree exposes both an {@code .onnx}
     *         artifact and {@code tokenizer.json} (so it will load in-process).
     *         Used by T624 to only offer models that actually load. Fail-open:
     *         an unreachable tree returns {@code false} (caller decides).
     */
    public boolean hasOnnxAndTokenizer(String repoId) {
        List<String> tree = listTree(cleanRepoId(repoId));
        return pickOnnxPath(tree).isPresent() && pickTokenizerPath(tree).isPresent();
    }

    /** The public repo tree paths, or an empty list when the probe fails. */
    public List<String> listTree(String repoId) {
        String repo = cleanRepoId(repoId);
        if (!StringUtils.hasText(repo)) {
            return List.of();
        }
        try {
            List<?> entries = RestClient.create(baseUrl).get()
                    .uri("/api/models/{repo}/tree/main?recursive=true", repo)
                    .retrieve()
                    .body(List.class);
            if (entries == null) {
                return List.of();
            }
            List<String> paths = new ArrayList<>();
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> node && "file".equals(String.valueOf(node.get("type")))) {
                    String path = String.valueOf(node.get("path"));
                    if (StringUtils.hasText(path) && !"null".equals(path)) {
                        paths.add(path);
                    }
                }
            }
            return paths;
        } catch (Exception e) {
            log.debug("HuggingFace tree probe failed for repo '{}': {}", repo, e.getMessage());
            return List.of();
        }
    }

    /**
     * Picks the ONNX artifact path from a tree listing: prefer the canonical
     * {@code onnx/model.onnx}, then a root {@code model.onnx}, then any
     * non-quantized {@code .onnx}, then any {@code .onnx} at all.
     */
    Optional<String> pickOnnxPath(List<String> tree) {
        List<String> onnx = tree.stream().filter(p -> p.toLowerCase().endsWith(".onnx")).toList();
        if (onnx.isEmpty()) {
            return Optional.empty();
        }
        return onnx.stream().filter("onnx/model.onnx"::equalsIgnoreCase).findFirst()
                .or(() -> onnx.stream().filter("model.onnx"::equalsIgnoreCase).findFirst())
                .or(() -> onnx.stream().filter(p -> !p.toLowerCase().contains("quant")).findFirst())
                .or(() -> onnx.stream().findFirst());
    }

    Optional<String> pickTokenizerPath(List<String> tree) {
        return tree.stream().filter(p -> p.equalsIgnoreCase("tokenizer.json")).findFirst()
                .or(() -> tree.stream().filter(p -> p.toLowerCase().endsWith("/tokenizer.json")).findFirst());
    }

    private String resolveUrl(String repo, String path) {
        return "%s/%s/resolve/main/%s".formatted(baseUrl, repo, path);
    }

    /** Strips a leading {@code huggingface.co/} host or surrounding slashes. */
    private String cleanRepoId(String repoId) {
        if (!StringUtils.hasText(repoId)) {
            return null;
        }
        String repo = repoId.trim();
        int host = repo.indexOf("huggingface.co/");
        if (host >= 0) {
            repo = repo.substring(host + "huggingface.co/".length());
        }
        while (repo.startsWith("/")) {
            repo = repo.substring(1);
        }
        while (repo.endsWith("/")) {
            repo = repo.substring(0, repo.length() - 1);
        }
        return repo;
    }

    private static String normalize(String url) {
        String trimmed = url == null ? "https://huggingface.co" : url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}

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

    /**
     * Separator embedding a chosen ONNX artifact variant into {@code modelReference}
     * (T628) — e.g. {@code sentence-transformers/all-MiniLM-L6-v2::onnx/model_quantized.onnx}.
     * Keeps the "no new entity columns" design: the variant rides in the same
     * column as the repo id.
     */
    public static final String VARIANT_SEPARATOR = "::";

    private final String baseUrl;

    public TurHuggingFaceRepoResolver(
            @Value("${turing.genai.embedding.huggingface.base-url:https://huggingface.co}") String baseUrl) {
        this.baseUrl = normalize(baseUrl);
    }

    /**
     * Resolves a {@code modelReference} into ({@code .onnx}, {@code tokenizer.json})
     * {@code resolve/main/…} URLs. The reference is either a bare repo id or a
     * repo id with a chosen artifact variant appended
     * ({@code repoId::onnx/model_quantized.onnx}, T628). When a variant is given
     * it is used verbatim; otherwise the repo tree is probed to pick the real
     * ONNX path (preferring {@code onnx/model.onnx}), falling back to the
     * conventional layout when the tree is unavailable. The tokenizer is always
     * auto-resolved.
     */
    public HfArtifacts resolve(String modelReference) {
        String repo = cleanRepoId(modelReference);
        if (!StringUtils.hasText(repo)) {
            throw new IllegalStateException("HuggingFace embedding model requires a repo id in modelReference.");
        }
        String explicitVariant = variantOf(modelReference);
        List<String> tree = listTree(repo);
        String onnxPath = StringUtils.hasText(explicitVariant)
                ? explicitVariant
                : pickOnnxPath(tree).orElse("onnx/model.onnx");
        String tokenizerPath = pickTokenizerPath(tree).orElse("tokenizer.json");
        return new HfArtifacts(resolveUrl(repo, onnxPath), resolveUrl(repo, tokenizerPath));
    }

    /**
     * Lists the ONNX artifacts a repo ships (T628), ordered so the conventional
     * default comes first (canonical {@code onnx/model.onnx}, then a root
     * {@code model.onnx}, then non-quantized, then quantized/other), letting the
     * picker offer a size/latency-vs-accuracy choice. Empty when the tree is
     * unreachable or ships no {@code .onnx}.
     */
    public List<String> listOnnxVariants(String repoId) {
        return listTree(cleanRepoId(repoId)).stream()
                .filter(p -> p.toLowerCase().endsWith(".onnx"))
                .sorted(java.util.Comparator.comparingInt(TurHuggingFaceRepoResolver::variantRank)
                        .thenComparing(java.util.Comparator.naturalOrder()))
                .toList();
    }

    /** Lower rank sorts first: the auto-picked default, then plain, then quantized. */
    private static int variantRank(String path) {
        String p = path.toLowerCase();
        if (p.equals("onnx/model.onnx")) {
            return 0;
        }
        if (p.equals("model.onnx")) {
            return 1;
        }
        boolean quantized = p.contains("quant") || p.contains("int8") || p.contains("uint8")
                || p.contains("q4") || p.contains("bnb") || p.contains("fp16");
        return quantized ? 3 : 2;
    }

    /** The chosen ONNX artifact appended to a reference, or {@code null} when none. */
    private String variantOf(String modelReference) {
        if (modelReference == null) {
            return null;
        }
        int sep = modelReference.indexOf(VARIANT_SEPARATOR);
        if (sep < 0) {
            return null;
        }
        String variant = modelReference.substring(sep + VARIANT_SEPARATOR.length()).trim();
        return StringUtils.hasText(variant) ? variant : null;
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

    /**
     * Probes the model's embedding dimension from its {@code config.json}
     * ({@code hidden_size}, with common aliases) — free, no key, no download
     * (T627). Returns {@code null} when the config is unreachable or declares no
     * recognizable dimension (the caller then treats dimension as unknown).
     */
    public Integer probeDimensions(String repoId) {
        String repo = cleanRepoId(repoId);
        if (!StringUtils.hasText(repo)) {
            return null;
        }
        try {
            Map<?, ?> config = RestClient.create(baseUrl).get()
                    .uri("/{repo}/resolve/main/config.json", repo)
                    .retrieve()
                    .body(Map.class);
            if (config == null) {
                return null;
            }
            for (String key : new String[] {"hidden_size", "hidden_dim", "d_model", "dim", "n_embd"}) {
                Object value = config.get(key);
                if (value instanceof Number number) {
                    return number.intValue();
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("HuggingFace dimension probe failed for repo '{}': {}", repo, e.getMessage());
            return null;
        }
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

    /**
     * Extracts the bare repo id: drops any {@code ::variant} suffix (T628), a
     * leading {@code huggingface.co/} host, and surrounding slashes.
     */
    private String cleanRepoId(String repoId) {
        if (!StringUtils.hasText(repoId)) {
            return null;
        }
        String repo = repoId.trim();
        int sep = repo.indexOf(VARIANT_SEPARATOR);
        if (sep >= 0) {
            repo = repo.substring(0, sep);
        }
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

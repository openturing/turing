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
package com.viglet.turing.genai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.TurRagContextBuilder.RagInfrastructure;
import com.viglet.turing.genai.provider.store.TurStoreChunkPage;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Auto-extracts metadata filters from a free-text user query so RAG can switch
 * from "top-K most similar chunks" to "all chunks matching X" when the user
 * is clearly asking to list / enumerate / count items of a category.
 *
 * <p>Two-stage process:</p>
 * <ol>
 *   <li>{@link #getAvailableFacets(RagInfrastructure)} — samples the vector
 *       store via {@code listChunks(page=0, pageSize=200)} and aggregates
 *       distinct values per metadata key. Cached per collection for
 *       {@link #CACHE_TTL_MILLIS}.</li>
 *   <li>{@link #extractFilters(ChatModel, String, Map)} — sends the user
 *       query plus the discovered facet vocabulary to the chat model and asks
 *       for a strict JSON envelope mapping facet keys to chosen values.</li>
 * </ol>
 *
 * <p>System / RAG-internal keys ({@code source_id}, marker keys, dates, URLs)
 * are filtered out before reaching the LLM so the prompt stays focused on
 * what users would actually filter by (categories, types, instructors, …).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Service
public class TurRagFacetExtractor {

    /** How long an aggregated facet snapshot is reused before being recomputed. */
    private static final long CACHE_TTL_MILLIS = 10L * 60L * 1000L;
    /** Total chunks scanned to build the facet vocabulary. Higher = more complete, slower. */
    private static final int FACET_SAMPLE_SIZE = 500;
    /** Page size used when paginating {@code listChunks} for the facet scan. */
    private static final int FACET_PAGE_SIZE = 100;
    /**
     * Drops facets whose distinct-value count is outside this range. Below the
     * minimum it isn't a meaningful filter; above the maximum the LLM prompt
     * blows up and matching becomes brittle (think "url" or "modification_date").
     */
    private static final int MIN_VALUES_PER_FACET = 1;
    private static final int MAX_VALUES_PER_FACET = 100;
    /**
     * Field names excluded from the facet vocabulary because they are
     * RAG-internal (used elsewhere) or not user-meaningful as filters.
     */
    private static final Set<String> EXCLUDED_KEYS = Set.of(
            "source_id", "url", "modification_date", "publication_date",
            "sites", "locale", "source_apps");

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ConcurrentHashMap<String, CachedFacets> cache = new ConcurrentHashMap<>();

    private record CachedFacets(Map<String, List<String>> values, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /**
     * @return distinct values per metadata key found in the collection,
     *         minus internal/system keys. Empty when the store is empty or
     *         the scan failed.
     */
    public Map<String, List<String>> getAvailableFacets(RagInfrastructure infra) {
        if (infra == null || infra.collectionName() == null) {
            return Map.of();
        }
        String key = infra.collectionName();
        CachedFacets cached = cache.get(key);
        if (cached != null && !cached.expired()) {
            return cached.values();
        }

        Map<String, Set<String>> aggregator = new LinkedHashMap<>();
        try {
            int page = 0;
            int collected = 0;
            while (collected < FACET_SAMPLE_SIZE) {
                TurStoreChunkPage chunkPage = infra.storeProvider().listChunks(
                        infra.storeInstance(), infra.storeCredential(),
                        infra.collectionName(), page, FACET_PAGE_SIZE, null);
                if (chunkPage == null || chunkPage.chunks() == null || chunkPage.chunks().isEmpty()) {
                    break;
                }
                for (TurStoreChunkPage.Chunk chunk : chunkPage.chunks()) {
                    if (chunk.metadata() == null) {
                        continue;
                    }
                    for (var entry : chunk.metadata().entrySet()) {
                        String k = entry.getKey();
                        Object v = entry.getValue();
                        if (k == null || EXCLUDED_KEYS.contains(k) || isInternalKey(k) || v == null) {
                            continue;
                        }
                        Set<String> set = aggregator.computeIfAbsent(k, ignored -> new LinkedHashSet<>());
                        if (v instanceof Collection<?> col) {
                            for (Object o : col) {
                                if (o != null) {
                                    set.add(o.toString());
                                }
                            }
                        } else if (v instanceof CharSequence || v instanceof Number || v instanceof Boolean) {
                            set.add(v.toString());
                        }
                    }
                    collected++;
                    if (collected >= FACET_SAMPLE_SIZE) {
                        break;
                    }
                }
                if (chunkPage.chunks().size() < FACET_PAGE_SIZE) {
                    break;
                }
                page++;
            }
        } catch (UnsupportedOperationException e) {
            // Store provider doesn't implement listChunks (older deploys); no
            // facets means we just skip auto-extraction and behave as before.
            log.debug("Facet extraction skipped: store does not support listChunks ({})", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to scan facets for collection '{}': {}", key, e.getMessage());
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (var entry : aggregator.entrySet()) {
            int size = entry.getValue().size();
            if (size < MIN_VALUES_PER_FACET || size > MAX_VALUES_PER_FACET) {
                continue;
            }
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        cache.put(key, new CachedFacets(result, System.currentTimeMillis() + CACHE_TTL_MILLIS));
        log.debug("Cached {} facet keys for collection '{}'", result.size(), key);
        return result;
    }

    /**
     * Asks the chat model to map the user query onto the discovered facets
     * and returns the resulting filter map. Returns an empty map (no filters)
     * on any failure — the caller falls back to plain similarity search.
     */
    public Map<String, List<String>> extractFilters(ChatModel chatModel, String query,
            Map<String, List<String>> availableFacets) {
        if (chatModel == null || availableFacets == null || availableFacets.isEmpty()
                || query == null || query.isBlank()) {
            return Map.of();
        }
        try {
            String facetsJson = JSON.writeValueAsString(availableFacets);
            String systemPrompt = """
                    You analyze user questions for a retrieval system and extract metadata filter
                    selections that should narrow the search.

                    Available filters (key → list of allowed values, JSON):
                    %s

                    Respond ONLY with valid JSON in this exact shape:
                    {"filters": {"key": ["value", ...]}}

                    Rules:
                    - Only include a filter when the user clearly references a value present in the
                      available list above (semantic match: e.g. "executive education" matches a value
                      like "Educação Executiva" if both refer to the same concept).
                    - Match the value EXACTLY as listed (preserve case, accents, spacing).
                    - For list / enumeration questions ("list", "show me all", "quais", "todos"),
                      always extract the relevant filter when one matches.
                    - Do NOT invent values that are not in the list.
                    - If no filter applies, respond with {"filters": {}}.
                    - Return ONLY the JSON object — no prose, no markdown, no commentary.
                    """.formatted(facetsJson);

            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(query)));
            String response = chatModel.call(prompt).getResult().getOutput().getText();
            Map<String, List<String>> parsed = parseFilters(response);
            // Final defensive pass: drop keys/values not present in availableFacets so a
            // hallucinating model can't widen the filter to something the store rejects.
            return sanitize(parsed, availableFacets);
        } catch (Exception e) {
            log.warn("Failed to auto-extract filters from query '{}': {}", query, e.getMessage());
            return Map.of();
        }
    }

    private Map<String, List<String>> sanitize(Map<String, List<String>> filters,
            Map<String, List<String>> availableFacets) {
        if (filters == null || filters.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (var entry : filters.entrySet()) {
            List<String> allowed = availableFacets.get(entry.getKey());
            if (allowed == null) {
                continue;
            }
            Set<String> allowedSet = new LinkedHashSet<>(allowed);
            List<String> kept = entry.getValue().stream()
                    .filter(Objects::nonNull)
                    .filter(allowedSet::contains)
                    .toList();
            if (!kept.isEmpty()) {
                out.put(entry.getKey(), kept);
            }
        }
        return out;
    }

    private Map<String, List<String>> parseFilters(String response) {
        if (response == null || response.isBlank()) {
            return Map.of();
        }
        String json = extractJsonObject(response);
        if (json == null) {
            return Map.of();
        }
        try {
            Map<String, Object> outer = JSON.readValue(json, MAP_TYPE);
            Object filtersObj = outer.get("filters");
            if (!(filtersObj instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, List<String>> result = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                String key = entry.getKey() == null ? null : entry.getKey().toString();
                if (key == null || key.isBlank()) {
                    continue;
                }
                Object value = entry.getValue();
                List<String> values = new ArrayList<>();
                if (value instanceof Collection<?> col) {
                    for (Object o : col) {
                        if (o != null) {
                            values.add(o.toString());
                        }
                    }
                } else if (value != null) {
                    values.add(value.toString());
                }
                if (!values.isEmpty()) {
                    result.put(key, values);
                }
            }
            return result;
        } catch (Exception e) {
            log.debug("Could not parse facet-extraction LLM response: {}", response);
            return Map.of();
        }
    }

    /**
     * Extracts the outermost JSON object from a model response — the model
     * sometimes wraps it in {@code ```json ... ```} fences or adds a brief
     * intro despite the prompt telling it not to.
     */
    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    /**
     * Filters out chunk-id marker keys generated by {@code TurChromaVectorStore}
     * (pattern: {@code <field>__<sha1[:12]>}) so the LLM doesn't see noisy
     * hash-suffixed entries.
     */
    private boolean isInternalKey(String key) {
        int idx = key.lastIndexOf("__");
        if (idx < 0 || idx + 2 + 12 != key.length()) {
            return false;
        }
        for (int i = idx + 2; i < key.length(); i++) {
            char c = key.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}

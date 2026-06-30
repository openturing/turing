/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.rerank;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.google.auth.oauth2.GoogleCredentials;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T521 / §XXVIII.17 — reranks via the managed Google Vertex AI Ranking API
 * (Discovery Engine ranking config), the GCP-native Block N
 * {@link TurRagRerankStrategy}. Calls the REST {@code rankingConfigs:rank}
 * endpoint with a service-account bearer token (the {@code GLOBAL_RAG_SN_RERANK_API_KEY}
 * holds the service-account JSON; blank → Application Default Credentials).
 *
 * <p>Config: {@code GLOBAL_RAG_SN_RERANK_VERTEX_PROJECT} (required),
 * {@code GLOBAL_RAG_SN_RERANK_VERTEX_LOCATION} (default {@code global}), and the
 * ranking model {@code GLOBAL_RAG_SN_RERANK_MODEL} (default
 * {@code semantic-ranker-default-004}).
 *
 * <p>Fail-open like the rest of Block N: a missing project / credentials, an
 * HTTP error, or a garbled response returns an empty list and
 * {@code TurRagReranker} keeps retrieval order.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurVertexAiRerankStrategy implements TurRagRerankStrategy {

    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final String DEFAULT_MODEL = "semantic-ranker-default-004";
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final TurGlobalSettingsService globalSettingsService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    public TurVertexAiRerankStrategy(TurGlobalSettingsService globalSettingsService) {
        this.globalSettingsService = globalSettingsService;
    }

    @Override
    public TurRagRerankStrategyType getType() {
        return TurRagRerankStrategyType.VERTEX_AI;
    }

    @Override
    public List<Document> rerank(TurRagRerankRequest request) {
        String project = globalSettingsService.getRagSnRerankVertexProject();
        if (!StringUtils.hasText(project)) {
            log.debug("[RAG] Vertex reranker: no project configured; keeping retrieval order");
            return List.of();
        }
        String location = globalSettingsService.getRagSnRerankVertexLocation();
        String configuredModel = globalSettingsService.getRagSnRerankModel();
        String model = StringUtils.hasText(configuredModel) ? configuredModel.trim() : DEFAULT_MODEL;

        List<Document> candidates = request.candidates();
        try {
            String token = accessToken();
            String url = "https://discoveryengine.googleapis.com/v1/projects/" + project
                    + "/locations/" + location + "/rankingConfigs/default_ranking_config:rank";
            JsonNode root = postRank(url, token, buildBody(model, request, candidates));
            return TurRerankIndexMapper.map(candidates, orderFrom(root, candidates.size()), request.topK());
        } catch (RuntimeException e) {
            log.warn("[RAG] Vertex reranker failed ({}); keeping retrieval order", e.getMessage());
            return List.of();
        }
    }

    /** Builds the {@code rank} request body: model, query, topN, and id/content records. */
    private ObjectNode buildBody(String model, TurRagRerankRequest request, List<Document> candidates) {
        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("model", model);
        body.put("query", request.query());
        body.put("topN", Math.min(request.topK(), candidates.size()));
        ArrayNode records = body.putArray("records");
        for (int i = 0; i < candidates.size(); i++) {
            ObjectNode record = records.addObject();
            record.put("id", String.valueOf(i));
            record.put("content", candidates.get(i).getText() == null ? "" : candidates.get(i).getText());
        }
        return body;
    }

    /**
     * Extracts the ordered candidate indices from the {@code records[]} response
     * (Vertex returns them sorted by score descending; each {@code id} is the
     * request index). In-bounds + deduped; package-private for unit testing.
     */
    List<Integer> orderFrom(JsonNode root, int candidateCount) {
        if (root == null) {
            return List.of();
        }
        JsonNode records = root.path("records");
        if (!records.isArray()) {
            return List.of();
        }
        List<Integer> order = new ArrayList<>(records.size());
        for (JsonNode record : records) {
            int idx = parseIndex(record.path("id").asString(""));
            if (idx >= 0 && idx < candidateCount && !order.contains(idx)) {
                order.add(idx);
            }
        }
        return order;
    }

    private static int parseIndex(String id) {
        if (id == null || id.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(id.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private JsonNode postRank(String url, String token, ObjectNode body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Vertex rank HTTP " + response.statusCode());
            }
            return OBJECT_MAPPER.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Vertex rank call interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Vertex rank call failed: " + e.getMessage(), e);
        }
    }

    /** A cloud-platform OAuth token from the configured service-account JSON, else ADC. */
    private String accessToken() {
        try {
            String serviceAccountJson = globalSettingsService.getRagSnRerankApiKey();
            GoogleCredentials credentials = StringUtils.hasText(serviceAccountJson)
                    ? GoogleCredentials.fromStream(new ByteArrayInputStream(
                            serviceAccountJson.getBytes(StandardCharsets.UTF_8)))
                    : GoogleCredentials.getApplicationDefault();
            credentials = credentials.createScoped(List.of(CLOUD_PLATFORM_SCOPE));
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (Exception e) {
            throw new IllegalStateException("Vertex credentials unavailable: " + e.getMessage(), e);
        }
    }
}

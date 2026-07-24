/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.batch.openai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StringUtils;

import com.viglet.turing.genai.batch.TurBatchEmbeddingRequest;
import com.viglet.turing.genai.batch.TurBatchEmbeddingResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * F.7 / §X.8.c — pure (no-SDK, no-I/O) serialization for the OpenAI Batch API
 * {@code /v1/embeddings} JSONL contract.
 *
 * <p>Symmetric to {@link TurOpenAiBatchJsonl} but for embeddings: each input
 * line is {@code {custom_id, method:"POST", url:"/v1/embeddings", body:{model, input}}}
 * with a single string {@code input}; each output line carries
 * {@code response.body.data[0].embedding} (the float vector). Kept pure so the
 * wire format is unit-testable without a live key or the SDK.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurOpenAiEmbeddingBatchJsonl {

    public static final String EMBEDDINGS_URL = "/v1/embeddings";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private TurOpenAiEmbeddingBatchJsonl() {
    }

    public static String buildInputJsonl(List<TurBatchEmbeddingRequest> requests, String defaultModel) {
        StringBuilder sb = new StringBuilder();
        for (TurBatchEmbeddingRequest request : requests) {
            ObjectNode line = MAPPER.createObjectNode();
            line.put("custom_id", request.customId());
            line.put("method", "POST");
            line.put("url", EMBEDDINGS_URL);

            ObjectNode body = line.putObject("body");
            String model = StringUtils.hasText(request.model()) ? request.model() : defaultModel;
            body.put("model", model);
            body.put("input", request.text() == null ? "" : request.text());

            sb.append(MAPPER.writeValueAsString(line)).append('\n');
        }
        return sb.toString();
    }

    public static List<TurBatchEmbeddingResult> parseOutputJsonl(String outputJsonl) {
        List<TurBatchEmbeddingResult> results = new ArrayList<>();
        if (!StringUtils.hasText(outputJsonl)) {
            return results;
        }
        for (String rawLine : outputJsonl.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            results.add(parseLine(line));
        }
        return results;
    }

    private static TurBatchEmbeddingResult parseLine(String line) {
        JsonNode node = MAPPER.readTree(line);
        String customId = node.path("custom_id").asString(null);

        JsonNode error = node.get("error");
        if (error != null && !error.isNull()) {
            return TurBatchEmbeddingResult.failed(customId, errorMessage(error));
        }

        JsonNode response = node.path("response");
        int statusCode = response.path("status_code").asInt(0);
        JsonNode body = response.path("body");
        if (statusCode < 200 || statusCode >= 300) {
            String msg = body.path("error").path("message").asString("HTTP " + statusCode);
            return TurBatchEmbeddingResult.failed(customId, msg);
        }

        JsonNode embeddingNode = body.path("data").path(0).path("embedding");
        if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
            return TurBatchEmbeddingResult.failed(customId, "no embedding in response");
        }
        float[] embedding = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            embedding[i] = (float) embeddingNode.get(i).asDouble();
        }
        return TurBatchEmbeddingResult.ok(customId, embedding);
    }

    private static String errorMessage(JsonNode error) {
        if (error.isString()) {
            return error.asString();
        }
        String message = error.path("message").asString(null);
        return StringUtils.hasText(message) ? message : error.toString();
    }
}

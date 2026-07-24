/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.batch.gemini;

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
 * T496 / §X.19 — pure (no-SDK, no-I/O) serialization helpers for the Gemini Batch
 * API <em>embeddings</em> JSONL contract: the embeddings analog of
 * {@link TurGeminiBatchJsonl}, mirroring {@code TurOpenAiEmbeddingBatchJsonl} on
 * the Gemini side.
 *
 * <p>Like the chat leg, the Gemini embeddings batch takes a keyed JSONL file
 * (uploaded through the Files API and referenced as an
 * {@code EmbeddingsBatchJobSource}) where each line wraps an {@code embedContent}
 * request under a caller-chosen {@code key}, and returns a symmetric JSONL where
 * each line echoes that {@code key} alongside the {@code response} (or
 * {@code error}). The {@code key} is what round-trips Turing's {@code customId}
 * back to the right store chunk — exactly the role OpenAI's {@code custom_id}
 * plays.
 *
 * <p>The model is set at batch-creation time (on {@code batches.createEmbeddings},
 * not per line), so each input line is just
 * {@code {key, request:{content:{parts:[{text}]}, taskType}}}. Re-embedding is an
 * index-side operation, so {@code taskType} is fixed to {@code RETRIEVAL_DOCUMENT}
 * to match the synchronous index path of {@code TurGeminiEmbeddingModel}.
 *
 * <p><b>Wire format note:</b> field names follow the Gemini REST/mldev camelCase
 * contract ({@code content}, {@code taskType}). The output parser is deliberately
 * lenient — it accepts both the single-embedding shape
 * ({@code response.embedding.values}) and the list shape
 * ({@code response.embeddings[0].values}), and tolerates {@code error} present —
 * so a minor server-side shape change degrades to a failed result for that line
 * rather than throwing for the whole batch.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurGeminiEmbeddingBatchJsonl {

    /** Index-side task type, matching {@code TurGeminiEmbeddingModel}'s document path. */
    static final String TASK_TYPE_DOCUMENT = "RETRIEVAL_DOCUMENT";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private TurGeminiEmbeddingBatchJsonl() {
    }

    /** Serialize requests to the Gemini embeddings batch input JSONL (one keyed line per request). */
    public static String buildInputJsonl(List<TurBatchEmbeddingRequest> requests) {
        StringBuilder sb = new StringBuilder();
        for (TurBatchEmbeddingRequest request : requests) {
            ObjectNode line = MAPPER.createObjectNode();
            line.put("key", request.customId());

            ObjectNode body = line.putObject("request");
            ObjectNode content = body.putObject("content");
            content.putArray("parts").addObject()
                    .put("text", request.text() == null ? "" : request.text());
            body.put("taskType", TASK_TYPE_DOCUMENT);

            sb.append(MAPPER.writeValueAsString(line)).append('\n');
        }
        return sb.toString();
    }

    /** Parse the Gemini embeddings batch output JSONL into per-chunk vectors (keyed by {@code key}). */
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
        String key = node.path("key").asString(null);

        JsonNode error = node.get("error");
        if (error != null && !error.isNull()) {
            return TurBatchEmbeddingResult.failed(key, errorMessage(error));
        }

        JsonNode response = node.path("response");
        if (response.isMissingNode() || response.isNull()) {
            return TurBatchEmbeddingResult.failed(key, "No response in batch output line");
        }

        // Accept both the single shape (embedding.values) and the list shape (embeddings[0].values).
        JsonNode values = response.path("embedding").path("values");
        if (!values.isArray() || values.isEmpty()) {
            values = response.path("embeddings").path(0).path("values");
        }
        if (!values.isArray() || values.isEmpty()) {
            return TurBatchEmbeddingResult.failed(key, "no embedding in response");
        }
        float[] embedding = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            embedding[i] = (float) values.get(i).asDouble();
        }
        return TurBatchEmbeddingResult.ok(key, embedding);
    }

    private static String errorMessage(JsonNode error) {
        if (error.isString()) {
            return error.asString();
        }
        String message = error.path("message").asString(null);
        return StringUtils.hasText(message) ? message : error.toString();
    }
}

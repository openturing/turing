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

import com.viglet.turing.genai.batch.TurBatchChatRequest;
import com.viglet.turing.genai.batch.TurBatchChatResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * F.7 / §X.8.a — pure (no-SDK, no-I/O) serialization helpers for the OpenAI
 * Batch API JSONL contract.
 *
 * <p>The OpenAI Batch API does not take inline requests: the caller uploads a
 * JSONL file (one request object per line) through the Files API, then references
 * the resulting file id when creating the batch. The output is a symmetric JSONL
 * file (one response object per line). This class owns both directions of that
 * wire format and nothing else, so the request-building and response-parsing
 * logic — the part most likely to drift — is unit-testable without a live API
 * key or the SDK. {@link com.viglet.turing.genai.batch.openai.TurOpenAiBatchProvider}
 * wires it to the actual Files/Batch calls.
 *
 * <p>Each input line is {@code {custom_id, method:"POST", url, body}}; each
 * output line is {@code {custom_id, response:{status_code, body}, error}} where
 * a successful body carries {@code choices[0].message.content} and {@code usage}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurOpenAiBatchJsonl {

    /** The chat-completions endpoint every Turing batch request targets. */
    public static final String CHAT_COMPLETIONS_URL = "/v1/chat/completions";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private TurOpenAiBatchJsonl() {
    }

    /**
     * Serialize requests to the Batch input JSONL (one line per request,
     * newline-separated, no trailing newline required by the API but harmless).
     */
    public static String buildInputJsonl(List<TurBatchChatRequest> requests, String defaultModel) {
        StringBuilder sb = new StringBuilder();
        for (TurBatchChatRequest request : requests) {
            ObjectNode line = MAPPER.createObjectNode();
            line.put("custom_id", request.customId());
            line.put("method", "POST");
            line.put("url", CHAT_COMPLETIONS_URL);

            ObjectNode body = line.putObject("body");
            String model = StringUtils.hasText(request.model()) ? request.model() : defaultModel;
            body.put("model", model);

            ArrayNode messages = body.putArray("messages");
            if (StringUtils.hasText(request.systemPrompt())) {
                ObjectNode system = messages.addObject();
                system.put("role", "system");
                system.put("content", request.systemPrompt());
            }
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", request.userPrompt() == null ? "" : request.userPrompt());

            if (request.temperature() != null) {
                body.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                body.put("max_tokens", request.maxTokens());
            }
            sb.append(MAPPER.writeValueAsString(line)).append('\n');
        }
        return sb.toString();
    }

    /**
     * Parse the Batch output JSONL into per-request results. Lines that are
     * blank are skipped; a line whose {@code error} is present or whose status
     * code is not 2xx becomes a failed result.
     */
    public static List<TurBatchChatResult> parseOutputJsonl(String outputJsonl) {
        List<TurBatchChatResult> results = new ArrayList<>();
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

    private static TurBatchChatResult parseLine(String line) {
        JsonNode node = MAPPER.readTree(line);
        String customId = node.path("custom_id").asString(null);

        JsonNode error = node.get("error");
        if (error != null && !error.isNull()) {
            return TurBatchChatResult.failed(customId, errorMessage(error));
        }

        JsonNode response = node.path("response");
        int statusCode = response.path("status_code").asInt(0);
        JsonNode body = response.path("body");
        if (statusCode < 200 || statusCode >= 300) {
            String msg = body.path("error").path("message").asString("HTTP " + statusCode);
            return TurBatchChatResult.failed(customId, msg);
        }

        String content = body.path("choices").path(0).path("message").path("content").asString("");
        JsonNode usage = body.path("usage");
        Long inputTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asLong() : null;
        Long outputTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asLong() : null;
        return TurBatchChatResult.ok(customId, content, inputTokens, outputTokens);
    }

    private static String errorMessage(JsonNode error) {
        if (error.isString()) {
            return error.asString();
        }
        String message = error.path("message").asString(null);
        return StringUtils.hasText(message) ? message : error.toString();
    }
}

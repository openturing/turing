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

import com.viglet.turing.genai.batch.TurBatchChatRequest;
import com.viglet.turing.genai.batch.TurBatchChatResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T496 / §X.19 — pure (no-SDK, no-I/O) serialization helpers for the Gemini
 * Batch API JSONL contract (the Gemini analog of {@code TurOpenAiBatchJsonl}).
 *
 * <p>The Gemini Developer API batch mode takes a JSONL file (uploaded through
 * the Files API) where each line wraps a request under a caller-chosen
 * {@code key}, and returns a symmetric JSONL where each line echoes that
 * {@code key} alongside the {@code response} (or {@code error}). The {@code key}
 * is what lets results round-trip back to Turing's {@code customId} — exactly the
 * role OpenAI's {@code custom_id} plays — which is why the file (keyed) path is
 * used rather than inlined requests (which carry no key).
 *
 * <p>The model is set at batch-creation time (not per line), so each input line
 * is just {@code {key, request:{contents, systemInstruction, generationConfig}}}.
 *
 * <p><b>Wire format note:</b> the field names follow the Gemini REST/mldev
 * camelCase contract ({@code systemInstruction}, {@code generationConfig},
 * {@code maxOutputTokens}, {@code usageMetadata}). The output parser is
 * deliberately lenient (tolerates {@code response} present/absent, {@code error}
 * present) so a minor server-side shape change degrades to a failed result for
 * that line rather than throwing for the whole batch.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurGeminiBatchJsonl {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private TurGeminiBatchJsonl() {
    }

    /** Serialize requests to the Gemini batch input JSONL (one keyed line per request). */
    public static String buildInputJsonl(List<TurBatchChatRequest> requests) {
        StringBuilder sb = new StringBuilder();
        for (TurBatchChatRequest request : requests) {
            ObjectNode line = MAPPER.createObjectNode();
            line.put("key", request.customId());

            ObjectNode body = line.putObject("request");
            ArrayNode contents = body.putArray("contents");
            ObjectNode userTurn = contents.addObject();
            userTurn.put("role", "user");
            userTurn.putArray("parts").addObject()
                    .put("text", request.userPrompt() == null ? "" : request.userPrompt());

            if (StringUtils.hasText(request.systemPrompt())) {
                ObjectNode systemInstruction = body.putObject("systemInstruction");
                systemInstruction.putArray("parts").addObject().put("text", request.systemPrompt());
            }

            if (request.temperature() != null || request.maxTokens() != null) {
                ObjectNode generationConfig = body.putObject("generationConfig");
                if (request.temperature() != null) {
                    generationConfig.put("temperature", request.temperature());
                }
                if (request.maxTokens() != null) {
                    generationConfig.put("maxOutputTokens", request.maxTokens());
                }
            }
            sb.append(MAPPER.writeValueAsString(line)).append('\n');
        }
        return sb.toString();
    }

    /** Parse the Gemini batch output JSONL into per-request results (keyed by {@code key}). */
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
        String key = node.path("key").asString(null);

        JsonNode error = node.get("error");
        if (error != null && !error.isNull()) {
            return TurBatchChatResult.failed(key, errorMessage(error));
        }

        JsonNode response = node.path("response");
        if (response.isMissingNode() || response.isNull()) {
            return TurBatchChatResult.failed(key, "No response in batch output line");
        }
        // candidates[0].content.parts[*].text — concatenate text parts.
        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        StringBuilder text = new StringBuilder();
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                String partText = part.path("text").asString("");
                text.append(partText);
            }
        }
        JsonNode usage = response.path("usageMetadata");
        Long inputTokens = usage.has("promptTokenCount") ? usage.get("promptTokenCount").asLong() : null;
        Long outputTokens = usage.has("candidatesTokenCount")
                ? usage.get("candidatesTokenCount").asLong() : null;
        return TurBatchChatResult.ok(key, text.toString(), inputTokens, outputTokens);
    }

    private static String errorMessage(JsonNode error) {
        if (error.isString()) {
            return error.asString();
        }
        String message = error.path("message").asString(null);
        return StringUtils.hasText(message) ? message : error.toString();
    }
}

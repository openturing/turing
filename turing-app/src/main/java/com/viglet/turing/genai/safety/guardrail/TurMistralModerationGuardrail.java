/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.safety.guardrail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.properties.TurGuardrailProperty;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T516 / §XXVIII.12 — answer-guardrail adapter over Mistral's moderation API
 * ({@code mistral-moderation-latest}), the GDPR-native European screening option
 * (pairs with the T509 Mistral provider). Classifies the model <em>answer</em>
 * into Mistral's safety categories; like the OpenAI moderation adapter it does
 * not assess grounding ({@code grounded} stays {@code true}).
 *
 * <p>Inert when no API key is configured. Fail-open: any HTTP/parse error throws
 * and the {@link TurAnswerGuardrailService} facade degrades to "no verdict".
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurMistralModerationGuardrail implements TurAnswerGuardrailStrategy {

    private final TurGuardrailProperty guardrailProperty;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    public TurMistralModerationGuardrail(TurGuardrailProperty guardrailProperty) {
        this.guardrailProperty = guardrailProperty;
    }

    @Override
    public TurAnswerGuardrailType getType() {
        return TurAnswerGuardrailType.MISTRAL_MODERATION;
    }

    @Override
    public TurAnswerGuardrailVerdict evaluate(TurAnswerGuardrailRequest request) {
        TurGuardrailProperty.Mistral cfg = guardrailProperty.getMistral();
        if (cfg == null || !StringUtils.hasText(cfg.getApiKey())) {
            log.debug("[Guardrail] Mistral moderation has no API key — skipping (fail-open)");
            return TurAnswerGuardrailVerdict.pass(TurAnswerGuardrailType.MISTRAL_MODERATION);
        }
        List<String> flagged = callModeration(cfg, request.answer());
        if (flagged.isEmpty()) {
            return TurAnswerGuardrailVerdict.pass(TurAnswerGuardrailType.MISTRAL_MODERATION);
        }
        return new TurAnswerGuardrailVerdict(true, null, null,
                TurAnswerGuardrailAction.FLAG, flagged, null,
                TurAnswerGuardrailType.MISTRAL_MODERATION);
    }

    /**
     * POSTs {@code text} to the Mistral moderation endpoint and returns the names
     * of any flagged categories ({@code category == true}). Throws on transport
     * or non-2xx so the facade fails open.
     */
    private List<String> callModeration(TurGuardrailProperty.Mistral cfg, String text) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", cfg.getModel());
            ArrayNode input = body.putArray("input");
            input.add(text);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getEndpoint()))
                    .timeout(Duration.ofSeconds(Math.max(1, cfg.getTimeoutSeconds())))
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Mistral moderation HTTP " + response.statusCode());
            }
            return parseFlaggedCategories(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mistral moderation call interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Mistral moderation call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Reads {@code results[0].categories} and returns the keys whose value is
     * {@code true}. Tolerates a missing/empty {@code results} array (returns no
     * flags) so a benign-but-unexpected shape never blocks the answer.
     */
    private List<String> parseFlaggedCategories(String json) {
        List<String> flagged = new ArrayList<>();
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return flagged;
        }
        JsonNode categories = results.get(0).path("categories");
        if (categories.isObject()) {
            for (Map.Entry<String, JsonNode> entry : categories.properties()) {
                if (entry.getValue().asBoolean(false)) {
                    flagged.add(entry.getKey());
                }
            }
        }
        return flagged;
    }
}

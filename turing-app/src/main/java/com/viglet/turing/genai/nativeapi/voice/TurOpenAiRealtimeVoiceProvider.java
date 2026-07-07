/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.voice;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient.NativeCredentials;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * T147 / §X.6.a — OpenAI Realtime voice provider. Mints a short-lived ephemeral
 * session against the OpenAI Realtime {@code /realtime/client_secrets} endpoint
 * so the browser can open the WebSocket transport to {@code gpt-realtime}
 * directly, holding only the ephemeral {@code ek_…} token — the account API key
 * never leaves the server.
 *
 * <p>Credentials (decrypted API key + base URL) are sourced from the shared
 * {@link TurNativeProviderClient} seam, so there is no parallel secret path: the
 * same instance key that powers OpenAI chat/embedding powers voice.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurOpenAiRealtimeVoiceProvider implements TurRealtimeVoiceProvider {

    static final String PLUGIN_TYPE = "openai";
    static final String DEFAULT_MODEL = "gpt-realtime";
    static final String DEFAULT_VOICE = "marin";

    /** The GA realtime models this provider mints sessions for. */
    private static final Set<String> KNOWN_MODELS = Set.of(
            "gpt-realtime", "gpt-realtime-mini", "gpt-realtime-1.5");

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final TurNativeProviderClient nativeClient;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    public TurOpenAiRealtimeVoiceProvider(TurNativeProviderClient nativeClient) {
        this.nativeClient = nativeClient;
    }

    @Override
    public String getPluginType() {
        return PLUGIN_TYPE;
    }

    @Override
    public String defaultModel() {
        return DEFAULT_MODEL;
    }

    @Override
    public boolean supportsModel(String model) {
        if (!StringUtils.hasText(model)) {
            return false;
        }
        String normalized = model.trim().toLowerCase(Locale.ROOT);
        // Accept the known GA ids plus any forward-compatible realtime variant
        // (e.g. a future "gpt-realtime-2") so a new model id doesn't need a code
        // change; the legacy "gpt-4o[-mini]-realtime-*" names are accepted too.
        return KNOWN_MODELS.contains(normalized)
                || normalized.startsWith("gpt-realtime")
                || normalized.contains("realtime");
    }

    @Override
    public TurRealtimeVoiceSession createSession(TurLLMInstance instance,
            TurRealtimeVoiceSessionRequest request) {
        NativeCredentials creds = nativeClient.credentials(instance);
        if (creds == null || !StringUtils.hasText(creds.apiKey())) {
            throw new TurRealtimeVoiceException(
                    "OpenAI instance has no API key — cannot mint a realtime voice session");
        }
        String model = StringUtils.hasText(request.model()) ? request.model().trim() : DEFAULT_MODEL;
        if (!supportsModel(model)) {
            throw new TurRealtimeVoiceException("Unsupported realtime model: " + model);
        }
        String voice = StringUtils.hasText(request.voice()) ? request.voice().trim() : DEFAULT_VOICE;

        String endpoint = clientSecretsEndpoint(creds.baseUrl());
        String body = buildRequestBody(model, voice, request.instructions());
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + creds.apiKey().trim())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("[Voice] OpenAI client_secrets returned HTTP {} for instance '{}'",
                        response.statusCode(), instance.getId());
                throw new TurRealtimeVoiceException(
                        "OpenAI realtime session endpoint returned HTTP " + response.statusCode());
            }
            return parseSession(response.body(), model, voice, creds.baseUrl());
        } catch (java.io.IOException e) {
            throw new TurRealtimeVoiceException("realtime voice session request failed: "
                    + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TurRealtimeVoiceException("realtime voice session request interrupted", e);
        }
    }

    private String buildRequestBody(String model, String voice, String instructions) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode session = root.putObject("session");
        session.put("type", "realtime");
        session.put("model", model);
        if (StringUtils.hasText(instructions)) {
            session.put("instructions", instructions);
        }
        session.putObject("audio").putObject("output").put("voice", voice);
        return objectMapper.writeValueAsString(root);
    }

    /**
     * Parses the OpenAI response into a {@link TurRealtimeVoiceSession}. Tolerant
     * of both the GA top-level shape ({@code {value, expires_at, …}}) and the
     * older nested {@code {client_secret:{value, expires_at}}} envelope.
     */
    TurRealtimeVoiceSession parseSession(String responseBody, String model, String voice,
            String baseUrl) {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode secretNode = root.has("value") ? root : root.path("client_secret");
        JsonNode valueNode = secretNode.path("value");
        String clientSecret = valueNode.isString() ? valueNode.asString() : "";
        if (!StringUtils.hasText(clientSecret)) {
            throw new TurRealtimeVoiceException(
                    "OpenAI realtime response did not contain a client secret");
        }
        long expiresAt = secretNode.path("expires_at").asLong(0L);
        return new TurRealtimeVoiceSession(PLUGIN_TYPE, clientSecret, expiresAt, model, voice,
                webSocketUrl(baseUrl, model));
    }

    /** {@code https://api.openai.com/v1} → {@code https://api.openai.com/v1/realtime/client_secrets}. */
    static String clientSecretsEndpoint(String baseUrl) {
        return trimTrailingSlash(baseUrl) + "/realtime/client_secrets";
    }

    /**
     * Derives the realtime WebSocket URL from the REST base URL:
     * {@code https://api.openai.com/v1} → {@code wss://api.openai.com/v1/realtime?model=…}.
     */
    static String webSocketUrl(String baseUrl, String model) {
        String base = trimTrailingSlash(baseUrl);
        String ws;
        if (base.startsWith("https://")) {
            ws = "wss://" + base.substring("https://".length());
        } else if (base.startsWith("http://")) {
            ws = "ws://" + base.substring("http://".length());
        } else {
            ws = base;
        }
        return ws + "/realtime?model=" + model;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}

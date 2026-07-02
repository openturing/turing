/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.provider.llm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.viglet.turing.genai.nativeapi.gemini.TurGeminiEmbeddingModel;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * T510 / §XXVIII.6 — Google <b>Vertex AI</b> provider, the enterprise path for
 * Gemini on GCP.
 *
 * <p>The existing {@code gemini} provider hits the <i>public</i>
 * {@code generativelanguage.googleapis.com}, which cannot serve a GCP-regulated
 * tenant. Vertex AI adds IAM, VPC-SC, CMEK, regional endpoints and provisioned
 * throughput. It reuses the native Gemini stack — same {@code GoogleGenAiChatModel}
 * and {@link TurGeminiEmbeddingModel} (so the T489+ prompt-assembly and the T495
 * asymmetric-task-type embeddings come for free) — and <b>differs only in
 * transport + auth</b>: the {@link Client} is built in Vertex mode
 * ({@code vertexAI(true)}, project + location) rather than with an API key.
 *
 * <p>Auth mirrors the Bedrock IAM pattern: a service-account JSON in the
 * instance's encrypted key field (or a {@code credentialsJson} provider option)
 * yields explicit {@link GoogleCredentials}; otherwise it falls back to
 * Application Default Credentials (workload identity / gcloud / GCE metadata),
 * the path GCP-standardized enterprises use.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurVertexAiLlmProvider implements TurGenAiLlmProvider {

    private static final String DEFAULT_CHAT_MODEL = "gemini-2.0-flash";
    private static final String DEFAULT_LOCATION = "us-central1";
    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final TurProviderOptionsParser optionsParser;

    public TurVertexAiLlmProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @Override
    public String getPluginType() {
        return "vertex-ai";
    }

    @Override
    public ChatModel createChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        Client genAiClient = buildVertexClient(turLLMInstance, decryptedApiKey, options);

        GoogleGenAiChatOptions.Builder optionsBuilder = GoogleGenAiChatOptions.builder()
                .model(resolveChatModelName(firstNonBlank(
                        optionsParser.stringValue(options, "chatModel"),
                        optionsParser.stringValue(options, "model"),
                        turLLMInstance.getModelName())));

        Double temperature = firstNonNull(optionsParser.doubleValue(options, "temperature"),
                turLLMInstance.getTemperature());
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }
        Double topP = firstNonNull(optionsParser.doubleValue(options, "topP"), turLLMInstance.getTopP());
        if (topP != null) {
            optionsBuilder.topP(topP);
        }
        Integer topK = firstNonNull(optionsParser.intValue(options, "topK"), turLLMInstance.getTopK());
        if (topK != null) {
            optionsBuilder.topK(topK);
        }
        Integer maxTokens = optionsParser.intValue(options, "maxTokens");
        if (maxTokens != null) {
            optionsBuilder.maxOutputTokens(maxTokens);
        }

        return GoogleGenAiChatModel.builder()
                .genAiClient(genAiClient)
                .options(optionsBuilder.build())
                .build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        Client genAiClient = buildVertexClient(turLLMInstance, decryptedApiKey, options);

        String modelName = firstNonBlank(
                optionsParser.stringValue(options, "embeddingModel"),
                optionsParser.stringValue(options, "model"),
                turLLMInstance.getModelName());
        Integer outputDimensionality = optionsParser.intValue(options, "outputDimensionality");

        return new TurGeminiEmbeddingModel(genAiClient, modelName, outputDimensionality);
    }

    /** Builds a google-genai {@link Client} in Vertex mode (project + location + credentials). */
    private Client buildVertexClient(TurLLMInstance turLLMInstance, String decryptedApiKey,
            Map<String, Object> options) {
        String project = firstNonBlank(
                optionsParser.stringValue(options, "project"),
                optionsParser.stringValue(options, "projectId"));
        if (!StringUtils.hasText(project)) {
            throw new IllegalStateException(
                    "Missing GCP project for Vertex AI provider in LLM instance: " + turLLMInstance.getId()
                            + " (set a 'project' provider option)");
        }
        String location = firstNonBlank(
                optionsParser.stringValue(options, "location"),
                optionsParser.stringValue(options, "region"));
        if (!StringUtils.hasText(location)) {
            location = DEFAULT_LOCATION;
        }

        Client.Builder builder = Client.builder()
                .vertexAI(true)
                .project(project)
                .location(location)
                .credentials(resolveCredentials(turLLMInstance, decryptedApiKey, options));
        return builder.build();
    }

    /**
     * Explicit service-account JSON (from the {@code credentialsJson} option or
     * the encrypted key field) → {@link GoogleCredentials#fromStream}; otherwise
     * Application Default Credentials. Both are scoped to cloud-platform.
     */
    private GoogleCredentials resolveCredentials(TurLLMInstance turLLMInstance, String decryptedApiKey,
            Map<String, Object> options) {
        String credentialsJson = firstNonBlank(
                optionsParser.stringValue(options, "credentialsJson"),
                decryptedApiKey);
        try {
            GoogleCredentials credentials;
            if (StringUtils.hasText(credentialsJson) && credentialsJson.trim().startsWith("{")) {
                credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8)));
            } else {
                credentials = GoogleCredentials.getApplicationDefault();
            }
            if (credentials.createScopedRequired()) {
                credentials = credentials.createScoped(List.of(CLOUD_PLATFORM_SCOPE));
            }
            return credentials;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to resolve Google credentials for Vertex AI instance "
                            + turLLMInstance.getId() + ": " + e.getMessage(), e);
        }
    }

    private String resolveChatModelName(String configuredModel) {
        return StringUtils.hasText(configuredModel) ? configuredModel : DEFAULT_CHAT_MODEL;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}

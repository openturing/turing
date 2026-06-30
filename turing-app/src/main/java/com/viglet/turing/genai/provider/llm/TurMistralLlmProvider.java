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

import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * T509 / §XXVIII.5 — Mistral AI provider. The reason to prioritize it is
 * non-technical: a <b>European, GDPR-native</b> option matters to EU customers
 * and to Viglet's own positioning.
 *
 * <p>Mistral's API ({@code https://api.mistral.ai/v1}) is OpenAI-compatible for
 * both {@code /chat/completions} and {@code /embeddings}, so chat (Mistral
 * Large/Small, Codestral for code; default {@code mistral-large-latest}) and
 * embeddings (default {@code mistral-embed}) ride the same proven OpenAI seam as
 * {@code cohere} / {@code gemini-openai}.
 *
 * <p><b>Reranking:</b> Mistral exposes no managed rerank endpoint, so there is no
 * {@code MISTRAL} {@code TurRagReranker} strategy. Reranking with a Mistral model
 * is already available through the existing {@code LLM} rerank strategy by
 * pointing the default LLM at a Mistral instance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurMistralLlmProvider implements TurGenAiLlmProvider {

    private static final String BASE_URL = "baseUrl";
    private static final String MODEL = "model";

    private static final String DEFAULT_BASE_URL = "https://api.mistral.ai/v1";
    private static final String DEFAULT_CHAT_MODEL = "mistral-large-latest";
    private static final String DEFAULT_EMBEDDING_MODEL = "mistral-embed";

    private final TurProviderOptionsParser optionsParser;

    public TurMistralLlmProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @Override
    public String getPluginType() {
        return "mistral";
    }

    @Override
    public ChatModel createChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String baseUrl = resolveBaseUrl(options, turLLMInstance);
        String apiKey = requireApiKey(decryptedApiKey, turLLMInstance);

        OpenAIClient openAiClient = OpenAIOkHttpClient.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        OpenAIClientAsync openAiClientAsync = OpenAIOkHttpClientAsync.builder().baseUrl(baseUrl).apiKey(apiKey).build();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(resolveChatModelName(options, turLLMInstance));
        applyCommonOptions(optionsBuilder, turLLMInstance, options);

        return OpenAiChatModel.builder()
                .openAiClient(openAiClient)
                .openAiClientAsync(openAiClientAsync)
                .options(optionsBuilder.build())
                .build();
    }

    @Override
    public ChatModel createStreamingChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String baseUrl = resolveBaseUrl(options, turLLMInstance);
        String apiKey = requireApiKey(decryptedApiKey, turLLMInstance);
        String modelName = resolveChatModelName(options, turLLMInstance);

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model(modelName);
        applyCommonOptions(optionsBuilder, turLLMInstance, options);
        return new TurOpenAiStreamingChatModel(baseUrl, apiKey, modelName, optionsBuilder.build());
    }

    @Override
    public EmbeddingModel createEmbeddingModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        OpenAIClient openAiClient = OpenAIOkHttpClient.builder()
                .baseUrl(resolveBaseUrl(options, turLLMInstance))
                .apiKey(requireApiKey(decryptedApiKey, turLLMInstance))
                .build();

        OpenAiEmbeddingOptions embeddingOptions = OpenAiEmbeddingOptions.builder()
                .model(resolveEmbeddingModelName(options, turLLMInstance))
                .build();

        return OpenAiEmbeddingModel.builder()
                .openAiClient(openAiClient)
                .metadataMode(MetadataMode.NONE)
                .options(embeddingOptions)
                .build();
    }

    private void applyCommonOptions(OpenAiChatOptions.Builder optionsBuilder, TurLLMInstance turLLMInstance,
            Map<String, Object> options) {
        Double temperature = firstNonNull(optionsParser.doubleValue(options, "temperature"),
                turLLMInstance.getTemperature());
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }
        Double topP = firstNonNull(optionsParser.doubleValue(options, "topP"), turLLMInstance.getTopP());
        if (topP != null) {
            optionsBuilder.topP(topP);
        }
        Integer maxTokens = optionsParser.intValue(options, "maxTokens");
        if (maxTokens != null) {
            optionsBuilder.maxTokens(maxTokens);
        }
    }

    private String requireApiKey(String decryptedApiKey, TurLLMInstance turLLMInstance) {
        if (!StringUtils.hasText(decryptedApiKey)) {
            throw new IllegalStateException(
                    "Missing API key for Mistral provider in LLM instance: " + turLLMInstance.getId());
        }
        return decryptedApiKey;
    }

    private String resolveBaseUrl(Map<String, Object> options, TurLLMInstance turLLMInstance) {
        String configured = firstNonBlank(
                optionsParser.stringValue(options, BASE_URL),
                turLLMInstance.getUrl());
        return StringUtils.hasText(configured) ? configured : DEFAULT_BASE_URL;
    }

    private String resolveChatModelName(Map<String, Object> options, TurLLMInstance turLLMInstance) {
        String configured = firstNonBlank(
                optionsParser.stringValue(options, "chatModel"),
                optionsParser.stringValue(options, MODEL),
                turLLMInstance.getModelName());
        return StringUtils.hasText(configured) ? configured : DEFAULT_CHAT_MODEL;
    }

    private String resolveEmbeddingModelName(Map<String, Object> options, TurLLMInstance turLLMInstance) {
        String configured = firstNonBlank(
                optionsParser.stringValue(options, "embeddingModel"),
                optionsParser.stringValue(options, MODEL),
                turLLMInstance.getModelName());
        return StringUtils.hasText(configured) ? configured : DEFAULT_EMBEDDING_MODEL;
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

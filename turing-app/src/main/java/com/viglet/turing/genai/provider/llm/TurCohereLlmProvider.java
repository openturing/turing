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
 * T508 / §XXVIII.4 — full Cohere provider, promoting Cohere beyond the existing
 * {@code COHERE} reranker strategy (T339) to a first-class chat + embeddings
 * provider.
 *
 * <p>Cohere exposes an <b>OpenAI-compatible</b> endpoint
 * ({@code https://api.cohere.ai/compatibility/v1}), so chat ({@code Command}
 * family) and embeddings ({@code Embed v4} — multilingual + multimodal +
 * Matryoshka) ride the same proven OpenAI seam used by {@code gemini-openai} and
 * {@code openai-compatible}. The default chat model is {@code command-r-plus} and
 * the default embedding model is {@code embed-v4.0}; both are overridable per
 * instance.
 *
 * <p>Cohere's <i>native</i> built-in citation mode is a later cross-vendor bridge
 * (§XXVIII.15 / T519); this task delivers the provider seam (parity chat +
 * embeddings).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurCohereLlmProvider implements TurGenAiLlmProvider {

    private static final String BASE_URL = "baseUrl";
    private static final String MODEL = "model";

    private static final String DEFAULT_BASE_URL = "https://api.cohere.ai/compatibility/v1";
    private static final String DEFAULT_CHAT_MODEL = "command-r-plus";
    private static final String DEFAULT_EMBEDDING_MODEL = "embed-v4.0";

    private final TurProviderOptionsParser optionsParser;

    public TurCohereLlmProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @Override
    public String getPluginType() {
        return "cohere";
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
                    "Missing API key for Cohere provider in LLM instance: " + turLLMInstance.getId());
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

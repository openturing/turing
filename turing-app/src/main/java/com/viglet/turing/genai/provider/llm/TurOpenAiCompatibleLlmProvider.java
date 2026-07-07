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
 * T505 / §XXVIII.1 — generic <b>OpenAI-compatible</b> provider.
 *
 * <p>Generalizes the {@code gemini-openai} seam (OpenAI wire format at a custom
 * {@code baseUrl}) into a first-class, vendor-agnostic provider. One seam then
 * reaches every endpoint that speaks the OpenAI Chat Completions / Embeddings
 * protocol — <b>DeepSeek, xAI Grok, Groq, Cerebras, OpenRouter, Together,
 * Fireworks, and a local vLLM / LM-Studio</b> — chat plus embeddings wherever
 * the endpoint exposes {@code /embeddings}.
 *
 * <p>Unlike the fixed-endpoint providers, the {@code baseUrl} is <b>required</b>
 * per-instance config (there is no sensible default) and is read from either the
 * {@code baseUrl} provider option or the instance {@code url}. The API key is
 * sent as a Bearer token (the auth shape all eight named vendors use); it is
 * <b>optional</b> so a local, unauthenticated vLLM endpoint works with a blank
 * key.
 *
 * <p>The capability matrix stays empty/conservative for this type — these
 * endpoints vary, so no provider-native server tools are assumed; the union tool
 * loop (Block F.15) treats it like {@code ANY} and only runs Turing/MCP/custom
 * tools.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurOpenAiCompatibleLlmProvider implements TurGenAiLlmProvider {

    private static final String BASE_URL = "baseUrl";
    private static final String MODEL = "model";

    /**
     * Placeholder used when no API key is configured — required because the
     * OpenAI SDK builder rejects a null key, but a local vLLM/LM-Studio endpoint
     * typically ignores the value entirely.
     */
    private static final String NO_AUTH_PLACEHOLDER = "not-needed";

    private final TurProviderOptionsParser optionsParser;

    public TurOpenAiCompatibleLlmProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @Override
    public String getPluginType() {
        return "openai-compatible";
    }

    @Override
    public ChatModel createChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String baseUrl = requireBaseUrl(turLLMInstance, options);
        String apiKey = resolveApiKey(decryptedApiKey);

        OpenAIClient openAiClient = OpenAIOkHttpClient.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        OpenAIClientAsync openAiClientAsync = OpenAIOkHttpClientAsync.builder().baseUrl(baseUrl).apiKey(apiKey).build();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(resolveChatModelName(turLLMInstance, options));
        applyCommonOptions(optionsBuilder, turLLMInstance, options);

        return OpenAiChatModel.builder()
                .openAiClient(openAiClient)
                .openAiClientAsync(openAiClientAsync)
                .options(optionsBuilder.build())
                .build();
    }

    @Override
    public ChatModel createStreamingChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        // Same rationale as TurOpenAiLlmProvider: Spring AI's SDK-based
        // OpenAiChatModel buffers the streaming response, so a hand-rolled
        // SSE-parsing model is used for token-by-token delivery. Because these
        // endpoints speak the OpenAI wire format, the same model works.
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String baseUrl = requireBaseUrl(turLLMInstance, options);
        String apiKey = resolveApiKey(decryptedApiKey);
        String modelName = resolveChatModelName(turLLMInstance, options);

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model(modelName);
        applyCommonOptions(optionsBuilder, turLLMInstance, options);
        return new TurOpenAiStreamingChatModel(baseUrl, apiKey, modelName, optionsBuilder.build());
    }

    @Override
    public EmbeddingModel createEmbeddingModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String baseUrl = requireBaseUrl(turLLMInstance, options);
        OpenAIClient openAiClient = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(resolveApiKey(decryptedApiKey))
                .build();

        OpenAiEmbeddingOptions embeddingOptions = OpenAiEmbeddingOptions.builder()
                .model(resolveEmbeddingModelName(turLLMInstance, options))
                .build();

        return OpenAiEmbeddingModel.builder()
                .openAiClient(openAiClient)
                .metadataMode(MetadataMode.NONE)
                .options(embeddingOptions)
                .build();
    }

    @Override
    public java.util.List<TurLlmModelOption> listModels(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String baseUrl = firstNonBlank(
                optionsParser.stringValue(options, BASE_URL),
                turLLMInstance.getUrl());
        return TurLlmModelListingSupport.openAiStyle(baseUrl, decryptedApiKey);
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

    private String resolveApiKey(String decryptedApiKey) {
        return StringUtils.hasText(decryptedApiKey) ? decryptedApiKey : NO_AUTH_PLACEHOLDER;
    }

    private String requireBaseUrl(TurLLMInstance turLLMInstance, Map<String, Object> options) {
        String baseUrl = firstNonBlank(
                optionsParser.stringValue(options, BASE_URL),
                turLLMInstance.getUrl());
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException(
                    "Missing baseUrl for OpenAI-compatible provider in LLM instance: " + turLLMInstance.getId()
                            + " (set the instance URL or a 'baseUrl' provider option, e.g. "
                            + "https://api.deepseek.com/v1)");
        }
        return baseUrl;
    }

    private String resolveChatModelName(TurLLMInstance turLLMInstance, Map<String, Object> options) {
        return firstNonBlank(
                optionsParser.stringValue(options, "chatModel"),
                optionsParser.stringValue(options, MODEL),
                turLLMInstance.getModelName());
    }

    private String resolveEmbeddingModelName(TurLLMInstance turLLMInstance, Map<String, Object> options) {
        return firstNonBlank(
                optionsParser.stringValue(options, "embeddingModel"),
                optionsParser.stringValue(options, MODEL),
                turLLMInstance.getModelName());
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

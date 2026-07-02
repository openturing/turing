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

import org.springframework.ai.bedrock.converse.BedrockChatOptions;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * T506 / §XXVIII.2 — AWS Bedrock provider over Spring AI's Bedrock Converse API.
 *
 * <p>Distinct from every other provider in that it is a <b>gateway</b> — Claude,
 * Llama, Mistral, Nova and Titan all reachable on one endpoint — authenticated by
 * <b>IAM</b> (region + credentials chain) rather than an API key. The model id is
 * the Bedrock id or inference-profile ARN stored on the instance (e.g.
 * {@code anthropic.claude-3-5-sonnet-20241022-v2:0} or
 * {@code us.anthropic.claude-3-5-sonnet-20241022-v2:0}). This is the path
 * AWS-standardized enterprises require for data residency and procurement.
 *
 * <p>Chat goes through {@link BedrockProxyChatModel} (which supports native
 * streaming via the Converse stream API, so the default
 * {@code createStreamingChatModel} is inherited). Embeddings go through
 * {@link TurBedrockEmbeddingModel} (Titan / Cohere on Bedrock via
 * {@code InvokeModel}), since the Converse module is chat-only.
 *
 * <p>Credentials and region are resolved by {@link TurBedrockCredentials}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurBedrockLlmProvider implements TurGenAiLlmProvider {

    private final TurProviderOptionsParser optionsParser;

    public TurBedrockLlmProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @Override
    public String getPluginType() {
        return "bedrock";
    }

    @Override
    public ChatModel createChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String modelId = requireModelId(turLLMInstance, options);
        TurBedrockCredentials credentials =
                TurBedrockCredentials.from(turLLMInstance, decryptedApiKey, optionsParser);

        BedrockChatOptions.Builder optionsBuilder = BedrockChatOptions.builder().model(modelId);
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

        return BedrockProxyChatModel.builder()
                .credentialsProvider(credentials.credentialsProvider())
                .region(credentials.region())
                .options(optionsBuilder.build())
                .build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String modelId = requireModelId(turLLMInstance, options);
        TurBedrockCredentials credentials =
                TurBedrockCredentials.from(turLLMInstance, decryptedApiKey, optionsParser);

        BedrockRuntimeClient client = BedrockRuntimeClient.builder()
                .region(credentials.region())
                .credentialsProvider(credentials.credentialsProvider())
                .build();
        return new TurBedrockEmbeddingModel(client, modelId);
    }

    private String requireModelId(TurLLMInstance turLLMInstance, Map<String, Object> options) {
        String modelId = firstNonBlank(
                optionsParser.stringValue(options, "modelId"),
                optionsParser.stringValue(options, "chatModel"),
                optionsParser.stringValue(options, "model"),
                turLLMInstance.getModelName());
        if (!StringUtils.hasText(modelId)) {
            throw new IllegalStateException(
                    "Missing model id for Bedrock provider in LLM instance: " + turLLMInstance.getId()
                            + " (set the model name, e.g. anthropic.claude-3-5-sonnet-20241022-v2:0)");
        }
        return modelId;
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

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
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * T507 / §XXVIII.3 — Voyage AI provider. Voyage is an <b>embeddings + rerank</b>
 * specialist with no chat API, so this provider supplies embeddings only
 * ({@link #createChatModel} throws, mirroring the inverse Anthropic case where
 * embeddings throw). The companion {@code VOYAGE} reranker strategy lives in the
 * Block N {@code TurRagReranker} stack ({@code TurVoyageRerankStrategy}).
 *
 * <p>The API key is the instance's encrypted key; the model defaults to
 * {@code voyage-3} and an optional {@code outputDimensionality} provider option
 * applies Matryoshka truncation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurVoyageLlmProvider implements TurGenAiLlmProvider {

    private final TurProviderOptionsParser optionsParser;

    public TurVoyageLlmProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @Override
    public String getPluginType() {
        return "voyage";
    }

    @Override
    public ChatModel createChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        throw new UnsupportedOperationException(
                "Voyage AI is an embeddings/rerank specialist and has no chat API. "
                        + "Use it as an embedding LLM (and as the VOYAGE reranker strategy).");
    }

    @Override
    public EmbeddingModel createEmbeddingModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        if (!StringUtils.hasText(decryptedApiKey)) {
            throw new IllegalStateException(
                    "Missing API key for Voyage AI provider in LLM instance: " + turLLMInstance.getId());
        }
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String modelName = firstNonBlank(
                optionsParser.stringValue(options, "embeddingModel"),
                optionsParser.stringValue(options, "model"),
                turLLMInstance.getModelName());
        Integer outputDimensionality = optionsParser.intValue(options, "outputDimensionality");
        // T511 / §XXVIII.7 — opt into the multimodal model (text + image in one
        // vector space) when the model name is a voyage-multimodal-* variant or
        // the explicit {@code multimodal} option is set. Default stays the
        // text-only path so legacy embedding configs are byte-for-byte unchanged.
        if (isMultimodal(options, modelName)) {
            return new TurVoyageMultimodalEmbeddingModel(decryptedApiKey, modelName, outputDimensionality);
        }
        // T512 / §XXVIII.8 — opt into the contextualized model (each chunk embedded
        // with its sibling chunks) when the model is a voyage-context-* variant or
        // the explicit {@code contextual} option is set. Query time is unchanged.
        if (isContextual(options, modelName)) {
            return new TurVoyageContextualEmbeddingModel(decryptedApiKey, modelName, outputDimensionality);
        }
        return new TurVoyageEmbeddingModel(decryptedApiKey, modelName, outputDimensionality);
    }

    /**
     * T511 — true when the configured embedding model is a Voyage multimodal
     * variant (model name contains {@code multimodal}) or the explicit
     * {@code multimodal} provider option is enabled.
     */
    private boolean isMultimodal(Map<String, Object> options, String modelName) {
        Boolean flag = optionsParser.booleanValue(options, "multimodal");
        if (Boolean.TRUE.equals(flag)) {
            return true;
        }
        return modelName != null && modelName.toLowerCase(java.util.Locale.ROOT).contains("multimodal");
    }

    /**
     * T512 — true when the configured embedding model is a Voyage contextual
     * variant (model name contains {@code context}) or the explicit
     * {@code contextual} provider option is enabled.
     */
    private boolean isContextual(Map<String, Object> options, String modelName) {
        Boolean flag = optionsParser.booleanValue(options, "contextual");
        if (Boolean.TRUE.equals(flag)) {
            return true;
        }
        return modelName != null && modelName.toLowerCase(java.util.Locale.ROOT).contains("context");
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

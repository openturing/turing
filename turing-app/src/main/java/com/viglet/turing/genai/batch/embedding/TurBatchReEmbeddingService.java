/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.batch.embedding;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.batch.TurBatchEmbeddingRequest;
import com.viglet.turing.genai.batch.TurBatchInferenceService;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProviderFactory;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * F.7 / §X.8.c — schedules in-place re-embedding of a store collection as an
 * embeddings Batch job (50% off, 24h SLA) instead of a synchronous re-embed,
 * on any vendor with an embeddings Batch API (OpenAI or, since T496, Gemini).
 *
 * <p>When an admin switches the embedding model on a collection, every existing
 * chunk's <em>text and metadata stay the same</em> — only its vector must be
 * recomputed. So this reads the collection's chunks ({@code streamExport}),
 * submits their texts as an embeddings batch keyed by chunk id, and lets
 * {@link TurReEmbeddingBatchHandler} write the precomputed vectors straight back
 * (via the store's {@code importChunks}) when the batch ends. No source re-fetch
 * and no re-chunking — cheaper and more faithful than a full reindex for a pure
 * model switch.
 *
 * <p>Opt-in + fail-safe: requires {@code turing.batch.enabled} +
 * {@code turing.batch.embedding.enabled} and an embedding model whose linked LLM
 * instance is on a vendor with an embeddings Batch API (OpenAI or Gemini).
 * Otherwise it submits nothing and the caller falls back to the existing
 * synchronous reindex.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurBatchReEmbeddingService {

    /** Shared purpose tag — returned by {@link TurReEmbeddingBatchHandler#purpose()}. */
    public static final String PURPOSE = "re-embedding";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final TurBatchInferenceService batchInferenceService;
    private final TurGenAiStoreProviderFactory storeProviderFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurConfigProperties configProperties;

    public TurBatchReEmbeddingService(TurBatchInferenceService batchInferenceService,
            TurGenAiStoreProviderFactory storeProviderFactory,
            TurSecretCryptoService secretCryptoService,
            TurConfigProperties configProperties) {
        this.batchInferenceService = batchInferenceService;
        this.storeProviderFactory = storeProviderFactory;
        this.secretCryptoService = secretCryptoService;
        this.configProperties = configProperties;
    }

    /** True when the Batch embedding tier is on and the model can use it. */
    public boolean isAvailable(TurEmbeddingModel embeddingModel) {
        return configProperties.getBatch().isEnabled()
                && configProperties.getBatch().getEmbedding().isEnabled()
                && embeddingModel != null
                && embeddingModel.getTurLLMInstance() != null
                && batchInferenceService.isEmbeddingSupported(embeddingModel.getTurLLMInstance());
    }

    /**
     * Submit a batch re-embedding of {@code collectionName} on {@code storeInstance}
     * using {@code embeddingModel}. Returns the number of chunks scheduled (0 when
     * the tier is unavailable or the collection is empty).
     */
    public int submitForCollection(TurStoreInstance storeInstance, String collectionName,
            TurEmbeddingModel embeddingModel) {
        if (!isAvailable(embeddingModel) || storeInstance == null
                || !StringUtils.hasText(collectionName)) {
            return 0;
        }
        TurLLMInstance llmInstance = embeddingModel.getTurLLMInstance();
        String modelId = StringUtils.hasText(embeddingModel.getModelReference())
                ? embeddingModel.getModelReference()
                : embeddingModel.getModelName();

        String credential = secretCryptoService.decrypt(storeInstance.getCredentialEncrypted());
        var provider = storeProviderFactory.getProvider(storeInstance);

        List<TurBatchEmbeddingRequest> requests = new ArrayList<>();
        provider.streamExport(storeInstance, credential, collectionName, record -> {
            if (record != null && StringUtils.hasText(record.id())) {
                requests.add(new TurBatchEmbeddingRequest(record.id(),
                        record.content() == null ? "" : record.content(), modelId));
            }
        });
        if (requests.isEmpty()) {
            return 0;
        }

        String contextJson = contextJson(storeInstance.getId(), collectionName, embeddingModel.getId());
        int max = Math.max(1, configProperties.getBatch().getMaxRequestsPerBatch());
        int scheduled = 0;
        for (int from = 0; from < requests.size(); from += max) {
            List<TurBatchEmbeddingRequest> chunk =
                    new ArrayList<>(requests.subList(from, Math.min(from + max, requests.size())));
            if (batchInferenceService.submitEmbeddings(llmInstance, PURPOSE, chunk, contextJson)
                    .isPresent()) {
                scheduled += chunk.size();
            }
        }
        log.info("[Batch][ReEmbed] scheduled {} chunk(s) of collection '{}' for re-embedding",
                scheduled, collectionName);
        return scheduled;
    }

    private String contextJson(String storeInstanceId, String collection, String embeddingModelId) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("storeInstanceId", storeInstanceId);
        node.put("collection", collection);
        node.put("embeddingModelId", embeddingModelId);
        return MAPPER.writeValueAsString(node);
    }

    /** Parses the context written by {@link #contextJson} back into its fields. */
    public static ReEmbedContext parseContext(String contextJson) {
        if (!StringUtils.hasText(contextJson)) {
            return null;
        }
        var node = MAPPER.readTree(contextJson);
        return new ReEmbedContext(
                node.path("storeInstanceId").asString(null),
                node.path("collection").asString(null),
                node.path("embeddingModelId").asString(null));
    }

    public record ReEmbedContext(String storeInstanceId, String collection, String embeddingModelId) {
        public boolean isComplete() {
            return StringUtils.hasText(storeInstanceId) && StringUtils.hasText(collection);
        }
    }
}

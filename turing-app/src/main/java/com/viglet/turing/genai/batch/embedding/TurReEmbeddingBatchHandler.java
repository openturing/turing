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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.genai.batch.TurBatchEmbeddingCompletionHandler;
import com.viglet.turing.genai.batch.TurBatchEmbeddingResult;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProvider;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProviderFactory;
import com.viglet.turing.genai.provider.store.TurStoreExportRecord;
import com.viglet.turing.persistence.model.batch.TurBatchJob;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * F.7 / §X.8.c — writes a completed re-embedding batch's vectors back into the
 * store.
 *
 * <p>The batch only recomputed vectors; the chunk text and metadata are
 * unchanged, so this re-reads the collection's chunks (to recover content +
 * metadata for each id), matches each by id to its precomputed vector, and
 * upserts them through the store's {@code importChunks} — which persists records
 * carrying a vector verbatim (no re-embedding). Chunks whose embedding failed in
 * the batch are left as they were (their old vector survives).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurReEmbeddingBatchHandler implements TurBatchEmbeddingCompletionHandler {

    private final TurStoreInstanceRepository storeInstanceRepository;
    private final TurGenAiStoreProviderFactory storeProviderFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurRagContextBuilder ragContextBuilder;

    public TurReEmbeddingBatchHandler(TurStoreInstanceRepository storeInstanceRepository,
            TurGenAiStoreProviderFactory storeProviderFactory,
            TurSecretCryptoService secretCryptoService,
            TurRagContextBuilder ragContextBuilder) {
        this.storeInstanceRepository = storeInstanceRepository;
        this.storeProviderFactory = storeProviderFactory;
        this.secretCryptoService = secretCryptoService;
        this.ragContextBuilder = ragContextBuilder;
    }

    @Override
    public String purpose() {
        return TurBatchReEmbeddingService.PURPOSE;
    }

    @Override
    public void onBatchComplete(TurBatchJob job, List<TurBatchEmbeddingResult> results) {
        TurBatchReEmbeddingService.ReEmbedContext context =
                TurBatchReEmbeddingService.parseContext(job.getContextJson());
        if (context == null || !context.isComplete()) {
            log.warn("[Batch][ReEmbed] job {} has incomplete context — cannot write back", job.getId());
            return;
        }
        TurStoreInstance store = storeInstanceRepository.findById(context.storeInstanceId()).orElse(null);
        if (store == null) {
            log.warn("[Batch][ReEmbed] store instance {} gone — dropping {} vectors",
                    context.storeInstanceId(), results.size());
            return;
        }

        // Index the precomputed vectors by chunk id.
        Map<String, float[]> vectorById = new LinkedHashMap<>();
        for (TurBatchEmbeddingResult result : results) {
            if (result.success() && result.embedding() != null && StringUtils.hasText(result.customId())) {
                vectorById.put(result.customId(), result.embedding());
            }
        }
        if (vectorById.isEmpty()) {
            log.warn("[Batch][ReEmbed] job {} produced no usable vectors", job.getId());
            return;
        }

        String credential = secretCryptoService.decrypt(store.getCredentialEncrypted());
        TurGenAiStoreProvider provider = storeProviderFactory.getProvider(store);

        // Re-read the chunks to recover content + metadata, pairing each with its new vector.
        List<TurStoreExportRecord> records = new ArrayList<>(vectorById.size());
        provider.streamExport(store, credential, context.collection(), chunk -> {
            float[] vector = vectorById.get(chunk.id());
            if (vector != null) {
                records.add(new TurStoreExportRecord(chunk.id(), chunk.content(), vector, chunk.metadata()));
            }
        });
        if (records.isEmpty()) {
            log.warn("[Batch][ReEmbed] job {} — none of the {} vectors matched a live chunk",
                    job.getId(), vectorById.size());
            return;
        }

        EmbeddingModel embeddingModel = resolveEmbeddingModel(context.embeddingModelId());
        var result = provider.importChunks(store, credential, context.collection(), records, embeddingModel);
        log.info("[Batch][ReEmbed] job {} rewrote {} chunk vector(s) into collection '{}'",
                job.getId(), result.imported(), context.collection());
    }

    private EmbeddingModel resolveEmbeddingModel(String embeddingModelId) {
        if (!StringUtils.hasText(embeddingModelId)) {
            return null;
        }
        // T756 — unified entity first (falls back to the legacy embedding_model row).
        TurEmbeddingModel embModel = ragContextBuilder.resolveEmbeddingEntity(embeddingModelId);
        if (embModel == null) {
            return null;
        }
        try {
            return ragContextBuilder.resolveEmbeddingModel(embModel);
        } catch (Exception e) {
            log.warn("[Batch][ReEmbed] could not resolve embedding model '{}': {}",
                    embeddingModelId, e.getMessage());
            return null;
        }
    }
}

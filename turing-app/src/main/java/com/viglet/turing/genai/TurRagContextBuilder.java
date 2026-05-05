/*
 * Copyright (C) 2016-2025 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.genai;

import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProvider;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProviderFactory;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Shared builder for RAG infrastructure (EmbeddingModel + VectorStore).
 * Eliminates duplication between SN GenAI, Asset Training, and RAG Search Tool.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Slf4j
@Component
public class TurRagContextBuilder {

    private final TurLlmModelFactory llmModelFactory;
    private final TurGenAiStoreProviderFactory storeProviderFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurGlobalSettingsService globalSettingsService;
    private final TurEmbeddingModelRepository embeddingModelRepository;
    private final TurStoreInstanceRepository storeInstanceRepository;

    public TurRagContextBuilder(TurLlmModelFactory llmModelFactory,
            TurGenAiStoreProviderFactory storeProviderFactory,
            TurSecretCryptoService secretCryptoService,
            TurGlobalSettingsService globalSettingsService,
            TurEmbeddingModelRepository embeddingModelRepository,
            TurStoreInstanceRepository storeInstanceRepository) {
        this.llmModelFactory = llmModelFactory;
        this.storeProviderFactory = storeProviderFactory;
        this.secretCryptoService = secretCryptoService;
        this.globalSettingsService = globalSettingsService;
        this.embeddingModelRepository = embeddingModelRepository;
        this.storeInstanceRepository = storeInstanceRepository;
    }

    /**
     * Result of building RAG infrastructure. Contains the VectorStore for indexing/querying,
     * plus the store provider and credentials needed for metadata operations (deindex, clear, etc.).
     */
    public record RagInfrastructure(
            VectorStore vectorStore,
            EmbeddingModel embeddingModel,
            TurGenAiStoreProvider storeProvider,
            TurStoreInstance storeInstance,
            String storeCredential,
            String collectionName
    ) {}

    /**
     * Resolves a TurEmbeddingModel into a Spring AI EmbeddingModel.
     * Creates a detached LLM instance with the embedding model's reference overriding the modelName.
     */
    public EmbeddingModel resolveEmbeddingModel(TurEmbeddingModel embModel) {
        TurLLMInstance llmInstance = embModel.getTurLLMInstance();
        if (llmInstance == null) {
            throw new IllegalStateException(
                    "Embedding Model '%s' has no LLM instance.".formatted(embModel.getModelName()));
        }

        TurLLMInstance embLlmInstance = new TurLLMInstance();
        embLlmInstance.setId(llmInstance.getId());
        embLlmInstance.setUrl(llmInstance.getUrl());
        embLlmInstance.setApiKeyEncrypted(llmInstance.getApiKeyEncrypted());
        embLlmInstance.setProviderOptionsJson(null);
        embLlmInstance.setTurLLMVendor(llmInstance.getTurLLMVendor());
        String embModelRef = embModel.getModelReference();
        embLlmInstance.setModelName(
                StringUtils.hasText(embModelRef) ? embModelRef : llmInstance.getModelName());

        String apiKey = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
        return llmModelFactory.createEmbeddingModel(embLlmInstance, apiKey);
    }

    /**
     * Builds the full RAG infrastructure (EmbeddingModel + VectorStore) from explicit IDs.
     */
    public Optional<RagInfrastructure> build(String embeddingModelId, String storeInstanceId) {
        return build(embeddingModelId, storeInstanceId, null);
    }

    /**
     * Builds the full RAG infrastructure with an optional collection name override.
     */
    public Optional<RagInfrastructure> build(String embeddingModelId, String storeInstanceId,
            String collectionNameOverride) {
        if (!StringUtils.hasText(embeddingModelId) || !StringUtils.hasText(storeInstanceId)) {
            return Optional.empty();
        }

        TurEmbeddingModel embModel = embeddingModelRepository.findById(embeddingModelId).orElse(null);
        if (embModel == null) {
            log.warn("Embedding Model not found: {}", embeddingModelId);
            return Optional.empty();
        }

        TurStoreInstance storeInstance = storeInstanceRepository.findById(storeInstanceId).orElse(null);
        if (storeInstance == null) {
            log.warn("Embedding Store not found: {}", storeInstanceId);
            return Optional.empty();
        }

        return build(embModel, storeInstance, collectionNameOverride);
    }

    /**
     * Builds the full RAG infrastructure from resolved entities with an optional collection name override.
     */
    public Optional<RagInfrastructure> build(TurEmbeddingModel embModel, TurStoreInstance storeInstance,
            String collectionNameOverride) {
        try {
            EmbeddingModel embeddingModel = resolveEmbeddingModel(embModel);

            TurGenAiStoreProvider storeProvider = storeProviderFactory.getProvider(storeInstance);
            String storeCredential = secretCryptoService.decrypt(storeInstance.getCredentialEncrypted());

            String defaultCollection = storeInstance.getCollectionName() != null
                    ? storeInstance.getCollectionName() : "turing";
            String collectionName = StringUtils.hasText(collectionNameOverride)
                    ? collectionNameOverride
                    : defaultCollection;

            log.debug("RAG building vector store: provider='{}', storeInstanceId='{}', embeddingModelId='{}', defaultCollection='{}', collectionOverride='{}', effectiveCollection='{}'",
                    storeProvider.getClass().getSimpleName(), storeInstance.getId(),
                    embModel == null ? null : embModel.getId(),
                    defaultCollection, collectionNameOverride, collectionName);
            VectorStore vectorStore;
            if (StringUtils.hasText(collectionNameOverride)) {
                vectorStore = storeProvider.createVectorStore(storeInstance, embeddingModel,
                        storeCredential, collectionNameOverride);
            } else {
                vectorStore = storeProvider.createVectorStore(storeInstance, embeddingModel, storeCredential);
            }

            if (vectorStore instanceof InitializingBean initBean) {
                initBean.afterPropertiesSet();
            }

            return Optional.of(new RagInfrastructure(
                    vectorStore, embeddingModel, storeProvider,
                    storeInstance, storeCredential, collectionName));
        } catch (Exception e) {
            log.error("Failed to build RAG infrastructure", e);
            return Optional.empty();
        }
    }

    /**
     * Builds RAG infrastructure using the Global Settings defaults.
     */
    public Optional<RagInfrastructure> buildFromGlobalSettings() {
        return buildFromGlobalSettings(null);
    }

    /**
     * Builds RAG infrastructure using the Global Settings defaults with a collection name override.
     */
    public Optional<RagInfrastructure> buildFromGlobalSettings(String collectionNameOverride) {
        return build(
                globalSettingsService.getDefaultEmbeddingModelId(),
                globalSettingsService.getDefaultEmbeddingStoreId(),
                collectionNameOverride);
    }

    /**
     * Re-indexes a document into the vector store: first deletes any existing embeddings whose
     * metadata matches {@code metadataKey=metadataValue}, then adds the new chunks. Avoids
     * duplicates when the same logical document is indexed again.
     *
     * @since 2026.2.4
     */
    public void reindexByMetadata(RagInfrastructure infra, List<Document> documents,
            String metadataKey, String metadataValue) {
        if (infra == null || documents == null || documents.isEmpty()) {
            return;
        }
        if (StringUtils.hasText(metadataKey) && StringUtils.hasText(metadataValue)) {
            try {
                infra.storeProvider().deleteByMetadata(infra.storeInstance(), infra.storeCredential(),
                        infra.collectionName(), metadataKey, metadataValue);
            } catch (Exception e) {
                log.warn("Failed to delete previous embeddings for {}={} (collection='{}'): {}",
                        metadataKey, metadataValue, infra.collectionName(), e.getMessage());
            }
        }
        infra.vectorStore().add(documents);
    }
}

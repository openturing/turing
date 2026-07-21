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
import com.viglet.turing.genai.provider.llm.TurHuggingFaceEmbeddingModelFactory;
import com.viglet.turing.genai.provider.llm.TurLocalEmbeddingModelFactory;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProvider;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProviderFactory;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
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
    private final TurLocalEmbeddingModelFactory localEmbeddingModelFactory;
    private final TurHuggingFaceEmbeddingModelFactory huggingFaceEmbeddingModelFactory;
    private final TurGenAiStoreProviderFactory storeProviderFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurGlobalSettingsService globalSettingsService;
    private final TurEmbeddingModelRepository embeddingModelRepository;
    private final TurLLMInstanceRepository instanceRepository;
    private final TurStoreInstanceRepository storeInstanceRepository;

    public TurRagContextBuilder(TurLlmModelFactory llmModelFactory,
            TurLocalEmbeddingModelFactory localEmbeddingModelFactory,
            TurHuggingFaceEmbeddingModelFactory huggingFaceEmbeddingModelFactory,
            TurGenAiStoreProviderFactory storeProviderFactory,
            TurSecretCryptoService secretCryptoService,
            TurGlobalSettingsService globalSettingsService,
            TurEmbeddingModelRepository embeddingModelRepository,
            TurLLMInstanceRepository instanceRepository,
            TurStoreInstanceRepository storeInstanceRepository) {
        this.llmModelFactory = llmModelFactory;
        this.localEmbeddingModelFactory = localEmbeddingModelFactory;
        this.huggingFaceEmbeddingModelFactory = huggingFaceEmbeddingModelFactory;
        this.storeProviderFactory = storeProviderFactory;
        this.secretCryptoService = secretCryptoService;
        this.globalSettingsService = globalSettingsService;
        this.embeddingModelRepository = embeddingModelRepository;
        this.instanceRepository = instanceRepository;
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
        // T622 — a local ONNX embedding model has no LLM instance; its ONNX +
        // tokenizer resources come from modelPath/tokenizerPath and run
        // in-process (no external embedding API).
        if (localEmbeddingModelFactory.supports(embModel.getProviderType())) {
            return localEmbeddingModelFactory.resolve(embModel);
        }

        // T623 — the HuggingFace provider is the same in-process ONNX runtime;
        // its modelReference holds a HF repo id (resolved to the same ONNX +
        // tokenizer URLs) instead of hand-typed paths.
        if (huggingFaceEmbeddingModelFactory.supports(embModel.getProviderType())) {
            return huggingFaceEmbeddingModelFactory.resolve(embModel);
        }

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
     * Resolves an embedding model by id, <b>unified entity first</b> (T756 / ADR
     * 0004). The T755 migration made every embedding model an {@code llm_instance}
     * with the same id, so an embedding-capable instance is preferred and adapted
     * into the {@link TurEmbeddingModel} shape {@link #resolveEmbeddingModel} already
     * understands; when none is found it falls back to the legacy
     * {@code embedding_model} row (non-destructive — that table is still present).
     * Returns {@code null} when neither resolves.
     */
    public TurEmbeddingModel resolveEmbeddingEntity(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        TurEmbeddingModel unified = instanceRepository.findById(id)
                .filter(TurRagContextBuilder::isEmbeddingCapable)
                .map(TurRagContextBuilder::toEmbeddingModel)
                .orElse(null);
        if (unified != null) {
            return unified;
        }
        return embeddingModelRepository.findById(id).orElse(null);
    }

    /** Convenience: {@link #resolveEmbeddingEntity} + {@link #resolveEmbeddingModel}. */
    public EmbeddingModel resolveEmbeddingModelById(String id) {
        TurEmbeddingModel embModel = resolveEmbeddingEntity(id);
        return embModel == null ? null : resolveEmbeddingModel(embModel);
    }

    /** An instance serves embeddings when it carries an embedding default (cloud) or ONNX path (local/HF). */
    static boolean isEmbeddingCapable(TurLLMInstance inst) {
        return StringUtils.hasText(inst.getEmbeddingModelName())
                || StringUtils.hasText(inst.getEmbeddingModelPath());
    }

    /**
     * Adapts a unified {@link TurLLMInstance} into the transient
     * {@link TurEmbeddingModel} the resolver consumes, so no downstream logic
     * changes. For a cloud instance the instance IS its own connection (url / key
     * / vendor), so it becomes the adapter's {@code turLLMInstance}; an in-process
     * (local/HF) instance has no parent — its ONNX paths carry the model.
     */
    private static TurEmbeddingModel toEmbeddingModel(TurLLMInstance inst) {
        String vendorId = inst.getTurLLMVendor() != null ? inst.getTurLLMVendor().getId() : null;
        boolean inProcess =
                TurLocalEmbeddingModelFactory.PROVIDER_TYPE.equalsIgnoreCase(vendorId)
                        || TurHuggingFaceEmbeddingModelFactory.PROVIDER_TYPE.equalsIgnoreCase(vendorId);

        TurEmbeddingModel em = new TurEmbeddingModel();
        em.setId(inst.getId());
        em.setTenantId(inst.getTenantId());
        em.setModelName(inst.getEmbeddingModelName());
        em.setModelReference(inst.getEmbeddingModelName());
        em.setProviderType(vendorId);
        em.setEnabled(inst.getEnabled());
        em.setModelPath(inst.getEmbeddingModelPath());
        em.setTokenizerPath(inst.getEmbeddingTokenizerPath());
        em.setBatchSize(inst.getEmbeddingBatchSize());
        em.setTurLLMInstance(inProcess ? null : inst);
        return em;
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

        TurEmbeddingModel embModel = resolveEmbeddingEntity(embeddingModelId);
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

    /**
     * T512 / §XXVIII.8 — re-index a document's chunks, using contextualized
     * embeddings when the configured embedding model supports them (Voyage
     * {@code voyage-context-3}). The chunks of one logical document are embedded
     * <i>together</i> so each vector carries the surrounding context, then the
     * precomputed vectors are upserted. When the model is a plain per-chunk
     * model — or the contextual call fails — this falls back to the standard
     * {@link #reindexByMetadata} path, so the feature is opt-in (it is the
     * embedding-model choice that turns it on) and fail-soft.
     *
     * @param documents     the ordered chunks of <b>one</b> source document
     * @return {@code true} when the contextual path was used; {@code false} when
     *         it fell back to per-chunk embedding
     * @since 2026.3.4
     */
    public boolean reindexByMetadataContextual(RagInfrastructure infra, List<Document> documents,
            String metadataKey, String metadataValue) {
        if (infra == null || documents == null || documents.isEmpty()) {
            reindexByMetadata(infra, documents, metadataKey, metadataValue);
            return false;
        }
        com.viglet.turing.genai.provider.llm.TurContextualEmbeddingModel contextual =
                com.viglet.turing.genai.provider.llm.TurContextualEmbeddingModel.of(infra.embeddingModel());
        if (contextual == null) {
            reindexByMetadata(infra, documents, metadataKey, metadataValue);
            return false;
        }
        try {
            List<String> chunkTexts = documents.stream()
                    .map(d -> d.getText() == null ? "" : d.getText())
                    .toList();
            List<float[]> vectors = contextual.embedDocumentChunks(chunkTexts);
            if (vectors.size() != documents.size()) {
                log.warn("Contextual embedding returned {} vectors for {} chunks (collection='{}') — "
                                + "falling back to per-chunk embedding",
                        vectors.size(), documents.size(), infra.collectionName());
                reindexByMetadata(infra, documents, metadataKey, metadataValue);
                return false;
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
            List<com.viglet.turing.genai.provider.store.TurStoreExportRecord> records =
                    new java.util.ArrayList<>(documents.size());
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                records.add(new com.viglet.turing.genai.provider.store.TurStoreExportRecord(
                        doc.getId(), doc.getText(), vectors.get(i), doc.getMetadata()));
            }
            infra.storeProvider().importChunks(infra.storeInstance(), infra.storeCredential(),
                    infra.collectionName(), records, infra.embeddingModel());
            return true;
        } catch (RuntimeException e) {
            log.warn("Contextual re-index failed (collection='{}'): {} — falling back to per-chunk embedding",
                    infra.collectionName(), e.getMessage());
            reindexByMetadata(infra, documents, metadataKey, metadataValue);
            return false;
        }
    }
}

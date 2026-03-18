package com.viglet.turing.service.asset;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProvider;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProviderFactory;
import com.viglet.turing.persistence.model.asset.TurAssetTrainingRecord;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.asset.TurAssetTrainingRecordRepository;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;
import com.viglet.turing.utils.TurFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates training (embedding indexing) of all MinIO assets into a Vector Store.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Service
public class TurAssetTrainingService {

    private static final Logger log = LoggerFactory.getLogger(TurAssetTrainingService.class);
    private static final int CHUNK_SIZE = 1024;
    private static final int MAX_TEXT_LENGTH = 100_000;

    private final TurMinioService minioService;
    private final TurGlobalSettingsService globalSettingsService;
    private final TurEmbeddingModelRepository embeddingModelRepository;
    private final TurStoreInstanceRepository storeInstanceRepository;
    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurGenAiStoreProviderFactory storeProviderFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurAssetTrainingRecordRepository trainingRecordRepository;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "asset-training");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<TurAssetTrainingStatus> status =
            new AtomicReference<>(TurAssetTrainingStatus.idle());

    public TurAssetTrainingService(TurMinioService minioService,
                                    TurGlobalSettingsService globalSettingsService,
                                    TurEmbeddingModelRepository embeddingModelRepository,
                                    TurStoreInstanceRepository storeInstanceRepository,
                                    TurGenAiLlmProviderFactory llmProviderFactory,
                                    TurGenAiStoreProviderFactory storeProviderFactory,
                                    TurSecretCryptoService secretCryptoService,
                                    TurAssetTrainingRecordRepository trainingRecordRepository) {
        this.minioService = minioService;
        this.globalSettingsService = globalSettingsService;
        this.embeddingModelRepository = embeddingModelRepository;
        this.storeInstanceRepository = storeInstanceRepository;
        this.llmProviderFactory = llmProviderFactory;
        this.storeProviderFactory = storeProviderFactory;
        this.secretCryptoService = secretCryptoService;
        this.trainingRecordRepository = trainingRecordRepository;
    }

    public TurAssetTrainingStatus getStatus() {
        return status.get();
    }

    /**
     * Returns a map of objectName → trainedAt ISO string for the given file names.
     * Files not yet trained are absent from the map.
     */
    public Map<String, String> getTrainedAtMap(List<String> objectNames) {
        return trainingRecordRepository.findByObjectNameIn(objectNames).stream()
                .collect(java.util.stream.Collectors.toMap(
                        TurAssetTrainingRecord::getObjectName,
                        r -> r.getTrainedAt().toString()));
    }

    public TurAssetTrainingStatus startTraining() {
        TurAssetTrainingStatus current = status.get();
        if (current.state() == TurAssetTrainingState.RUNNING) {
            return current;
        }

        if (!globalSettingsService.isRagEnabled()) {
            return new TurAssetTrainingStatus(TurAssetTrainingState.FAILED, 0, 0, 0, "", "", "RAG is not enabled in Global Settings.");
        }

        status.set(new TurAssetTrainingStatus(TurAssetTrainingState.RUNNING, 0, 0, 0,
                Instant.now().toString(), "", ""));

        executor.submit(this::doTraining);
        return status.get();
    }

    private void doTraining() {
        String startedAt = Instant.now().toString();
        try {
            String embModelId = globalSettingsService.getDefaultEmbeddingModelId();
            String storeId = globalSettingsService.getDefaultEmbeddingStoreId();
            VectorStore vectorStore = buildVectorStore();
            List<TurAssetItem> allFiles = minioService.listAllObjects();

            int total = allFiles.size();
            int processed = 0;
            int errors = 0;

            status.set(new TurAssetTrainingStatus(TurAssetTrainingState.RUNNING, total, 0, 0, startedAt, "", ""));
            log.info("[AssetTraining] Starting training with {} files", total);

            for (TurAssetItem file : allFiles) {
                try {
                    processFile(file, vectorStore, embModelId, storeId);
                } catch (Exception e) {
                    errors++;
                    log.warn("[AssetTraining] Failed to process file: {}", file.name(), e);
                }
                processed++;
                status.set(new TurAssetTrainingStatus(TurAssetTrainingState.RUNNING, total, processed, errors, startedAt, "", ""));
            }

            String completedAt = Instant.now().toString();
            status.set(new TurAssetTrainingStatus(TurAssetTrainingState.COMPLETED, total, processed, errors, startedAt, completedAt, ""));
            log.info("[AssetTraining] Completed: {}/{} files processed, {} errors", processed, total, errors);

        } catch (Exception e) {
            log.error("[AssetTraining] Training failed", e);
            status.set(new TurAssetTrainingStatus(TurAssetTrainingState.FAILED, 0, 0, 0,
                    startedAt, Instant.now().toString(), e.getMessage()));
        }
    }

    private void processFile(TurAssetItem file, VectorStore vectorStore,
                             String embModelId, String storeId) throws Exception {
        String objectName = file.name();
        String path = objectName.contains("/") ? objectName.substring(0, objectName.lastIndexOf('/') + 1) : "";
        String fileName = objectName.contains("/") ? objectName.substring(objectName.lastIndexOf('/') + 1) : objectName;

        log.info("[AssetTraining] Processing file: {} (type={}, size={})", objectName, file.contentType(), file.size());

        try (InputStream is = minioService.downloadObject(objectName)) {
            String text = TurFileUtils.parseDocument(is).orElse("");
            if (text.isBlank()) {
                log.info("[AssetTraining] Skipped (no text extracted): {}", objectName);
                return;
            }
            if (text.length() > MAX_TEXT_LENGTH) {
                log.info("[AssetTraining] Text truncated from {} to {} chars: {}", text.length(), MAX_TEXT_LENGTH, objectName);
                text = text.substring(0, MAX_TEXT_LENGTH);
            }

            log.info("[AssetTraining] Extracted {} chars from: {}", text.length(), objectName);

            Map<String, Object> metadata = Map.of(
                    "source", "minio-asset",
                    "objectName", objectName,
                    "objectPath", path,
                    "fileName", fileName,
                    "contentType", file.contentType(),
                    "size", file.size()
            );

            List<Document> documents = splitText(text).stream()
                    .map(chunk -> new Document(chunk, metadata))
                    .toList();

            log.info("[AssetTraining] Adding {} chunks to embedding store: {}", documents.size(), objectName);
            vectorStore.add(documents);
            log.info("[AssetTraining] Successfully stored {} chunks for: {}", documents.size(), objectName);

            // Save training record
            TurAssetTrainingRecord record = new TurAssetTrainingRecord();
            record.setObjectName(objectName);
            record.setObjectPath(path);
            record.setFileName(fileName);
            record.setContentType(file.contentType());
            record.setFileSize(file.size());
            record.setChunkCount(documents.size());
            record.setTrainedAt(Instant.now());
            record.setEmbeddingModelId(embModelId);
            record.setEmbeddingStoreId(storeId);
            trainingRecordRepository.save(record);

            log.info("[AssetTraining] Training record saved for: {}", objectName);
        }
    }

    private VectorStore buildVectorStore() {
        String embModelId = globalSettingsService.getDefaultEmbeddingModelId();
        String storeId = globalSettingsService.getDefaultEmbeddingStoreId();

        if (embModelId.isBlank() || storeId.isBlank()) {
            throw new IllegalStateException("Embedding Model and Store must be configured in Global Settings.");
        }

        TurEmbeddingModel embModel = embeddingModelRepository.findById(embModelId)
                .orElseThrow(() -> new IllegalStateException("Embedding Model not found: " + embModelId));
        TurLLMInstance llmInstance = embModel.getTurLLMInstance();
        if (llmInstance == null) {
            throw new IllegalStateException("Embedding Model '" + embModel.getModelName() + "' has no LLM instance.");
        }

        // Create a detached copy of the LLM instance with the embedding model reference
        // so the provider creates an embedding model with the correct name (e.g., "bge-m" instead of "llama3")
        TurLLMInstance embLlmInstance = new TurLLMInstance();
        embLlmInstance.setId(llmInstance.getId());
        embLlmInstance.setUrl(llmInstance.getUrl());
        embLlmInstance.setApiKeyEncrypted(llmInstance.getApiKeyEncrypted());
        // Do NOT copy providerOptionsJson — it may contain "embeddingModel" pointing to a different model
        embLlmInstance.setProviderOptionsJson(null);
        embLlmInstance.setTurLLMVendor(llmInstance.getTurLLMVendor());
        // Use the embedding model reference as the model name
        String embModelRef = embModel.getModelReference();
        embLlmInstance.setModelName(
                (embModelRef != null && !embModelRef.isBlank()) ? embModelRef : llmInstance.getModelName());
        log.info("[AssetTraining] Using embedding model: {} via provider: {}",
                embLlmInstance.getModelName(), embModel.getProviderType());

        TurGenAiLlmProvider llmProvider = llmProviderFactory.getProvider(embLlmInstance);
        String llmApiKey = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
        EmbeddingModel embeddingModel = llmProvider.createEmbeddingModel(embLlmInstance, llmApiKey);

        TurStoreInstance storeInstance = storeInstanceRepository.findById(storeId)
                .orElseThrow(() -> new IllegalStateException("Embedding Store not found: " + storeId));
        TurGenAiStoreProvider storeProvider = storeProviderFactory.getProvider(storeInstance);
        String storeCredential = secretCryptoService.decrypt(storeInstance.getCredentialEncrypted());

        VectorStore vectorStore = storeProvider.createVectorStore(storeInstance, embeddingModel, storeCredential);

        // ChromaVectorStore requires afterPropertiesSet() to initialize the collection
        if (vectorStore instanceof org.springframework.beans.factory.InitializingBean initBean) {
            try {
                initBean.afterPropertiesSet();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize vector store", e);
            }
        }

        return vectorStore;
    }

    private List<String> splitText(String text) {
        if (text == null || text.isBlank()) return List.of();
        if (text.length() <= CHUNK_SIZE) return List.of(text);
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length(); start += CHUNK_SIZE) {
            chunks.add(text.substring(start, Math.min(text.length(), start + CHUNK_SIZE)));
        }
        return chunks;
    }
}

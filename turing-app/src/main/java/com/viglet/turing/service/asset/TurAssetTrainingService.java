package com.viglet.turing.service.asset;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.genai.TurRagContextBuilder.RagInfrastructure;
import com.viglet.turing.genai.TurRagUtils;
import com.viglet.turing.genai.ocr.TurMistralOcrService;
import com.viglet.turing.persistence.model.asset.TurAssetTrainingRecord;
import com.viglet.turing.persistence.repository.asset.TurAssetTrainingRecordRepository;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.utils.TurFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
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
    private static final String OBJECT_NAME_FIELD = "objectName";

    private final TurStorageService storageService;
    private final TurGlobalSettingsService globalSettingsService;
    private final TurRagContextBuilder ragContextBuilder;
    private final TurAssetTrainingRecordRepository trainingRecordRepository;
    private final TurMistralOcrService mistralOcrService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "asset-training");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<TurAssetTrainingStatus> status =
            new AtomicReference<>(TurAssetTrainingStatus.idle());

    public TurAssetTrainingService(TurStorageService storageService,
                                    TurGlobalSettingsService globalSettingsService,
                                    TurRagContextBuilder ragContextBuilder,
                                    TurAssetTrainingRecordRepository trainingRecordRepository,
                                    TurMistralOcrService mistralOcrService) {
        this.storageService = storageService;
        this.globalSettingsService = globalSettingsService;
        this.ragContextBuilder = ragContextBuilder;
        this.trainingRecordRepository = trainingRecordRepository;
        this.mistralOcrService = mistralOcrService;
    }

    public TurAssetTrainingStatus getStatus() {
        return status.get();
    }

    /**
     * Returns a map of objectName -> trainedAt ISO string for the given file names.
     * Files not yet trained are absent from the map.
     */
    public Map<String, String> getTrainedAtMap(List<String> objectNames) {
        return trainingRecordRepository.findByObjectNameIn(objectNames).stream()
                .collect(java.util.stream.Collectors.toMap(
                        TurAssetTrainingRecord::getObjectName,
                        r -> r.getTrainedAt().toString()));
    }

    /**
     * Index a single file (called from event listener on upload).
     */
    public void indexSingleFile(String objectName, String contentType, long size) {
        try {
            var infra = ragContextBuilder.buildFromGlobalSettings().orElse(null);
            if (infra == null) {
                log.warn("[AssetEvent] RAG enabled but embedding model/store not configured. Skipping: {}", objectName);
                return;
            }
            TurAssetItem item = new TurAssetItem(objectName, size, contentType, Instant.now().toString(), false);
            processFile(item, infra);
            log.info("[AssetEvent] Successfully indexed: {}", objectName);
        } catch (Exception e) {
            log.error("[AssetEvent] Failed to index file: {}", objectName, e);
        }
    }

    /**
     * Deindex a single file (called from event listener on delete).
     */
    public void deindexSingleFile(String objectName) {
        try {
            var infra = ragContextBuilder.buildFromGlobalSettings().orElse(null);
            if (infra == null) return;

            infra.storeProvider().deleteByMetadata(infra.storeInstance(), infra.storeCredential(),
                    infra.collectionName(), OBJECT_NAME_FIELD, objectName);
            trainingRecordRepository.deleteById(objectName);
            log.info("[AssetEvent] Successfully deindexed: {}", objectName);
        } catch (Exception e) {
            log.error("[AssetEvent] Failed to deindex file: {}", objectName, e);
        }
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
            var infra = ragContextBuilder.buildFromGlobalSettings()
                    .orElseThrow(() -> new IllegalStateException(
                            "Embedding Model and Store must be configured in Global Settings."));
            List<TurAssetItem> allFiles = storageService.listAllObjects();

            int total = allFiles.size();
            int processed = 0;
            int errors = 0;

            status.set(new TurAssetTrainingStatus(TurAssetTrainingState.RUNNING, total, 0, 0, startedAt, "", ""));
            log.info("[AssetTraining] Starting training with {} files", total);

            for (TurAssetItem file : allFiles) {
                if (!tryProcessFile(file, infra)) {
                    errors++;
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

    /** Processes one file, returning {@code false} (and logging) when it fails. */
    private boolean tryProcessFile(TurAssetItem file, RagInfrastructure infra) {
        try {
            processFile(file, infra);
            return true;
        } catch (Exception e) {
            log.warn("[AssetTraining] Failed to process file: {}", file.name(), e);
            return false;
        }
    }

    private void processFile(TurAssetItem file, RagInfrastructure infra) throws Exception {
        String objectName = file.name();
        String path = objectName.contains("/") ? objectName.substring(0, objectName.lastIndexOf('/') + 1) : "";
        String fileName = objectName.contains("/") ? objectName.substring(objectName.lastIndexOf('/') + 1) : objectName;

        log.info("[AssetTraining] Processing file: {} (type={}, size={})", objectName, file.contentType(), file.size());

        byte[] fileBytes;
        try (InputStream is = storageService.downloadObject(objectName)) {
            fileBytes = is.readAllBytes();
        }
        {
            String text = TurFileUtils.parseDocument(new ByteArrayInputStream(fileBytes)).orElse("");
            // T515 / §XXVIII.11 — augment Tika with Mistral OCR for scanned PDFs /
            // image-only docs Tika couldn't read. No-op (returns the Tika text)
            // when OCR is disabled, the file isn't OCR-eligible, or Tika already
            // got text — so the common text-document path is unchanged.
            text = mistralOcrService.augment(text, fileBytes, file.contentType(), fileName);
            if (text.isBlank()) {
                log.info("[AssetTraining] Skipped (no text extracted): {}", objectName);
                infra.storeProvider().deleteByMetadata(infra.storeInstance(), infra.storeCredential(),
                        infra.collectionName(), OBJECT_NAME_FIELD, objectName);
                trainingRecordRepository.deleteById(objectName);
                return;
            }
            text = TurRagUtils.truncateText(text, TurRagUtils.MAX_TEXT_LENGTH);

            log.info("[AssetTraining] Extracted {} chars from: {}", text.length(), objectName);

            Map<String, Object> metadata = Map.of(
                    "source", "storage-asset",
                    OBJECT_NAME_FIELD, objectName,
                    "objectPath", path,
                    "fileName", fileName,
                    "contentType", file.contentType(),
                    "size", file.size()
            );

            List<Document> documents = TurRagUtils.createDocuments(text, metadata);

            log.info("[AssetTraining] Reindexing {} chunks for: {} (replaces previous embeddings)",
                    documents.size(), objectName);
            // T512 / §XXVIII.8 — when the configured embedding model is a
            // contextualized model (voyage-context-3), embed all chunks of this
            // document together so each vector carries the surrounding context;
            // otherwise this is identical to reindexByMetadata (per-chunk embed).
            boolean contextual = ragContextBuilder.reindexByMetadataContextual(
                    infra, documents, OBJECT_NAME_FIELD, objectName);
            log.info("[AssetTraining] Successfully stored {} chunks for: {} (contextual={})",
                    documents.size(), objectName, contextual);

            // Upsert training record
            TurAssetTrainingRecord trainingRecord = new TurAssetTrainingRecord();
            trainingRecord.setObjectName(objectName);
            trainingRecord.setObjectPath(path);
            trainingRecord.setFileName(fileName);
            trainingRecord.setContentType(file.contentType());
            trainingRecord.setFileSize(file.size());
            trainingRecord.setChunkCount(documents.size());
            trainingRecord.setTrainedAt(Instant.now());
            trainingRecord.setEmbeddingModelId(globalSettingsService.getDefaultEmbeddingModelId());
            trainingRecord.setEmbeddingStoreId(globalSettingsService.getDefaultEmbeddingStoreId());
            trainingRecordRepository.save(trainingRecord);

            log.info("[AssetTraining] Training record saved for: {}", objectName);
        }
    }
}

package com.viglet.turing.genai.tool;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool calling service that searches the global RAG knowledge base
 * (assets indexed in the default Embedding Store).
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Service
public class TurRagSearchToolService {

    private static final Logger log = LoggerFactory.getLogger(TurRagSearchToolService.class);

    private final TurGlobalSettingsService globalSettingsService;
    private final TurEmbeddingModelRepository embeddingModelRepository;
    private final TurStoreInstanceRepository storeInstanceRepository;
    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurGenAiStoreProviderFactory storeProviderFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurAssetTrainingRecordRepository trainingRecordRepository;

    public TurRagSearchToolService(TurGlobalSettingsService globalSettingsService,
                                    TurEmbeddingModelRepository embeddingModelRepository,
                                    TurStoreInstanceRepository storeInstanceRepository,
                                    TurGenAiLlmProviderFactory llmProviderFactory,
                                    TurGenAiStoreProviderFactory storeProviderFactory,
                                    TurSecretCryptoService secretCryptoService,
                                    TurAssetTrainingRecordRepository trainingRecordRepository) {
        this.globalSettingsService = globalSettingsService;
        this.embeddingModelRepository = embeddingModelRepository;
        this.storeInstanceRepository = storeInstanceRepository;
        this.llmProviderFactory = llmProviderFactory;
        this.storeProviderFactory = storeProviderFactory;
        this.secretCryptoService = secretCryptoService;
        this.trainingRecordRepository = trainingRecordRepository;
    }

    public boolean isAvailable() {
        return globalSettingsService.isRagEnabled()
                && !globalSettingsService.getDefaultEmbeddingModelId().isBlank()
                && !globalSettingsService.getDefaultEmbeddingStoreId().isBlank();
    }

    @Tool(name = "search_knowledge_base", description = ".")
    public String searchKnowledgeBase(String query, Integer maxResults) {
        if (!isAvailable()) {
            return "RAG is not enabled. Configure Embedding Model and Store in Global Settings.";
        }

        int topK = (maxResults != null && maxResults > 0) ? Math.min(maxResults, 20) : 5;

        try {
            VectorStore vectorStore = buildVectorStore();

            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(0.5)
                    .build();

            List<Document> results = vectorStore.similaritySearch(searchRequest);
            log.info("[RAG] Search '{}' returned {} results (topK={})", query, results.size(), topK);

            if (results.isEmpty()) {
                return "No relevant documents found in the knowledge base for: " + query;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(results.size()).append(" relevant document(s):\n\n");

            for (int i = 0; i < results.size(); i++) {
                Document doc = results.get(i);
                String fileName = doc.getMetadata().getOrDefault("fileName", "unknown").toString();
                String objectName = doc.getMetadata().getOrDefault("objectName", "").toString();
                String contentType = doc.getMetadata().getOrDefault("contentType", "").toString();

                sb.append("--- Document ").append(i + 1).append(" ---\n");
                sb.append("File: ").append(fileName).append("\n");
                sb.append("Path: ").append(objectName).append("\n");
                if (!contentType.isBlank()) {
                    sb.append("Type: ").append(contentType).append("\n");
                }
                sb.append("Content:\n").append(doc.getText()).append("\n\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("[RAG] Search failed for query: {}", query, e);
            return "Error searching knowledge base: " + e.getMessage();
        }
    }

    @Tool(name = "knowledge_base_stats", description = ".")
    public String knowledgeBaseStats() {
        if (!isAvailable()) {
            return "RAG is not enabled.";
        }
        try {
            List<TurAssetTrainingRecord> records = trainingRecordRepository.findAll();
            if (records.isEmpty()) {
                return "The knowledge base is empty. No files have been indexed yet.";
            }

            long totalFiles = records.size();
            long totalChunks = records.stream().mapToInt(TurAssetTrainingRecord::getChunkCount).sum();
            long totalSize = records.stream().mapToLong(TurAssetTrainingRecord::getFileSize).sum();

            Map<String, Long> byType = records.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.getContentType() != null ? r.getContentType() : "unknown",
                            Collectors.counting()));

            StringBuilder sb = new StringBuilder();
            sb.append("Knowledge Base Statistics:\n");
            sb.append("- Total indexed files: ").append(totalFiles).append("\n");
            sb.append("- Total embedding chunks: ").append(totalChunks).append("\n");
            sb.append("- Total file size: ").append(formatSize(totalSize)).append("\n");
            sb.append("\nFiles by type:\n");
            byType.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> sb.append("  - ").append(e.getKey()).append(": ").append(e.getValue()).append(" file(s)\n"));

            return sb.toString();
        } catch (Exception e) {
            log.error("[RAG] Stats failed", e);
            return "Error retrieving knowledge base stats: " + e.getMessage();
        }
    }

    @Tool(name = "list_knowledge_base_files", description = ".")
    public String listKnowledgeBaseFiles(String filterKeyword, String filterContentType) {
        if (!isAvailable()) {
            return "RAG is not enabled.";
        }
        try {
            List<TurAssetTrainingRecord> records;

            if (filterKeyword != null && !filterKeyword.isBlank()) {
                records = trainingRecordRepository.findByFileNameContainingIgnoreCase(filterKeyword.trim());
            } else if (filterContentType != null && !filterContentType.isBlank()) {
                records = trainingRecordRepository.findByContentType(filterContentType.trim());
            } else {
                records = trainingRecordRepository.findAll();
            }

            if (records.isEmpty()) {
                return "No indexed files found" +
                        (filterKeyword != null ? " matching '" + filterKeyword + "'" : "") +
                        (filterContentType != null ? " of type '" + filterContentType + "'" : "") + ".";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Indexed files (").append(records.size()).append("):\n\n");

            for (TurAssetTrainingRecord r : records) {
                sb.append("- ").append(r.getFileName())
                        .append(" | path: ").append(r.getObjectName())
                        .append(" | type: ").append(r.getContentType())
                        .append(" | size: ").append(formatSize(r.getFileSize()))
                        .append(" | chunks: ").append(r.getChunkCount())
                        .append(" | trained: ").append(r.getTrainedAt())
                        .append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("[RAG] List files failed", e);
            return "Error listing knowledge base files: " + e.getMessage();
        }
    }

    @Tool(name = "get_file_from_knowledge_base", description = ".")
    public String getFileFromKnowledgeBase(String fileName, Integer maxChunks) {
        if (!isAvailable()) {
            return "RAG is not enabled.";
        }
        try {
            VectorStore vectorStore = buildVectorStore();
            int topK = (maxChunks != null && maxChunks > 0) ? Math.min(maxChunks, 50) : 10;

            // Search specifically for this file's content
            SearchRequest request = SearchRequest.builder()
                    .query(fileName)
                    .topK(topK)
                    .similarityThreshold(0.3)
                    .build();

            List<Document> results = vectorStore.similaritySearch(request);

            // Filter to only chunks from the matching file
            List<Document> fileChunks = results.stream()
                    .filter(d -> {
                        String docFileName = d.getMetadata().getOrDefault("fileName", "").toString();
                        String docObjectName = d.getMetadata().getOrDefault("objectName", "").toString();
                        return docFileName.toLowerCase().contains(fileName.toLowerCase())
                                || docObjectName.toLowerCase().contains(fileName.toLowerCase());
                    })
                    .toList();

            if (fileChunks.isEmpty()) {
                return "No content found for file: " + fileName + ". Make sure the file name is correct and has been indexed.";
            }

            String matchedFile = fileChunks.getFirst().getMetadata().getOrDefault("objectName", fileName).toString();
            StringBuilder sb = new StringBuilder();
            sb.append("Content from: ").append(matchedFile).append("\n");
            sb.append("Showing ").append(fileChunks.size()).append(" chunk(s):\n\n");

            for (Document doc : fileChunks) {
                sb.append(doc.getText()).append("\n---\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("[RAG] Get file content failed for: {}", fileName, e);
            return "Error retrieving file content: " + e.getMessage();
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private VectorStore buildVectorStore() {
        String embModelId = globalSettingsService.getDefaultEmbeddingModelId();
        String storeId = globalSettingsService.getDefaultEmbeddingStoreId();

        TurEmbeddingModel embModel = embeddingModelRepository.findById(embModelId)
                .orElseThrow(() -> new IllegalStateException("Embedding Model not found: " + embModelId));
        TurLLMInstance llmInstance = embModel.getTurLLMInstance();
        if (llmInstance == null) {
            throw new IllegalStateException("Embedding Model has no LLM instance.");
        }

        // Detached LLM instance with embedding model reference
        TurLLMInstance embLlmInstance = new TurLLMInstance();
        embLlmInstance.setId(llmInstance.getId());
        embLlmInstance.setUrl(llmInstance.getUrl());
        embLlmInstance.setApiKeyEncrypted(llmInstance.getApiKeyEncrypted());
        embLlmInstance.setProviderOptionsJson(null);
        embLlmInstance.setTurLLMVendor(llmInstance.getTurLLMVendor());
        String embModelRef = embModel.getModelReference();
        embLlmInstance.setModelName(
                (embModelRef != null && !embModelRef.isBlank()) ? embModelRef : llmInstance.getModelName());

        TurGenAiLlmProvider llmProvider = llmProviderFactory.getProvider(embLlmInstance);
        String llmApiKey = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
        EmbeddingModel embeddingModel = llmProvider.createEmbeddingModel(embLlmInstance, llmApiKey);

        TurStoreInstance storeInstance = storeInstanceRepository.findById(storeId)
                .orElseThrow(() -> new IllegalStateException("Embedding Store not found: " + storeId));
        var storeProvider = storeProviderFactory.getProvider(storeInstance);
        String storeCredential = secretCryptoService.decrypt(storeInstance.getCredentialEncrypted());

        VectorStore vectorStore = storeProvider.createVectorStore(storeInstance, embeddingModel, storeCredential);

        if (vectorStore instanceof org.springframework.beans.factory.InitializingBean initBean) {
            try {
                initBean.afterPropertiesSet();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize vector store", e);
            }
        }

        return vectorStore;
    }
}

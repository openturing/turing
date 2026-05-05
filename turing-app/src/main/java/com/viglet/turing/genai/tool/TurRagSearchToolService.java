package com.viglet.turing.genai.tool;

import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.persistence.model.asset.TurAssetTrainingRecord;
import com.viglet.turing.persistence.repository.asset.TurAssetTrainingRecordRepository;
import com.viglet.turing.system.TurGlobalSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private static final String OBJECT_NAME_FIELD = "objectName";
    private static final String RAG_NOT_ENABLED_MSG = "RAG is not enabled.";

    private final TurGlobalSettingsService globalSettingsService;
    private final TurRagContextBuilder ragContextBuilder;
    private final TurAssetTrainingRecordRepository trainingRecordRepository;

    public TurRagSearchToolService(TurGlobalSettingsService globalSettingsService,
                                    TurRagContextBuilder ragContextBuilder,
                                    TurAssetTrainingRecordRepository trainingRecordRepository) {
        this.globalSettingsService = globalSettingsService;
        this.ragContextBuilder = ragContextBuilder;
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
            VectorStore vectorStore = ragContextBuilder.buildFromGlobalSettings()
                    .orElseThrow(() -> new IllegalStateException("RAG infrastructure not configured."))
                    .vectorStore();

            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(0.5)
                    .build();

            List<Document> results = vectorStore.similaritySearch(searchRequest);
            log.debug("[RAG] Search '{}' returned {} results (topK={})", query, results.size(), topK);

            if (results.isEmpty()) {
                return "No relevant documents found in the knowledge base for: " + query;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(results.size()).append(" relevant document(s):\n\n");

            java.util.LinkedHashMap<String, String> fileLinks = new java.util.LinkedHashMap<>();

            for (int i = 0; i < results.size(); i++) {
                Document doc = results.get(i);
                String objectName = doc.getMetadata().getOrDefault(OBJECT_NAME_FIELD, "").toString();
                String contentType = doc.getMetadata().getOrDefault("contentType", "").toString();

                String downloadUrl = "/api/asset/download?objectName="
                        + URLEncoder.encode(objectName, StandardCharsets.UTF_8);

                fileLinks.putIfAbsent(objectName, downloadUrl);

                sb.append("--- Document ").append(i + 1).append(" ---\n");
                sb.append("File: ").append(objectName).append("\n");
                if (!contentType.isBlank()) {
                    sb.append("Type: ").append(contentType).append("\n");
                }
                sb.append("Content:\n").append(doc.getText()).append("\n\n");
            }

            sb.append("REFERENCES (copy these markdown links verbatim to your response):\n");
            for (var entry : fileLinks.entrySet()) {
                sb.append("- [").append(entry.getKey()).append("](").append(entry.getValue()).append(")\n");
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
            return RAG_NOT_ENABLED_MSG;
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
            return RAG_NOT_ENABLED_MSG;
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
            return RAG_NOT_ENABLED_MSG;
        }
        try {
            VectorStore vectorStore = ragContextBuilder.buildFromGlobalSettings()
                    .orElseThrow(() -> new IllegalStateException("RAG infrastructure not configured."))
                    .vectorStore();
            int topK = (maxChunks != null && maxChunks > 0) ? Math.min(maxChunks, 50) : 10;

            SearchRequest request = SearchRequest.builder()
                    .query(fileName)
                    .topK(topK)
                    .similarityThreshold(0.3)
                    .build();

            List<Document> results = vectorStore.similaritySearch(request);

            List<Document> fileChunks = results.stream()
                    .filter(d -> {
                        String docFileName = d.getMetadata().getOrDefault("fileName", "").toString();
                        String docObjectName = d.getMetadata().getOrDefault(OBJECT_NAME_FIELD, "").toString();
                        return docFileName.toLowerCase().contains(fileName.toLowerCase())
                                || docObjectName.toLowerCase().contains(fileName.toLowerCase());
                    })
                    .toList();

            if (fileChunks.isEmpty()) {
                return "No content found for file: " + fileName + ". Make sure the file name is correct and has been indexed.";
            }

            String matchedFile = fileChunks.getFirst().getMetadata().getOrDefault(OBJECT_NAME_FIELD, fileName).toString();
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
}

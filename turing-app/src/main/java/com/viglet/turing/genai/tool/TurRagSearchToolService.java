package com.viglet.turing.genai.tool;

import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.genai.provider.store.lucene.TurLuceneVectorStore;
import com.viglet.turing.persistence.model.asset.TurAssetTrainingRecord;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.asset.TurAssetTrainingRecordRepository;
import com.viglet.turing.system.TurGlobalSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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

    /**
     * T19 / §III.3 — minimum cosine score the vector top-1 must hit for
     * the result to be trusted. Below this, the tool treats the vector
     * pass as "no signal" and tries BM25 over {@code FIELD_CONTENT}
     * before giving up.
     *
     * <p>0.5 is the same threshold the legacy SearchRequest used as a
     * hard filter — preserving that boundary preserves UX continuity
     * (queries that crossed the bar before continue to take the vector
     * path; queries that didn't, but happen to have keyword matches,
     * now get a fallback instead of a flat "no results found"). Tune
     * down to ~0.35 if vector recall drops on a noisy corpus; the
     * fallback adds latency only on cold queries that miss the bar.
     */
    private static final double MIN_VECTOR_CONFIDENCE = 0.5;

    private final TurGlobalSettingsService globalSettingsService;
    private final TurRagContextBuilder ragContextBuilder;
    private final TurAssetTrainingRecordRepository trainingRecordRepository;
    private final TurAIAgentRepository agentRepository;

    public TurRagSearchToolService(TurGlobalSettingsService globalSettingsService,
                                    TurRagContextBuilder ragContextBuilder,
                                    TurAssetTrainingRecordRepository trainingRecordRepository,
                                    TurAIAgentRepository agentRepository) {
        this.globalSettingsService = globalSettingsService;
        this.ragContextBuilder = ragContextBuilder;
        this.trainingRecordRepository = trainingRecordRepository;
        this.agentRepository = agentRepository;
    }

    public boolean isAvailable() {
        return globalSettingsService.isRagEnabled()
                && !globalSettingsService.getDefaultEmbeddingModelId().isBlank()
                && !globalSettingsService.getDefaultEmbeddingStoreId().isBlank();
    }

    @Tool(name = "search_knowledge_base", description = ".")
    public String searchKnowledgeBase(
            @ToolParam(description = "The search query in natural language") String query,
            @ToolParam(required = false, description = "Number of results to return (1-20)") Integer maxResults,
            ToolContext toolContext) {
        if (!isAvailable()) {
            return "RAG is not enabled. Configure Embedding Model and Store in Global Settings.";
        }

        int topK = (maxResults != null && maxResults > 0) ? Math.min(maxResults, 20) : 5;

        // T19 / §III.3 + T24 / §III.2 — per-agent retrieval mode flags.
        // The agentId arrives via TOOL_CONTEXT_AGENT_ID (published by
        // TurAgentChatExecutor on every turn); missing or unknown → both
        // default TRUE (post-T24 behavior: hybrid retrieval). Looked up
        // here instead of injected at construction because this service
        // is a singleton @Service called from many agents.
        AgentRagFlags flags = resolveAgentRagFlags(toolContext);
        final boolean bm25Enabled = flags.bm25Enabled();
        final boolean hybridEnabled = flags.hybridEnabled();

        try {
            VectorStore vectorStore = ragContextBuilder.buildFromGlobalSettings()
                    .orElseThrow(() -> new IllegalStateException("RAG infrastructure not configured."))
                    .vectorStore();

            // Pick the retrieval mode based on the per-agent flags:
            //   HYBRID    — bm25Enabled && hybridEnabled (T24 default):
            //               always fuse vector + BM25 via RRF.
            //   FALLBACK  — bm25Enabled && !hybridEnabled (legacy T19):
            //               vector first, BM25 only if vector top-1 < MIN.
            //   VECTOR    — !bm25Enabled: strict vector, BM25 never runs.
            boolean useHybrid = bm25Enabled && hybridEnabled
                    && vectorStore instanceof TurLuceneVectorStore;
            List<Document> results;
            boolean allKeywordOnly = false;
            if (useHybrid) {
                TurLuceneVectorStore lucene = (TurLuceneVectorStore) vectorStore;
                results = lucene.hybridSearch(query, topK, 0.0);
                allKeywordOnly = !results.isEmpty() && results.stream().allMatch(d ->
                        Boolean.TRUE.equals(d.getMetadata()
                                .get(TurLuceneVectorStore.METADATA_KEYWORD_ONLY_FALLBACK)));
                log.debug("[RAG] Hybrid search '{}' returned {} fused result(s); allKeywordOnly={}",
                        query, results.size(), allKeywordOnly);
            } else {
                // T19 / §III.3: vector pass first with similarityThreshold=0
                // so we receive ALL topK hits regardless of score; the
                // confidence gate moves into application code below.
                SearchRequest searchRequest = SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(0.0)
                        .build();
                results = vectorStore.similaritySearch(searchRequest);
                log.debug("[RAG] Vector search '{}' returned {} result(s)", query, results.size());

                if (bm25Enabled && shouldFallbackToBm25(results)
                        && vectorStore instanceof TurLuceneVectorStore lucene) {
                    List<Document> bm25Hits = lucene.bm25Fallback(query, topK);
                    if (!bm25Hits.isEmpty()) {
                        log.info("[RAG] Vector top-1 < {} for '{}'; BM25 fallback returned {} hit(s)",
                                MIN_VECTOR_CONFIDENCE, query, bm25Hits.size());
                        results = bm25Hits;
                        allKeywordOnly = true;
                    }
                } else if (!bm25Enabled && shouldFallbackToBm25(results)) {
                    log.info("[RAG] Vector top-1 < {} for '{}' but agent has ragBm25Fallback=false "
                                    + "— returning vector hits as-is",
                            MIN_VECTOR_CONFIDENCE, query);
                }
            }
            boolean fellBackToBm25 = allKeywordOnly;

            if (results.isEmpty()) {
                return "No relevant documents found in the knowledge base for: " + query;
            }

            StringBuilder sb = new StringBuilder();
            if (fellBackToBm25) {
                // The "keyword-only" header is verbatim what the LLM uses to
                // decide whether to soften its answer ("Based on a keyword
                // match in the knowledge base..."). Don't paraphrase or
                // localize without checking the agent's system prompt
                // language; default English is what the existing prompts
                // expect.
                sb.append("⚠️ KEYWORD-ONLY MATCH: the embedding model did not recognize this query; ");
                sb.append("the documents below were matched by keyword (BM25) instead. ");
                sb.append("Treat the relevance as approximate and verify against the cited content before quoting.\n\n");
            }
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

    /**
     * T19 / §III.3 — gate for the BM25 fallback. Returns true when the
     * vector pass either returned nothing OR returned only hits whose
     * top-1 score is below {@link #MIN_VECTOR_CONFIDENCE} (cosine
     * similarity, range [-1, 1] in theory but [0, 1] for OpenAI-style
     * normalized embeddings).
     *
     * <p>Reading score off the first element is sufficient — the vector
     * store returns results sorted by score descending, so if the best
     * one doesn't clear the bar, none do.
     */
    private static boolean shouldFallbackToBm25(List<Document> vectorResults) {
        if (vectorResults.isEmpty()) {
            return true;
        }
        Double topScore = vectorResults.get(0).getScore();
        return topScore == null || topScore < MIN_VECTOR_CONFIDENCE;
    }

    /**
     * T19 + T24 — per-agent RAG retrieval flags consumed by
     * {@link #searchKnowledgeBase}. Encapsulates both {@code ragBm25Fallback}
     * (whether BM25 runs at all) and {@code ragHybridSearch} (when it runs,
     * fuse with vector via RRF vs. fall back only on low vector confidence).
     */
    private record AgentRagFlags(boolean bm25Enabled, boolean hybridEnabled) {
        static AgentRagFlags defaults() {
            // Post-T24 defaults: BM25 on + hybrid fusion on. New agents
            // get the strictly-better retrieval out of the box.
            return new AgentRagFlags(true, true);
        }
    }

    /**
     * T19 + T24 — resolves both per-agent RAG flags from
     * {@code TurAIAgent.ragBm25Fallback} and {@code TurAIAgent.ragHybridSearch}.
     * The agent id is published into {@link ToolContext} under
     * {@link com.viglet.turing.genai.tool.TurCustomToolCallbackService#TOOL_CONTEXT_AGENT_ID}
     * by {@code TurAgentChatExecutor} on every turn. Falls back to
     * {@link AgentRagFlags#defaults()} when:
     *
     * <ul>
     *   <li>{@code toolContext} is null (legacy callers / unit tests),</li>
     *   <li>the agent id is missing from the context,</li>
     *   <li>the agent row is gone (deleted between the turn starting
     *       and the tool firing — rare but possible).</li>
     * </ul>
     *
     * @since 2026.2.7
     */
    private AgentRagFlags resolveAgentRagFlags(ToolContext toolContext) {
        if (toolContext == null) {
            return AgentRagFlags.defaults();
        }
        Object rawAgentId = toolContext.getContext().get(
                com.viglet.turing.genai.tool.TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID);
        if (rawAgentId == null) {
            return AgentRagFlags.defaults();
        }
        String agentId = rawAgentId.toString();
        if (agentId.isBlank()) {
            return AgentRagFlags.defaults();
        }
        return agentRepository.findById(agentId)
                .map(agent -> new AgentRagFlags(agent.isRagBm25Fallback(), agent.isRagHybridSearch()))
                .orElseGet(AgentRagFlags::defaults);
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

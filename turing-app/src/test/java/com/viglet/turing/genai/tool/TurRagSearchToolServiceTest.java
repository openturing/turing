package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.persistence.model.asset.TurAssetTrainingRecord;
import com.viglet.turing.persistence.repository.asset.TurAssetTrainingRecordRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * Tests for TurRagSearchToolService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurRagSearchToolServiceTest {

    @Mock
    private TurGlobalSettingsService globalSettingsService;
    @Mock
    private TurRagContextBuilder ragContextBuilder;
    @Mock
    private TurAssetTrainingRecordRepository trainingRecordRepository;
    @Mock
    private com.viglet.turing.persistence.repository.agent.TurAIAgentRepository agentRepository;

    private TurRagSearchToolService service;

    @BeforeEach
    void setUp() {
        service = new TurRagSearchToolService(globalSettingsService, ragContextBuilder, trainingRecordRepository,
                agentRepository);
    }

    @Test
    void isAvailableShouldReturnFalseWhenRagDisabled() {
        when(globalSettingsService.isRagEnabled()).thenReturn(false);
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void isAvailableShouldReturnFalseWhenEmbeddingModelIdBlank() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("");
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void isAvailableShouldReturnFalseWhenStoreIdBlank() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("");
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void isAvailableShouldReturnTrueWhenAllConfigured() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        assertThat(service.isAvailable()).isTrue();
    }

    @Test
    void searchKnowledgeBaseShouldReturnDisabledMessageWhenNotAvailable() {
        when(globalSettingsService.isRagEnabled()).thenReturn(false);
        String result = service.searchKnowledgeBase("test query", 5, (org.springframework.ai.chat.model.ToolContext) null);
        assertThat(result).contains("RAG is not enabled");
    }

    @Test
    void knowledgeBaseStatsShouldReturnDisabledMessageWhenNotAvailable() {
        when(globalSettingsService.isRagEnabled()).thenReturn(false);
        String result = service.knowledgeBaseStats();
        assertThat(result).isEqualTo("RAG is not enabled.");
    }

    @Test
    void knowledgeBaseStatsShouldReturnEmptyMessageWhenNoRecords() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(trainingRecordRepository.findAll()).thenReturn(Collections.emptyList());

        String result = service.knowledgeBaseStats();
        assertThat(result).contains("knowledge base is empty");
    }

    @Test
    void knowledgeBaseStatsShouldReturnStatsWithRecords() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");

        TurAssetTrainingRecord record1 = new TurAssetTrainingRecord();
        record1.setChunkCount(10);
        record1.setFileSize(2048L);
        record1.setContentType("application/pdf");

        TurAssetTrainingRecord record2 = new TurAssetTrainingRecord();
        record2.setChunkCount(5);
        record2.setFileSize(1024L);
        record2.setContentType("text/plain");

        when(trainingRecordRepository.findAll()).thenReturn(List.of(record1, record2));

        String result = service.knowledgeBaseStats();
        assertThat(result)
                .contains("Total indexed files: 2")
                .contains("Total embedding chunks: 15")
                .contains("application/pdf")
                .contains("text/plain");
    }

    @Test
    void listKnowledgeBaseFilesShouldReturnDisabledMessageWhenNotAvailable() {
        when(globalSettingsService.isRagEnabled()).thenReturn(false);
        String result = service.listKnowledgeBaseFiles(null, null);
        assertThat(result).isEqualTo("RAG is not enabled.");
    }

    @Test
    void listKnowledgeBaseFilesShouldReturnNoFilesMessage() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(trainingRecordRepository.findAll()).thenReturn(Collections.emptyList());

        String result = service.listKnowledgeBaseFiles(null, null);
        assertThat(result).contains("No indexed files found");
    }

    @Test
    void listKnowledgeBaseFilesShouldFilterByKeyword() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(trainingRecordRepository.findByFileNameContainingIgnoreCase("report"))
                .thenReturn(Collections.emptyList());

        String result = service.listKnowledgeBaseFiles("report", null);
        assertThat(result).contains("No indexed files found").contains("report");
    }

    @Test
    void listKnowledgeBaseFilesShouldFilterByContentType() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(trainingRecordRepository.findByContentType("text/plain"))
                .thenReturn(Collections.emptyList());

        String result = service.listKnowledgeBaseFiles(null, "text/plain");
        assertThat(result).contains("No indexed files found").contains("text/plain");
    }

    @Test
    void getFileFromKnowledgeBaseShouldReturnDisabledMessageWhenNotAvailable() {
        when(globalSettingsService.isRagEnabled()).thenReturn(false);
        String result = service.getFileFromKnowledgeBase("file.pdf", 10);
        assertThat(result).isEqualTo("RAG is not enabled.");
    }

    @Test
    void formatSizeShouldFormatBytes() throws Exception {
        Method method = TurRagSearchToolService.class.getDeclaredMethod("formatSize", long.class);
        method.setAccessible(true);

        assertThat(method.invoke(null, 500L)).isEqualTo("500 B");
        assertThat((String) method.invoke(null, 2048L)).matches("2[.,]0 KB");
        assertThat((String) method.invoke(null, 1_048_576L)).matches("1[.,]0 MB");
        assertThat((String) method.invoke(null, 1_073_741_824L)).matches("1[.,]0 GB");
    }

    @Test
    void formatSizeShouldFormatZeroBytes() throws Exception {
        Method method = TurRagSearchToolService.class.getDeclaredMethod("formatSize", long.class);
        method.setAccessible(true);

        assertThat(method.invoke(null, 0L)).isEqualTo("0 B");
    }

    @Test
    void knowledgeBaseStatsShouldHandleNullContentType() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");

        TurAssetTrainingRecord trainingRecord = new TurAssetTrainingRecord();
        trainingRecord.setChunkCount(3);
        trainingRecord.setFileSize(1024L);
        trainingRecord.setContentType(null);

        when(trainingRecordRepository.findAll()).thenReturn(List.of(trainingRecord));

        String result = service.knowledgeBaseStats();
        assertThat(result)
                .contains("Total indexed files: 1")
                .contains("unknown");
    }

    @Test
    void knowledgeBaseStatsShouldHandleException() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(trainingRecordRepository.findAll()).thenThrow(new RuntimeException("DB error"));

        String result = service.knowledgeBaseStats();
        assertThat(result).contains("Error retrieving knowledge base stats");
    }

    @Test
    void listKnowledgeBaseFilesShouldReturnFormattedFiles() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");

        TurAssetTrainingRecord trainingRecord = new TurAssetTrainingRecord();
        trainingRecord.setFileName("report.pdf");
        trainingRecord.setObjectName("/docs/report.pdf");
        trainingRecord.setContentType("application/pdf");
        trainingRecord.setFileSize(2048L);
        trainingRecord.setChunkCount(10);
        trainingRecord.setTrainedAt(java.time.Instant.now());

        when(trainingRecordRepository.findAll()).thenReturn(List.of(trainingRecord));

        String result = service.listKnowledgeBaseFiles(null, null);
        assertThat(result)
                .contains("Indexed files (1)")
                .contains("report.pdf")
                .contains("/docs/report.pdf")
                .contains("application/pdf");
    }

    @Test
    void listKnowledgeBaseFilesShouldHandleException() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(trainingRecordRepository.findAll()).thenThrow(new RuntimeException("DB error"));

        String result = service.listKnowledgeBaseFiles(null, null);
        assertThat(result).contains("Error listing knowledge base files");
    }

    @Test
    void listKnowledgeBaseFilesShouldShowNoFilesWithKeywordFilter() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(trainingRecordRepository.findByFileNameContainingIgnoreCase("nothing"))
                .thenReturn(Collections.emptyList());

        String result = service.listKnowledgeBaseFiles("nothing", null);
        assertThat(result).contains("No indexed files found").contains("nothing");
    }

    @Test
    void listKnowledgeBaseFilesShouldShowNoFilesWithContentTypeFilter() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(trainingRecordRepository.findByContentType("image/png"))
                .thenReturn(Collections.emptyList());

        String result = service.listKnowledgeBaseFiles(null, "image/png");
        assertThat(result).contains("No indexed files found").contains("image/png");
    }

    @Test
    void listKnowledgeBaseFilesShouldShowNoFilesWithNoFilters() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(trainingRecordRepository.findAll()).thenReturn(Collections.emptyList());

        // Both null filters should show generic message without filter info
        String result = service.listKnowledgeBaseFiles(null, null);
        assertThat(result).contains("No indexed files found.");
    }

    @Test
    void searchKnowledgeBaseShouldReturnErrorWhenRagInfraNotConfigured() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(java.util.Optional.empty());

        String result = service.searchKnowledgeBase("test", 5, (org.springframework.ai.chat.model.ToolContext) null);
        assertThat(result).contains("Error searching knowledge base");
    }

    @Test
    void getFileFromKnowledgeBaseShouldReturnErrorWhenRagInfraNotConfigured() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(java.util.Optional.empty());

        String result = service.getFileFromKnowledgeBase("file.pdf", 10);
        assertThat(result).contains("Error retrieving file content");
    }

    @Test
    void searchKnowledgeBaseShouldClampMaxResults() {
        // maxResults null should default to 5, maxResults > 20 should clamp to 20
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(java.util.Optional.empty());

        // Tests with null maxResults
        String result1 = service.searchKnowledgeBase("test", null, (org.springframework.ai.chat.model.ToolContext) null);
        assertThat(result1).contains("Error searching knowledge base");

        // Tests with 0 maxResults
        String result2 = service.searchKnowledgeBase("test", 0, (org.springframework.ai.chat.model.ToolContext) null);
        assertThat(result2).contains("Error searching knowledge base");

        // Tests with negative maxResults
        String result3 = service.searchKnowledgeBase("test", -5, (org.springframework.ai.chat.model.ToolContext) null);
        assertThat(result3).contains("Error searching knowledge base");

        // Tests with large maxResults
        String result4 = service.searchKnowledgeBase("test", 100, (org.springframework.ai.chat.model.ToolContext) null);
        assertThat(result4).contains("Error searching knowledge base");
    }

    @Test
    void getFileFromKnowledgeBaseShouldClampMaxChunks() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(java.util.Optional.empty());

        // null maxChunks defaults to 10, 0 defaults to 10, >50 clamps to 50
        String result1 = service.getFileFromKnowledgeBase("file.pdf", null);
        assertThat(result1).contains("Error retrieving file content");

        String result2 = service.getFileFromKnowledgeBase("file.pdf", 0);
        assertThat(result2).contains("Error retrieving file content");

        String result3 = service.getFileFromKnowledgeBase("file.pdf", 100);
        assertThat(result3).contains("Error retrieving file content");
    }

    // --- Additional coverage: success paths with mocked VectorStore ---

    @Test
    void searchKnowledgeBaseShouldReturnNoDocumentsMessage() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");

        org.springframework.ai.vectorstore.VectorStore mockVectorStore =
                org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class);
        org.springframework.ai.embedding.EmbeddingModel mockEmbeddingModel =
                org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class);
        com.viglet.turing.genai.provider.store.TurGenAiStoreProvider mockStoreProvider =
                org.mockito.Mockito.mock(com.viglet.turing.genai.provider.store.TurGenAiStoreProvider.class);
        com.viglet.turing.persistence.model.store.TurStoreInstance mockStoreInstance =
                org.mockito.Mockito.mock(com.viglet.turing.persistence.model.store.TurStoreInstance.class);

        TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                mockVectorStore, mockEmbeddingModel, mockStoreProvider, mockStoreInstance, "cred", "collection");

        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(java.util.Optional.of(infra));
        when(mockVectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(
                org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(Collections.emptyList());

        String result = service.searchKnowledgeBase("non-existent query", 5, (org.springframework.ai.chat.model.ToolContext) null);
        assertThat(result).contains("No relevant documents found");
    }

    @Test
    void searchKnowledgeBaseShouldReturnFormattedResults() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");

        org.springframework.ai.vectorstore.VectorStore mockVectorStore =
                org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class);
        org.springframework.ai.embedding.EmbeddingModel mockEmbeddingModel =
                org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class);
        com.viglet.turing.genai.provider.store.TurGenAiStoreProvider mockStoreProvider =
                org.mockito.Mockito.mock(com.viglet.turing.genai.provider.store.TurGenAiStoreProvider.class);
        com.viglet.turing.persistence.model.store.TurStoreInstance mockStoreInstance =
                org.mockito.Mockito.mock(com.viglet.turing.persistence.model.store.TurStoreInstance.class);

        TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                mockVectorStore, mockEmbeddingModel, mockStoreProvider, mockStoreInstance, "cred", "collection");

        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(java.util.Optional.of(infra));

        org.springframework.ai.document.Document doc = new org.springframework.ai.document.Document(
                "This is the content of the document.",
                java.util.Map.of("objectName", "docs/report.pdf", "contentType", "application/pdf"));

        when(mockVectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(
                org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(doc));

        String result = service.searchKnowledgeBase("report", 5, (org.springframework.ai.chat.model.ToolContext) null);
        assertThat(result)
                .contains("Found 1 relevant document")
                .contains("docs/report.pdf")
                .contains("application/pdf")
                .contains("This is the content")
                .contains("REFERENCES");
    }

    @Test
    void searchKnowledgeBaseShouldHandleEmptyContentType() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");

        org.springframework.ai.vectorstore.VectorStore mockVectorStore =
                org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class);
        org.springframework.ai.embedding.EmbeddingModel mockEmbeddingModel =
                org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class);
        com.viglet.turing.genai.provider.store.TurGenAiStoreProvider mockStoreProvider =
                org.mockito.Mockito.mock(com.viglet.turing.genai.provider.store.TurGenAiStoreProvider.class);
        com.viglet.turing.persistence.model.store.TurStoreInstance mockStoreInstance =
                org.mockito.Mockito.mock(com.viglet.turing.persistence.model.store.TurStoreInstance.class);

        TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                mockVectorStore, mockEmbeddingModel, mockStoreProvider, mockStoreInstance, "cred", "collection");

        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(java.util.Optional.of(infra));

        org.springframework.ai.document.Document doc = new org.springframework.ai.document.Document(
                "Some text content.",
                java.util.Map.of("objectName", "data/file.txt", "contentType", ""));

        when(mockVectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(
                org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(doc));

        String result = service.searchKnowledgeBase("text", 5, (org.springframework.ai.chat.model.ToolContext) null);
        assertThat(result)
                .contains("Found 1 relevant document")
                .doesNotContain("Type:");
    }

    @Test
    void getFileFromKnowledgeBaseShouldReturnNoContentMessage() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");

        org.springframework.ai.vectorstore.VectorStore mockVectorStore =
                org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class);
        org.springframework.ai.embedding.EmbeddingModel mockEmbeddingModel =
                org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class);
        com.viglet.turing.genai.provider.store.TurGenAiStoreProvider mockStoreProvider =
                org.mockito.Mockito.mock(com.viglet.turing.genai.provider.store.TurGenAiStoreProvider.class);
        com.viglet.turing.persistence.model.store.TurStoreInstance mockStoreInstance =
                org.mockito.Mockito.mock(com.viglet.turing.persistence.model.store.TurStoreInstance.class);

        TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                mockVectorStore, mockEmbeddingModel, mockStoreProvider, mockStoreInstance, "cred", "collection");

        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(java.util.Optional.of(infra));

        // Return documents that don't match the file name
        org.springframework.ai.document.Document doc = new org.springframework.ai.document.Document(
                "Unrelated content.",
                java.util.Map.of("objectName", "other/doc.pdf", "fileName", "doc.pdf"));

        when(mockVectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(
                org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(doc));

        String result = service.getFileFromKnowledgeBase("report.pdf", 10);
        assertThat(result).contains("No content found for file: report.pdf");
    }

    @Test
    void getFileFromKnowledgeBaseShouldReturnMatchedContent() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");

        org.springframework.ai.vectorstore.VectorStore mockVectorStore =
                org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class);
        org.springframework.ai.embedding.EmbeddingModel mockEmbeddingModel =
                org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class);
        com.viglet.turing.genai.provider.store.TurGenAiStoreProvider mockStoreProvider =
                org.mockito.Mockito.mock(com.viglet.turing.genai.provider.store.TurGenAiStoreProvider.class);
        com.viglet.turing.persistence.model.store.TurStoreInstance mockStoreInstance =
                org.mockito.Mockito.mock(com.viglet.turing.persistence.model.store.TurStoreInstance.class);

        TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                mockVectorStore, mockEmbeddingModel, mockStoreProvider, mockStoreInstance, "cred", "collection");

        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(java.util.Optional.of(infra));

        org.springframework.ai.document.Document doc1 = new org.springframework.ai.document.Document(
                "Chapter 1 content here.",
                java.util.Map.of("objectName", "docs/report.pdf", "fileName", "report.pdf"));
        org.springframework.ai.document.Document doc2 = new org.springframework.ai.document.Document(
                "Chapter 2 content here.",
                java.util.Map.of("objectName", "docs/report.pdf", "fileName", "report.pdf"));

        when(mockVectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(
                org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(doc1, doc2));

        String result = service.getFileFromKnowledgeBase("report.pdf", 10);
        assertThat(result)
                .contains("Content from: docs/report.pdf")
                .contains("Showing 2 chunk(s)")
                .contains("Chapter 1 content")
                .contains("Chapter 2 content");
    }

    @Test
    void searchKnowledgeBaseShouldDeduplicateFileLinks() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");

        org.springframework.ai.vectorstore.VectorStore mockVectorStore =
                org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class);
        org.springframework.ai.embedding.EmbeddingModel mockEmbeddingModel =
                org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class);
        com.viglet.turing.genai.provider.store.TurGenAiStoreProvider mockStoreProvider =
                org.mockito.Mockito.mock(com.viglet.turing.genai.provider.store.TurGenAiStoreProvider.class);
        com.viglet.turing.persistence.model.store.TurStoreInstance mockStoreInstance =
                org.mockito.Mockito.mock(com.viglet.turing.persistence.model.store.TurStoreInstance.class);

        TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                mockVectorStore, mockEmbeddingModel, mockStoreProvider, mockStoreInstance, "cred", "collection");

        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(java.util.Optional.of(infra));

        // Two documents from the same file
        org.springframework.ai.document.Document doc1 = new org.springframework.ai.document.Document(
                "First chunk.",
                java.util.Map.of("objectName", "docs/report.pdf", "contentType", "application/pdf"));
        org.springframework.ai.document.Document doc2 = new org.springframework.ai.document.Document(
                "Second chunk.",
                java.util.Map.of("objectName", "docs/report.pdf", "contentType", "application/pdf"));

        when(mockVectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(
                org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(doc1, doc2));

        String result = service.searchKnowledgeBase("report", 10, (org.springframework.ai.chat.model.ToolContext) null);
        assertThat(result)
                .contains("Found 2 relevant document")
                .contains("REFERENCES");
        // Only one reference link despite two documents from same file
        int refCount = result.split("\\- \\[docs/report\\.pdf\\]").length - 1;
        assertThat(refCount).isEqualTo(1);
    }

    @Test
    void listKnowledgeBaseFilesShouldHandleExceptionFromKeywordSearch() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(trainingRecordRepository.findByFileNameContainingIgnoreCase("fail"))
                .thenThrow(new RuntimeException("DB error"));

        String result = service.listKnowledgeBaseFiles("fail", null);
        assertThat(result).contains("Error listing knowledge base files");
    }
}

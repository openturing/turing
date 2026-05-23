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
    private com.viglet.turing.persistence.repository.sn.genai.TurSNSiteGenAiRepository snSiteGenAiRepository;
    @Mock
    private com.viglet.turing.persistence.repository.rag.TurRagBm25CoreRepository ragBm25CoreRepository;
    @Mock
    private com.viglet.turing.plugins.se.TurSearchEnginePluginFactory searchEnginePluginFactory;

    private TurRagSearchToolService service;

    @BeforeEach
    void setUp() {
        service = new TurRagSearchToolService(globalSettingsService, ragContextBuilder, trainingRecordRepository,
                snSiteGenAiRepository, ragBm25CoreRepository, searchEnginePluginFactory);
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

    // --- T24b §III.2: SE_INSTANCE hybrid retrieval path ---
    //
    // The flag combo {ragBm25Fallback=true, ragHybridSearch=true,
    // ragBm25Source=SE_INSTANCE, ragSeInstance!=null} must:
    //   (a) lookup TurRagBm25Core by (storeInstanceId, locale from ToolContext)
    //   (b) run vector + plugin.retrieveStandalone in parallel
    //   (c) fuse via RRF (verified via TurRagRrfTest; here we assert
    //       the routing + degradation contract).

    @Test
    void seHybridShouldDegradeToVectorOnlyWhenNoProvisionedCore() {
        // Setup: agent flags say SE_INSTANCE, but the (store, locale) core
        // is missing from the repository → vector-only fallback path.
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");

        var mockVectorStore = org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class);
        var mockStoreInstance = org.mockito.Mockito.mock(com.viglet.turing.persistence.model.store.TurStoreInstance.class);
        when(mockStoreInstance.getId()).thenReturn("store-1");
        TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                mockVectorStore,
                org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class),
                org.mockito.Mockito.mock(com.viglet.turing.genai.provider.store.TurGenAiStoreProvider.class),
                mockStoreInstance, "cred", "collection");
        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(java.util.Optional.of(infra));

        // Binding with SE_INSTANCE source pointing at an SE instance, no core
        // provisioned yet for the requested locale.
        var binding = new com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi();
        binding.setRagBm25Fallback(true);
        binding.setRagHybridSearch(true);
        binding.setRagBm25Source(com.viglet.turing.persistence.model.rag.TurRagBm25Source.SE_INSTANCE);
        var seInstance = org.mockito.Mockito.mock(com.viglet.turing.persistence.model.se.TurSEInstance.class);
        binding.setRagSeInstance(seInstance);
        when(snSiteGenAiRepository.findByTurAIAgent_Id("agent-1")).thenReturn(List.of(binding));

        when(ragBm25CoreRepository.findByTurStoreInstance_IdAndLocale(
                "store-1", java.util.Locale.forLanguageTag("pt-BR")))
                .thenReturn(java.util.Optional.empty());

        // Vector-only fallback returns one doc.
        var doc = new org.springframework.ai.document.Document("Some text",
                java.util.Map.of("objectName", "doc.pdf", "contentType", "application/pdf"));
        when(mockVectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(
                org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(doc));

        var toolContext = new org.springframework.ai.chat.model.ToolContext(java.util.Map.of(
                TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID, "agent-1",
                TurCustomToolCallbackService.TOOL_CONTEXT_LOCALE, "pt-BR"));

        String result = service.searchKnowledgeBase("query", 5, toolContext);

        assertThat(result)
                .contains("Found 1 relevant document")
                .contains("doc.pdf");
        // No SE plugin lookup should be attempted because the core was missing.
        org.mockito.Mockito.verifyNoInteractions(searchEnginePluginFactory);
    }

    @Test
    void seHybridShouldDegradeWhenCoreIsNotProvisioned() {
        // Core row exists but its lifecycle status is not PROVISIONED yet
        // (provisioning in progress, errored, or pending cleanup) — same
        // degradation contract as missing-row.
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");

        var mockVectorStore = org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class);
        var mockStoreInstance = org.mockito.Mockito.mock(com.viglet.turing.persistence.model.store.TurStoreInstance.class);
        when(mockStoreInstance.getId()).thenReturn("store-1");
        TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                mockVectorStore,
                org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class),
                org.mockito.Mockito.mock(com.viglet.turing.genai.provider.store.TurGenAiStoreProvider.class),
                mockStoreInstance, "cred", "collection");
        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(java.util.Optional.of(infra));

        var binding = new com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi();
        binding.setRagBm25Fallback(true);
        binding.setRagHybridSearch(true);
        binding.setRagBm25Source(com.viglet.turing.persistence.model.rag.TurRagBm25Source.SE_INSTANCE);
        binding.setRagSeInstance(org.mockito.Mockito.mock(com.viglet.turing.persistence.model.se.TurSEInstance.class));
        when(snSiteGenAiRepository.findByTurAIAgent_Id("agent-1")).thenReturn(List.of(binding));

        var core = new com.viglet.turing.persistence.model.rag.TurRagBm25Core();
        core.setStatus(com.viglet.turing.persistence.model.rag.TurRagBm25Core.Status.PROVISIONING);
        when(ragBm25CoreRepository.findByTurStoreInstance_IdAndLocale(
                "store-1", java.util.Locale.forLanguageTag("en-US")))
                .thenReturn(java.util.Optional.of(core));

        when(mockVectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(
                org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(Collections.emptyList());

        var toolContext = new org.springframework.ai.chat.model.ToolContext(java.util.Map.of(
                TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID, "agent-1",
                TurCustomToolCallbackService.TOOL_CONTEXT_LOCALE, "en-US"));

        String result = service.searchKnowledgeBase("query", 5, toolContext);

        assertThat(result).contains("No relevant documents found");
        org.mockito.Mockito.verifyNoInteractions(searchEnginePluginFactory);
    }

    @Test
    void seHybridShouldFuseVectorAndBm25HitsViaRrf() {
        // Happy path: PROVISIONED core, plugin returns BM25 hits, RRF
        // fuses both lists. We assert the BM25-only doc gets the
        // keyword-only tag and the response includes both docs.
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");

        var mockVectorStore = org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class);
        var mockStoreInstance = org.mockito.Mockito.mock(com.viglet.turing.persistence.model.store.TurStoreInstance.class);
        when(mockStoreInstance.getId()).thenReturn("store-1");
        TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                mockVectorStore,
                org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class),
                org.mockito.Mockito.mock(com.viglet.turing.genai.provider.store.TurGenAiStoreProvider.class),
                mockStoreInstance, "cred", "collection");
        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(java.util.Optional.of(infra));

        var seInstance = org.mockito.Mockito.mock(com.viglet.turing.persistence.model.se.TurSEInstance.class);
        var binding = new com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi();
        binding.setRagBm25Fallback(true);
        binding.setRagHybridSearch(true);
        binding.setRagBm25Source(com.viglet.turing.persistence.model.rag.TurRagBm25Source.SE_INSTANCE);
        binding.setRagSeInstance(seInstance);
        when(snSiteGenAiRepository.findByTurAIAgent_Id("agent-1")).thenReturn(List.of(binding));

        var core = new com.viglet.turing.persistence.model.rag.TurRagBm25Core();
        core.setStatus(com.viglet.turing.persistence.model.rag.TurRagBm25Core.Status.PROVISIONED);
        core.setCoreName("rag_store_pt-BR");
        core.setTurSEInstance(seInstance);
        when(ragBm25CoreRepository.findByTurStoreInstance_IdAndLocale(
                "store-1", java.util.Locale.forLanguageTag("pt-BR")))
                .thenReturn(java.util.Optional.of(core));

        // Vector hits: one doc that ALSO appears in BM25 (id=vec-shared).
        var vecDoc = org.springframework.ai.document.Document.builder()
                .id("vec-shared")
                .text("Vector text")
                .metadata(java.util.Map.of("objectName", "shared.pdf", "contentType", "application/pdf"))
                .score(0.8)
                .build();
        when(mockVectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(
                org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(vecDoc));

        // BM25 hits: the shared doc + one BM25-exclusive doc.
        var plugin = org.mockito.Mockito.mock(com.viglet.turing.plugins.se.TurSearchEnginePlugin.class);
        when(searchEnginePluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.retrieveStandalone(seInstance, "rag_store_pt-BR", "query", 5)).thenReturn(List.of(
                new com.viglet.turing.plugins.se.TurSEStandaloneHit("vec-shared", "BM25 text",
                        java.util.Map.of("objectName", "shared.pdf", "contentType", "application/pdf"), 5.0),
                new com.viglet.turing.plugins.se.TurSEStandaloneHit("bm-only", "Keyword-only text",
                        java.util.Map.of("objectName", "kw.txt", "contentType", "text/plain"), 3.0)));

        var toolContext = new org.springframework.ai.chat.model.ToolContext(java.util.Map.of(
                TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID, "agent-1",
                TurCustomToolCallbackService.TOOL_CONTEXT_LOCALE, "pt-BR"));

        String result = service.searchKnowledgeBase("query", 5, toolContext);

        // Both files appear in the output; only the BM25-exclusive one
        // triggers the keyword-only warning header (since the shared doc
        // was vector-confirmed → tag stripped by RRF).
        assertThat(result)
                .contains("shared.pdf")
                .contains("kw.txt")
                .doesNotContain("KEYWORD-ONLY MATCH"); // not allKeywordOnly — vector confirmed shared
    }

    @Test
    void seHybridShouldDegradeWhenPluginThrows() {
        // SE plugin raises (network down, core gone) — degrade to vector-only.
        // The contract: tool MUST NOT bubble the exception to the LLM.
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");

        var mockVectorStore = org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class);
        var mockStoreInstance = org.mockito.Mockito.mock(com.viglet.turing.persistence.model.store.TurStoreInstance.class);
        when(mockStoreInstance.getId()).thenReturn("store-1");
        TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                mockVectorStore,
                org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class),
                org.mockito.Mockito.mock(com.viglet.turing.genai.provider.store.TurGenAiStoreProvider.class),
                mockStoreInstance, "cred", "collection");
        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(java.util.Optional.of(infra));

        var seInstance = org.mockito.Mockito.mock(com.viglet.turing.persistence.model.se.TurSEInstance.class);
        var binding = new com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi();
        binding.setRagBm25Fallback(true);
        binding.setRagHybridSearch(true);
        binding.setRagBm25Source(com.viglet.turing.persistence.model.rag.TurRagBm25Source.SE_INSTANCE);
        binding.setRagSeInstance(seInstance);
        when(snSiteGenAiRepository.findByTurAIAgent_Id("agent-1")).thenReturn(List.of(binding));

        var core = new com.viglet.turing.persistence.model.rag.TurRagBm25Core();
        core.setStatus(com.viglet.turing.persistence.model.rag.TurRagBm25Core.Status.PROVISIONED);
        core.setCoreName("rag_store_pt-BR");
        core.setTurSEInstance(seInstance);
        when(ragBm25CoreRepository.findByTurStoreInstance_IdAndLocale(
                "store-1", java.util.Locale.forLanguageTag("pt-BR")))
                .thenReturn(java.util.Optional.of(core));

        var doc = org.springframework.ai.document.Document.builder()
                .id("vec-1")
                .text("Vector text")
                .metadata(java.util.Map.of("objectName", "vec.pdf", "contentType", "application/pdf"))
                .score(0.9)
                .build();
        when(mockVectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(
                org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(doc));

        var plugin = org.mockito.Mockito.mock(com.viglet.turing.plugins.se.TurSearchEnginePlugin.class);
        when(searchEnginePluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.retrieveStandalone(seInstance, "rag_store_pt-BR", "query", 5))
                .thenThrow(new RuntimeException("Connection refused"));

        var toolContext = new org.springframework.ai.chat.model.ToolContext(java.util.Map.of(
                TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID, "agent-1",
                TurCustomToolCallbackService.TOOL_CONTEXT_LOCALE, "pt-BR"));

        String result = service.searchKnowledgeBase("query", 5, toolContext);

        // Vector-only fallback fused via RRF with empty BM25 list — the
        // vector doc still surfaces.
        assertThat(result)
                .contains("vec.pdf");
    }

    @Test
    void seInstanceFlagButNoSeInstanceShouldFallToEmbeddedPath() {
        // Defensive: source=SE_INSTANCE but ragSeInstance is null (admin
        // toggled the flag without binding an SE). The tool MUST NOT
        // attempt the SE path — it falls through to the embedded /
        // vector-only path. We assert no plugin factory call happens.
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");

        var mockVectorStore = org.mockito.Mockito.mock(org.springframework.ai.vectorstore.VectorStore.class);
        var mockStoreInstance = org.mockito.Mockito.mock(com.viglet.turing.persistence.model.store.TurStoreInstance.class);
        // Note: mockStoreInstance.getId() is NOT stubbed — the defensive
        // check (seInstance == null) short-circuits before any storeId
        // resolution, so the call shouldn't happen at all.
        TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                mockVectorStore,
                org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class),
                org.mockito.Mockito.mock(com.viglet.turing.genai.provider.store.TurGenAiStoreProvider.class),
                mockStoreInstance, "cred", "collection");
        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(java.util.Optional.of(infra));

        var binding = new com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi();
        binding.setRagBm25Fallback(true);
        binding.setRagHybridSearch(true);
        binding.setRagBm25Source(com.viglet.turing.persistence.model.rag.TurRagBm25Source.SE_INSTANCE);
        binding.setRagSeInstance(null); // <- the misconfiguration
        when(snSiteGenAiRepository.findByTurAIAgent_Id("agent-1")).thenReturn(List.of(binding));

        // Vector pass returns one doc; tool falls back through the legacy
        // vector path (vectorStore is not a TurLuceneVectorStore mock so
        // useEmbeddedHybrid is also false).
        when(mockVectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(
                org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(Collections.emptyList());

        var toolContext = new org.springframework.ai.chat.model.ToolContext(java.util.Map.of(
                TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID, "agent-1",
                TurCustomToolCallbackService.TOOL_CONTEXT_LOCALE, "pt-BR"));

        service.searchKnowledgeBase("query", 5, toolContext);

        // The defensive flag check means we never even look up the core
        // for this misconfigured binding.
        org.mockito.Mockito.verifyNoInteractions(searchEnginePluginFactory, ragBm25CoreRepository);
    }

    @Test
    void resolveLocaleShouldFallBackToRootWhenContextIsNull() throws Exception {
        java.lang.reflect.Method m = TurRagSearchToolService.class.getDeclaredMethod(
                "resolveLocale", org.springframework.ai.chat.model.ToolContext.class);
        m.setAccessible(true);
        assertThat(m.invoke(null, (Object) null)).isEqualTo(java.util.Locale.ROOT);
    }

    @Test
    void resolveLocaleShouldFallBackToRootWhenTagIsBlank() throws Exception {
        java.lang.reflect.Method m = TurRagSearchToolService.class.getDeclaredMethod(
                "resolveLocale", org.springframework.ai.chat.model.ToolContext.class);
        m.setAccessible(true);
        var ctx = new org.springframework.ai.chat.model.ToolContext(java.util.Map.of(
                TurCustomToolCallbackService.TOOL_CONTEXT_LOCALE, ""));
        assertThat(m.invoke(null, ctx)).isEqualTo(java.util.Locale.ROOT);
    }

    @Test
    void resolveLocaleShouldParseBcp47Tag() throws Exception {
        java.lang.reflect.Method m = TurRagSearchToolService.class.getDeclaredMethod(
                "resolveLocale", org.springframework.ai.chat.model.ToolContext.class);
        m.setAccessible(true);
        var ctx = new org.springframework.ai.chat.model.ToolContext(java.util.Map.of(
                TurCustomToolCallbackService.TOOL_CONTEXT_LOCALE, "pt-BR"));
        assertThat(m.invoke(null, ctx)).isEqualTo(java.util.Locale.forLanguageTag("pt-BR"));
    }
}

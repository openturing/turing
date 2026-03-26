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

    private TurRagSearchToolService service;

    @BeforeEach
    void setUp() {
        service = new TurRagSearchToolService(globalSettingsService, ragContextBuilder, trainingRecordRepository);
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
        String result = service.searchKnowledgeBase("test query", 5);
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
}

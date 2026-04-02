package com.viglet.turing.service.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.genai.TurRagContextBuilder.RagInfrastructure;
import com.viglet.turing.persistence.model.asset.TurAssetTrainingRecord;
import com.viglet.turing.persistence.repository.asset.TurAssetTrainingRecordRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * Tests for TurAssetTrainingService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurAssetTrainingServiceTest {

        @Mock
        private com.viglet.turing.service.storage.TurStorageService storageService;

        @Mock
        private TurGlobalSettingsService globalSettingsService;

        @Mock
        private TurRagContextBuilder ragContextBuilder;

        @Mock
        private TurAssetTrainingRecordRepository trainingRecordRepository;

        @Mock
        private RagInfrastructure ragInfrastructure;

        private TurAssetTrainingService service;

        @BeforeEach
        void setUp() {
                service = new TurAssetTrainingService(
                                storageService,
                                globalSettingsService,
                                ragContextBuilder,
                                trainingRecordRepository);
        }

        @Test
        void getStatusReturnsIdleByDefault() {
                TurAssetTrainingStatus status = service.getStatus();

                assertThat(status).isNotNull();
                assertThat(status.state()).isEqualTo(TurAssetTrainingState.IDLE);
                assertThat(status.totalCount()).isZero();
                assertThat(status.processedCount()).isZero();
                assertThat(status.errorCount()).isZero();
                assertThat(status.startedAt()).isEmpty();
                assertThat(status.completedAt()).isEmpty();
                assertThat(status.errorMessage()).isEmpty();
        }

        @Test
        void getTrainedAtMapReturnsEmptyMapWhenNoRecords() {
                when(trainingRecordRepository.findByObjectNameIn(any()))
                                .thenReturn(Collections.emptyList());

                var result = service.getTrainedAtMap(List.of("file1.pdf", "file2.txt"));

                assertThat(result).isEmpty();
        }

        @Test
        void getTrainedAtMapReturnsCorrectMapping() {
                TurAssetTrainingRecord record1 = new TurAssetTrainingRecord();
                record1.setObjectName("file1.pdf");
                record1.setTrainedAt(Instant.parse("2026-03-01T10:00:00Z"));

                TurAssetTrainingRecord record2 = new TurAssetTrainingRecord();
                record2.setObjectName("file2.txt");
                record2.setTrainedAt(Instant.parse("2026-03-02T15:30:00Z"));

                when(trainingRecordRepository.findByObjectNameIn(List.of("file1.pdf", "file2.txt")))
                                .thenReturn(List.of(record1, record2));

                var result = service.getTrainedAtMap(List.of("file1.pdf", "file2.txt"));

                assertThat(result).hasSize(2)
                        .containsKey("file2.txt")
                        .containsEntry("file1.pdf", "2026-03-01T10:00:00Z");
        }

        @Test
        void getTrainedAtMapWithEmptyInputList() {
                when(trainingRecordRepository.findByObjectNameIn(Collections.emptyList()))
                                .thenReturn(Collections.emptyList());

                var result = service.getTrainedAtMap(Collections.emptyList());

                assertThat(result).isEmpty();
        }

        @Test
        void getTrainedAtMapPartialMatch() {
                TurAssetTrainingRecord trainingRecord = new TurAssetTrainingRecord();
                trainingRecord.setObjectName("trained.pdf");
                trainingRecord.setTrainedAt(Instant.parse("2026-01-15T08:00:00Z"));

                when(trainingRecordRepository.findByObjectNameIn(List.of("trained.pdf", "untrained.pdf")))
                                .thenReturn(List.of(trainingRecord));

                var result = service.getTrainedAtMap(List.of("trained.pdf", "untrained.pdf"));

                assertThat(result).hasSize(1);
                assertThat(result).containsKey("trained.pdf");
                assertThat(result).doesNotContainKey("untrained.pdf");
        }

        @Test
        void indexSingleFileSkipsWhenRagNotConfigured() {
                when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(Optional.empty());

                service.indexSingleFile("test.pdf", "application/pdf", 1024);

                verify(trainingRecordRepository, never()).save(any());
        }

        @Test
        void deindexSingleFileSkipsWhenRagNotConfigured() {
                when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(Optional.empty());

                service.deindexSingleFile("test.pdf");

                verify(trainingRecordRepository, never()).deleteById(anyString());
        }

        @Test
        void startTrainingReturnsFailedWhenRagNotEnabled() {
                when(globalSettingsService.isRagEnabled()).thenReturn(false);

                TurAssetTrainingStatus status = service.startTraining();

                assertThat(status.state()).isEqualTo(TurAssetTrainingState.FAILED);
                assertThat(status.errorMessage()).contains("RAG is not enabled");
        }

        @Test
        void startTrainingReturnsRunningWhenAlreadyRunning() throws Exception {
                CountDownLatch holdTraining = new CountDownLatch(1);
                when(globalSettingsService.isRagEnabled()).thenReturn(true);
                when(ragContextBuilder.buildFromGlobalSettings()).thenAnswer(inv -> {
                        holdTraining.await(5, TimeUnit.SECONDS);
                        return Optional.of(ragInfrastructure);
                });

                TurAssetTrainingStatus firstCall = service.startTraining();
                assertThat(firstCall.state()).isEqualTo(TurAssetTrainingState.RUNNING);

                TurAssetTrainingStatus secondCall = service.startTraining();
                assertThat(secondCall.state()).isEqualTo(TurAssetTrainingState.RUNNING);

                holdTraining.countDown();
        }

        @Test
        void startTrainingReturnsRunningStatus() {
                when(globalSettingsService.isRagEnabled()).thenReturn(true);
                when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(Optional.of(ragInfrastructure));
                when(storageService.listAllObjects()).thenReturn(Collections.emptyList());

                TurAssetTrainingStatus status = service.startTraining();

                // The returned status is captured at call time; with an empty file list
                // the background executor may complete before this assertion runs.
                assertThat(status.state()).isIn(TurAssetTrainingState.RUNNING, TurAssetTrainingState.COMPLETED);
                assertThat(status.startedAt()).isNotEmpty();
        }

        @Test
        void turAssetTrainingStatusIdleFactory() {
                TurAssetTrainingStatus idle = TurAssetTrainingStatus.idle();

                assertThat(idle.state()).isEqualTo(TurAssetTrainingState.IDLE);
                assertThat(idle.totalCount()).isZero();
                assertThat(idle.processedCount()).isZero();
                assertThat(idle.errorCount()).isZero();
                assertThat(idle.startedAt()).isEmpty();
                assertThat(idle.completedAt()).isEmpty();
                assertThat(idle.errorMessage()).isEmpty();
        }

        @Test
        void turAssetTrainingStateEnumValues() {
                TurAssetTrainingState[] values = TurAssetTrainingState.values();

                assertThat(values).hasSize(4);
                assertThat(values).containsExactly(
                                TurAssetTrainingState.IDLE,
                                TurAssetTrainingState.RUNNING,
                                TurAssetTrainingState.COMPLETED,
                                TurAssetTrainingState.FAILED);
        }

        @Test
        void turAssetTrainingStatusRecordEquality() {
                TurAssetTrainingStatus s1 = new TurAssetTrainingStatus(
                                TurAssetTrainingState.COMPLETED, 10, 10, 0, "t1", "t2", "");
                TurAssetTrainingStatus s2 = new TurAssetTrainingStatus(
                                TurAssetTrainingState.COMPLETED, 10, 10, 0, "t1", "t2", "");

                assertThat(s1).isEqualTo(s2)
                        .hasSameHashCodeAs(s2);
        }

        @Test
        void turAssetTrainingStatusRecordInequality() {
                TurAssetTrainingStatus s1 = new TurAssetTrainingStatus(
                                TurAssetTrainingState.COMPLETED, 10, 10, 0, "t1", "t2", "");
                TurAssetTrainingStatus s2 = new TurAssetTrainingStatus(
                                TurAssetTrainingState.FAILED, 10, 10, 0, "t1", "t2", "error");

                assertThat(s1).isNotEqualTo(s2);
        }

        @Test
        void turAssetTrainingStatusAccessors() {
                TurAssetTrainingStatus status = new TurAssetTrainingStatus(
                                TurAssetTrainingState.RUNNING, 50, 25, 3, "2026-01-01", "2026-01-02", "some error");

                assertThat(status.state()).isEqualTo(TurAssetTrainingState.RUNNING);
                assertThat(status.totalCount()).isEqualTo(50);
                assertThat(status.processedCount()).isEqualTo(25);
                assertThat(status.errorCount()).isEqualTo(3);
                assertThat(status.startedAt()).isEqualTo("2026-01-01");
                assertThat(status.completedAt()).isEqualTo("2026-01-02");
                assertThat(status.errorMessage()).isEqualTo("some error");
        }

        @Test
        void deindexSingleFileDoesNotThrowOnException() {
                when(ragContextBuilder.buildFromGlobalSettings())
                                .thenThrow(new RuntimeException("Unexpected error"));

                assertDoesNotThrow(() -> service.deindexSingleFile("bad-file.pdf"));

                // Should not propagate the exception
        }

        @Test
        void indexSingleFileDoesNotThrowOnException() {
                when(ragContextBuilder.buildFromGlobalSettings())
                                .thenThrow(new RuntimeException("Unexpected error"));

                assertDoesNotThrow(() -> service.indexSingleFile("bad-file.pdf", "application/pdf", 1024));

                // Should not propagate the exception
        }

        // --- Additional coverage: deindexSingleFile success path ---

        @Test
        void deindexSingleFileCallsDeleteWhenRagConfigured() {
                com.viglet.turing.genai.provider.store.TurGenAiStoreProvider mockStoreProvider = org.mockito.Mockito
                                .mock(com.viglet.turing.genai.provider.store.TurGenAiStoreProvider.class);
                com.viglet.turing.persistence.model.store.TurStoreInstance mockStoreInstance = org.mockito.Mockito
                                .mock(com.viglet.turing.persistence.model.store.TurStoreInstance.class);
                org.springframework.ai.vectorstore.VectorStore mockVectorStore = org.mockito.Mockito
                                .mock(org.springframework.ai.vectorstore.VectorStore.class);
                org.springframework.ai.embedding.EmbeddingModel mockEmbeddingModel = org.mockito.Mockito
                                .mock(org.springframework.ai.embedding.EmbeddingModel.class);

                TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                                mockVectorStore, mockEmbeddingModel, mockStoreProvider, mockStoreInstance, "cred",
                                "collection");

                when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(Optional.of(infra));

                service.deindexSingleFile("docs/report.pdf");

                verify(mockStoreProvider).deleteByMetadata(mockStoreInstance, "cred",
                                "collection", "objectName", "docs/report.pdf");
                verify(trainingRecordRepository).deleteById("docs/report.pdf");
        }

        // --- startTraining edge cases ---

        @Test
        void startTrainingReturnsFailedStatusWithMessage() {
                when(globalSettingsService.isRagEnabled()).thenReturn(false);

                TurAssetTrainingStatus status = service.startTraining();

                assertThat(status.state()).isEqualTo(TurAssetTrainingState.FAILED);
                assertThat(status.errorMessage()).isEqualTo("RAG is not enabled in Global Settings.");
                assertThat(status.totalCount()).isZero();
                assertThat(status.processedCount()).isZero();
                assertThat(status.errorCount()).isZero();
        }

        // --- getTrainedAtMap with single record ---

        @Test
        void getTrainedAtMapWithSingleRecord() {
                TurAssetTrainingRecord trainingRecord = new TurAssetTrainingRecord();
                trainingRecord.setObjectName("single.pdf");
                trainingRecord.setTrainedAt(java.time.Instant.parse("2026-03-15T12:00:00Z"));

                when(trainingRecordRepository.findByObjectNameIn(List.of("single.pdf")))
                                .thenReturn(List.of(trainingRecord));

                var result = service.getTrainedAtMap(List.of("single.pdf"));

                assertThat(result).hasSize(1)
                        .containsEntry("single.pdf", "2026-03-15T12:00:00Z");
        }

        // --- TurAssetTrainingStatus toString ---

        @Test
        void turAssetTrainingStatusToString() {
                TurAssetTrainingStatus status = new TurAssetTrainingStatus(
                                TurAssetTrainingState.COMPLETED, 10, 10, 0, "t1", "t2", "");

                String str = status.toString();
                assertThat(str).contains("COMPLETED");
        }

        // --- deindexSingleFile when storeProvider throws ---

        @Test
        void deindexSingleFileHandlesStoreProviderException() {
                com.viglet.turing.genai.provider.store.TurGenAiStoreProvider mockStoreProvider = org.mockito.Mockito
                                .mock(com.viglet.turing.genai.provider.store.TurGenAiStoreProvider.class);
                com.viglet.turing.persistence.model.store.TurStoreInstance mockStoreInstance = org.mockito.Mockito
                                .mock(com.viglet.turing.persistence.model.store.TurStoreInstance.class);
                org.springframework.ai.vectorstore.VectorStore mockVectorStore = org.mockito.Mockito
                                .mock(org.springframework.ai.vectorstore.VectorStore.class);
                org.springframework.ai.embedding.EmbeddingModel mockEmbeddingModel = org.mockito.Mockito
                                .mock(org.springframework.ai.embedding.EmbeddingModel.class);

                TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                                mockVectorStore, mockEmbeddingModel, mockStoreProvider, mockStoreInstance, "cred",
                                "collection");

                when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(Optional.of(infra));
                org.mockito.Mockito.doThrow(new RuntimeException("Store error")).when(mockStoreProvider)
                                .deleteByMetadata(any(), anyString(), anyString(), anyString(), anyString());

                // Should not throw
                assertDoesNotThrow(() -> service.deindexSingleFile("problem-file.pdf"));
        }

        // --- getStatus after startTraining ---

        @Test
        void getStatusReflectsRunningAfterStartTraining() {
                when(globalSettingsService.isRagEnabled()).thenReturn(true);
                when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(Optional.of(ragInfrastructure));
                when(storageService.listAllObjects()).thenReturn(Collections.emptyList());

                service.startTraining();
                TurAssetTrainingStatus status = service.getStatus();

                assertThat(status.state()).isIn(TurAssetTrainingState.RUNNING, TurAssetTrainingState.COMPLETED);
        }
}

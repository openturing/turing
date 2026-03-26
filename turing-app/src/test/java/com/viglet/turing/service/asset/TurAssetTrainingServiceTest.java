package com.viglet.turing.service.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
    private TurMinioService minioService;

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
                minioService,
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

        assertThat(result).hasSize(2);
        assertThat(result).containsKey("file1.pdf");
        assertThat(result).containsKey("file2.txt");
        assertThat(result.get("file1.pdf")).isEqualTo("2026-03-01T10:00:00Z");
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
        TurAssetTrainingRecord record = new TurAssetTrainingRecord();
        record.setObjectName("trained.pdf");
        record.setTrainedAt(Instant.parse("2026-01-15T08:00:00Z"));

        when(trainingRecordRepository.findByObjectNameIn(List.of("trained.pdf", "untrained.pdf")))
                .thenReturn(List.of(record));

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
    void startTrainingReturnsRunningWhenAlreadyRunning() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(Optional.of(ragInfrastructure));
        when(minioService.listAllObjects()).thenReturn(Collections.emptyList());

        TurAssetTrainingStatus firstCall = service.startTraining();
        assertThat(firstCall.state()).isEqualTo(TurAssetTrainingState.RUNNING);

        TurAssetTrainingStatus secondCall = service.startTraining();
        assertThat(secondCall.state()).isEqualTo(TurAssetTrainingState.RUNNING);
    }

    @Test
    void startTrainingReturnsRunningStatus() {
        when(globalSettingsService.isRagEnabled()).thenReturn(true);
        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(Optional.of(ragInfrastructure));
        when(minioService.listAllObjects()).thenReturn(Collections.emptyList());

        TurAssetTrainingStatus status = service.startTraining();

        assertThat(status.state()).isEqualTo(TurAssetTrainingState.RUNNING);
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

        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
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

        service.deindexSingleFile("bad-file.pdf");

        // Should not propagate the exception
    }

    @Test
    void indexSingleFileDoesNotThrowOnException() {
        when(ragContextBuilder.buildFromGlobalSettings())
                .thenThrow(new RuntimeException("Unexpected error"));

        service.indexSingleFile("bad-file.pdf", "application/pdf", 1024);

        // Should not propagate the exception
    }
}

package com.viglet.turing.lucene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.service.storage.TurStorageType;

/**
 * Unit tests for {@link TurLuceneStorageSync}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@ExtendWith(MockitoExtension.class)
class TurLuceneStorageSyncTest {

    @Mock
    private TurStorageService storageService;

    private TurLuceneStorageSync sync;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        sync = new TurLuceneStorageSync(storageService);
    }

    // ---- isEnabled ----

    @Test
    void isEnabledReturnsFalseWhenStorageDisabled() {
        when(storageService.isEnabled()).thenReturn(false);
        assertThat(sync.isEnabled()).isFalse();
    }

    @Test
    void isEnabledReturnsFalseWhenTypeIsNone() {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getType()).thenReturn(TurStorageType.NONE);
        assertThat(sync.isEnabled()).isFalse();
    }

    @Test
    void isEnabledReturnsTrueForMinio() {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getType()).thenReturn(TurStorageType.MINIO);
        assertThat(sync.isEnabled()).isTrue();
    }

    // ---- syncToStorage ----

    @Test
    void syncToStorageUploadsNewFiles() throws IOException {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getType()).thenReturn(TurStorageType.MINIO);
        when(storageService.listObjects(anyString())).thenReturn(List.of());

        // Create local segment files
        Files.writeString(tempDir.resolve("_0.cfs"), "segment-data");
        Files.writeString(tempDir.resolve("segments_1"), "commit-point");

        sync.syncToStorage(tempDir, "test-core");

        // Should upload _0.cfs first, then segments_1 last
        verify(storageService, times(2)).uploadStream(anyString(), any(), anyLong(), eq("application/octet-stream"));
        verify(storageService).uploadStream(eq("lucene-indexes/test-core/_0.cfs"), any(), anyLong(), anyString());
        verify(storageService).uploadStream(eq("lucene-indexes/test-core/segments_1"), any(), anyLong(), anyString());
    }

    @Test
    void syncToStorageSkipsUnchangedFiles() throws IOException {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getType()).thenReturn(TurStorageType.MINIO);

        Files.writeString(tempDir.resolve("_0.cfs"), "data");
        long localSize = Files.size(tempDir.resolve("_0.cfs"));

        // Remote has same file with same size
        when(storageService.listObjects("lucene-indexes/test-core/")).thenReturn(List.of(
                new TurAssetItem("lucene-indexes/test-core/_0.cfs", localSize, "application/octet-stream", "", false)
        ));

        sync.syncToStorage(tempDir, "test-core");

        verify(storageService, never()).uploadStream(eq("lucene-indexes/test-core/_0.cfs"), any(), anyLong(), anyString());
    }

    @Test
    void syncToStorageDeletesOrphanedRemoteFiles() throws IOException {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getType()).thenReturn(TurStorageType.MINIO);

        // No local files
        when(storageService.listObjects("lucene-indexes/test-core/")).thenReturn(List.of(
                new TurAssetItem("lucene-indexes/test-core/_old.cfs", 100, "application/octet-stream", "", false)
        ));

        sync.syncToStorage(tempDir, "test-core");

        verify(storageService).deleteObject("lucene-indexes/test-core/_old.cfs");
    }

    @Test
    void syncToStorageExcludesWriteLock() throws IOException {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getType()).thenReturn(TurStorageType.MINIO);
        when(storageService.listObjects(anyString())).thenReturn(List.of());

        Files.writeString(tempDir.resolve("write.lock"), "");
        Files.writeString(tempDir.resolve("_0.cfs"), "data");

        sync.syncToStorage(tempDir, "test-core");

        verify(storageService, never()).uploadStream(eq("lucene-indexes/test-core/write.lock"), any(), anyLong(), anyString());
        verify(storageService).uploadStream(eq("lucene-indexes/test-core/_0.cfs"), any(), anyLong(), anyString());
    }

    // ---- syncFromStorage ----

    @Test
    void syncFromStorageDownloadsMissingFiles() throws IOException {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getType()).thenReturn(TurStorageType.MINIO);
        when(storageService.listObjects("lucene-indexes/test-core/")).thenReturn(List.of(
                new TurAssetItem("lucene-indexes/test-core/_0.cfs", 12, "application/octet-stream", "", false),
                new TurAssetItem("lucene-indexes/test-core/segments_1", 5, "application/octet-stream", "", false)
        ));
        when(storageService.downloadObject("lucene-indexes/test-core/_0.cfs"))
                .thenReturn(new ByteArrayInputStream("segment-data".getBytes()));
        when(storageService.downloadObject("lucene-indexes/test-core/segments_1"))
                .thenReturn(new ByteArrayInputStream("point".getBytes()));

        sync.syncFromStorage(tempDir, "test-core");

        assertThat(tempDir.resolve("_0.cfs")).exists();
        assertThat(tempDir.resolve("segments_1")).exists();
        verify(storageService, times(2)).downloadObject(anyString());
    }

    @Test
    void syncFromStorageSkipsMatchingFiles() throws IOException {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getType()).thenReturn(TurStorageType.MINIO);

        Files.writeString(tempDir.resolve("_0.cfs"), "data");
        long localSize = Files.size(tempDir.resolve("_0.cfs"));

        when(storageService.listObjects("lucene-indexes/test-core/")).thenReturn(List.of(
                new TurAssetItem("lucene-indexes/test-core/_0.cfs", localSize, "application/octet-stream", "", false)
        ));

        sync.syncFromStorage(tempDir, "test-core");

        verify(storageService, never()).downloadObject(anyString());
    }

    // ---- deleteFromStorage ----

    @Test
    void deleteFromStorageRemovesPrefix() {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getType()).thenReturn(TurStorageType.MINIO);

        sync.deleteFromStorage("test-core");

        verify(storageService).deleteObjectsWithPrefix("lucene-indexes/test-core/");
    }

    @Test
    void deleteFromStorageNoOpWhenDisabled() {
        when(storageService.isEnabled()).thenReturn(false);

        sync.deleteFromStorage("test-core");

        verify(storageService, never()).deleteObjectsWithPrefix(anyString());
    }
}

package com.viglet.turing.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurMinioProperty;
import com.viglet.turing.properties.TurStorageProperty;

/**
 * Tests for TurMinioStorageService.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@ExtendWith(MockitoExtension.class)
class TurMinioStorageServiceTest {

    @Mock
    private TurConfigProperties configProperties;

    private TurMinioStorageService service;

    @BeforeEach
    void setUp() {
        service = new TurMinioStorageService(configProperties);
    }

    @Test
    void getTypeShouldReturnMinio() {
        assertThat(service.getType()).isEqualTo(TurStorageType.MINIO);
    }

    // --- isEnabled: false when init() not called (minioClient is null) ---

    @Test
    void isEnabledShouldReturnFalseWhenNotInitialized() {
        assertThat(service.isEnabled()).isFalse();
    }

    // --- listObjects when minioClient is null (disabled) ---

    @ParameterizedTest(name = "listObjects(prefix={0}) -> empty")
    @NullSource
    @ValueSource(strings = {"", "prefix/", "   "})
    void listObjectsShouldReturnEmptyListWhenDisabled(String prefix) {
        List<TurAssetItem> result = service.listObjects(prefix);
        assertThat(result).isEmpty();
    }

    // --- listAllObjects when minioClient is null ---

    @Test
    void listAllObjectsShouldReturnEmptyListWhenDisabled() {
        List<TurAssetItem> result = service.listAllObjects();
        assertThat(result).isEmpty();
    }

    // --- downloadObject when minioClient is null ---

    @Test
    void downloadObjectShouldThrowWhenDisabled() {
        assertThatThrownBy(() -> service.downloadObject("test.pdf"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MinIO is not configured");
    }

    // --- statObject when minioClient is null ---

    @Test
    void statObjectShouldThrowWhenDisabled() {
        assertThatThrownBy(() -> service.statObject("test.pdf"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MinIO is not configured");
    }

    // --- uploadObject when minioClient is null ---

    @ParameterizedTest(name = "uploadObject(file, prefix={0}) -> throws when disabled")
    @NullSource
    @ValueSource(strings = {"", "some/prefix/"})
    void uploadObjectShouldThrowWhenDisabled(String prefix) {
        org.springframework.web.multipart.MultipartFile file =
                new org.springframework.mock.web.MockMultipartFile("file", "test.txt",
                        "text/plain", "content".getBytes());
        assertThatThrownBy(() -> service.uploadObject(file, prefix))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MinIO is not configured");
    }

    // --- createFolder when minioClient is null ---

    @Test
    void createFolderShouldThrowWhenDisabled() {
        assertThatThrownBy(() -> service.createFolder("new-folder/"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MinIO is not configured");
    }

    // --- deleteObject when minioClient is null ---

    @Test
    void deleteObjectShouldThrowWhenDisabled() {
        assertThatThrownBy(() -> service.deleteObject("test.pdf"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MinIO is not configured");
    }

    // --- init Tests ---

    private TurStorageProperty storageWithMinio(TurMinioProperty minio) {
        TurStorageProperty storage = new TurStorageProperty();
        storage.setMinio(minio);
        return storage;
    }

    @Test
    void initShouldNotCreateClientWhenStorageNull() {
        when(configProperties.getStorage()).thenReturn(null);
        service.init();
        assertThat(service.listObjects("")).isEmpty();
    }

    @Test
    void initShouldNotCreateClientWhenMinioNull() {
        TurStorageProperty storage = new TurStorageProperty();
        when(configProperties.getStorage()).thenReturn(storage);
        service.init();
        assertThat(service.listObjects("")).isEmpty();
    }

    @Test
    void initShouldNotCreateClientWhenEndpointNull() {
        TurMinioProperty minio = new TurMinioProperty();
        minio.setEndpoint(null);
        when(configProperties.getStorage()).thenReturn(storageWithMinio(minio));
        service.init();
        assertThat(service.listObjects("")).isEmpty();
    }

    @Test
    void initShouldHandleUnreachableEndpointGracefully() {
        TurMinioProperty minio = new TurMinioProperty();
        minio.setEndpoint("http://localhost:19999");
        minio.setAccessKey("testKey");
        minio.setSecretKey("testSecret");
        minio.setBucket("test-bucket");
        when(configProperties.getStorage()).thenReturn(storageWithMinio(minio));
        service.init();
        assertThat(service.isEnabled()).isTrue();
    }

}

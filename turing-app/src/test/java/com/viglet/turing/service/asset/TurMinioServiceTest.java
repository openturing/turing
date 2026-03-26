package com.viglet.turing.service.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurMinioProperty;

/**
 * Tests for TurMinioService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurMinioServiceTest {

    @Mock
    private TurConfigProperties configProperties;

    private TurMinioService service;

    @BeforeEach
    void setUp() {
        service = new TurMinioService(configProperties);
    }

    // --- isEnabled Tests ---

    @Test
    void isEnabledShouldReturnFalseWhenMinioPropertyIsNull() {
        when(configProperties.getMinio()).thenReturn(null);

        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void isEnabledShouldReturnFalseWhenMinioDisabled() {
        TurMinioProperty minio = new TurMinioProperty();
        minio.setEnabled(false);
        when(configProperties.getMinio()).thenReturn(minio);

        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void isEnabledShouldReturnTrueWhenMinioEnabled() {
        TurMinioProperty minio = new TurMinioProperty();
        minio.setEnabled(true);
        when(configProperties.getMinio()).thenReturn(minio);

        assertThat(service.isEnabled()).isTrue();
    }

    // --- listObjects when minioClient is null (disabled) ---

    @Test
    void listObjectsShouldReturnEmptyListWhenDisabled() {
        // minioClient is null because init() was not called (no @PostConstruct in test)
        List<TurAssetItem> result = service.listObjects("");

        assertThat(result).isEmpty();
    }

    @Test
    void listObjectsShouldReturnEmptyListWithPrefix() {
        List<TurAssetItem> result = service.listObjects("prefix/");

        assertThat(result).isEmpty();
    }

    @Test
    void listObjectsShouldReturnEmptyListWithNull() {
        List<TurAssetItem> result = service.listObjects(null);

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

    @Test
    void uploadObjectShouldThrowWhenDisabled() {
        org.springframework.web.multipart.MultipartFile file =
                new org.springframework.mock.web.MockMultipartFile("file", "test.txt",
                        "text/plain", "content".getBytes());

        assertThatThrownBy(() -> service.uploadObject(file, ""))
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

    // --- guessContentType Tests ---

    @Test
    void guessContentTypeShouldReturnPdfForPdfExtension() throws Exception {
        Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "document.pdf")).isEqualTo("application/pdf");
    }

    @Test
    void guessContentTypeShouldReturnPngForPngExtension() throws Exception {
        Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "image.png")).isEqualTo("image/png");
    }

    @Test
    void guessContentTypeShouldReturnJpegForJpgExtension() throws Exception {
        Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "photo.jpg")).isEqualTo("image/jpeg");
        assertThat(method.invoke(service, "photo.jpeg")).isEqualTo("image/jpeg");
    }

    @Test
    void guessContentTypeShouldReturnGifForGifExtension() throws Exception {
        Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "animation.gif")).isEqualTo("image/gif");
    }

    @Test
    void guessContentTypeShouldReturnSvgForSvgExtension() throws Exception {
        Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "logo.svg")).isEqualTo("image/svg+xml");
    }

    @Test
    void guessContentTypeShouldReturnTextForTxtExtension() throws Exception {
        Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "readme.txt")).isEqualTo("text/plain");
    }

    @Test
    void guessContentTypeShouldReturnCsvForCsvExtension() throws Exception {
        Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "data.csv")).isEqualTo("text/csv");
    }

    @Test
    void guessContentTypeShouldReturnJsonForJsonExtension() throws Exception {
        Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "config.json")).isEqualTo("application/json");
    }

    @Test
    void guessContentTypeShouldReturnXmlForXmlExtension() throws Exception {
        Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "data.xml")).isEqualTo("application/xml");
    }

    @Test
    void guessContentTypeShouldReturnZipForZipExtension() throws Exception {
        Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "archive.zip")).isEqualTo("application/zip");
    }

    @Test
    void guessContentTypeShouldReturnMsWordForDocExtension() throws Exception {
        Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "document.doc")).isEqualTo("application/msword");
        assertThat(method.invoke(service, "document.docx")).isEqualTo("application/msword");
    }

    @Test
    void guessContentTypeShouldReturnExcelForXlsExtension() throws Exception {
        Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "spreadsheet.xls")).isEqualTo("application/vnd.ms-excel");
        assertThat(method.invoke(service, "spreadsheet.xlsx")).isEqualTo("application/vnd.ms-excel");
    }

    @Test
    void guessContentTypeShouldReturnOctetStreamForUnknown() throws Exception {
        Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "unknown.xyz")).isEqualTo("application/octet-stream");
    }

    @Test
    void guessContentTypeShouldReturnOctetStreamForNull() throws Exception {
        Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, (String) null)).isEqualTo("application/octet-stream");
    }

    @Test
    void guessContentTypeShouldBeCaseInsensitive() throws Exception {
        Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "IMAGE.PNG")).isEqualTo("image/png");
        assertThat(method.invoke(service, "Document.PDF")).isEqualTo("application/pdf");
    }

    // --- init Tests ---

    @Test
    void initShouldNotCreateClientWhenMinioPropertyNull() {
        when(configProperties.getMinio()).thenReturn(null);

        service.init();

        // Service should remain disabled - listObjects returns empty
        assertThat(service.listObjects("")).isEmpty();
    }

    @Test
    void initShouldNotCreateClientWhenMinioDisabled() {
        TurMinioProperty minio = new TurMinioProperty();
        minio.setEnabled(false);
        when(configProperties.getMinio()).thenReturn(minio);

        service.init();

        assertThat(service.listObjects("")).isEmpty();
    }

    @Test
    void initShouldNotCreateClientWhenEndpointNull() {
        TurMinioProperty minio = new TurMinioProperty();
        minio.setEnabled(true);
        minio.setEndpoint(null);
        when(configProperties.getMinio()).thenReturn(minio);

        service.init();

        assertThat(service.listObjects("")).isEmpty();
    }

    // --- uploadObject prefix handling ---

    @Test
    void uploadObjectShouldThrowWhenDisabledWithPrefix() {
        org.springframework.web.multipart.MultipartFile file =
                new org.springframework.mock.web.MockMultipartFile("file", "test.txt",
                        "text/plain", "content".getBytes());

        assertThatThrownBy(() -> service.uploadObject(file, "some/prefix/"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MinIO is not configured");
    }

    @Test
    void uploadObjectShouldThrowWhenDisabledWithNullPrefix() {
        org.springframework.web.multipart.MultipartFile file =
                new org.springframework.mock.web.MockMultipartFile("file", "test.txt",
                        "text/plain", "content".getBytes());

        assertThatThrownBy(() -> service.uploadObject(file, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MinIO is not configured");
    }

    // --- createFolder path normalization ---

    @Test
    void createFolderWithoutTrailingSlashShouldThrowWhenDisabled() {
        assertThatThrownBy(() -> service.createFolder("new-folder"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MinIO is not configured");
    }

    // --- guessContentType: additional edge cases ---

    @Test
    void guessContentTypeShouldHandleNoExtension() throws Exception {
        java.lang.reflect.Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "filenoext")).isEqualTo("application/octet-stream");
    }

    @Test
    void guessContentTypeShouldHandleEmptyString() throws Exception {
        java.lang.reflect.Method method = TurMinioService.class.getDeclaredMethod("guessContentType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "")).isEqualTo("application/octet-stream");
    }

    // --- init with enabled but unreachable endpoint (catches exception in ensureBucket) ---

    @Test
    void initShouldHandleUnreachableEndpointGracefully() {
        TurMinioProperty minio = new TurMinioProperty();
        minio.setEnabled(true);
        minio.setEndpoint("http://localhost:19999");
        minio.setAccessKey("testKey");
        minio.setSecretKey("testSecret");
        minio.setBucket("test-bucket");
        when(configProperties.getMinio()).thenReturn(minio);

        // init() should not throw - ensureBucket catches the connection exception
        service.init();

        // MinIO client was created but bucket check failed silently
        assertThat(service.isEnabled()).isTrue();
    }

    // --- listObjects with blank prefix normalization ---

    @Test
    void listObjectsShouldReturnEmptyListWithBlankPrefix() {
        List<TurAssetItem> result = service.listObjects("   ");

        assertThat(result).isEmpty();
    }
}

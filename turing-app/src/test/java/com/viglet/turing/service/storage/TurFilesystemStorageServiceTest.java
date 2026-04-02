package com.viglet.turing.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurStorageProperty;
import com.viglet.turing.properties.TurStorageProperty.TurFilesystemProperty;

/**
 * Tests for TurFilesystemStorageService.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@ExtendWith(MockitoExtension.class)
class TurFilesystemStorageServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private TurConfigProperties configProperties;

    private TurFilesystemStorageService service;

    @BeforeEach
    void setUp() {
        TurFilesystemProperty fs = new TurFilesystemProperty();
        fs.setPath(tempDir.toString());
        TurStorageProperty storage = new TurStorageProperty();
        storage.setFilesystem(fs);
        when(configProperties.getStorage()).thenReturn(storage);
        service = new TurFilesystemStorageService(configProperties);
        service.init();
    }

    @Test
    void getTypeShouldReturnFilesystem() {
        assertThat(service.getType()).isEqualTo(TurStorageType.FILESYSTEM);
    }

    @Test
    void isEnabledShouldReturnTrue() {
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void listObjectsShouldReturnEmptyForEmptyDir() {
        assertThat(service.listObjects("")).isEmpty();
    }

    @Test
    void uploadAndDownloadRoundTrip() throws Exception {
        byte[] content = "hello world".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);
        service.uploadObject(file, "");

        assertThat(service.listObjects("")).hasSize(1);

        try (InputStream is = service.downloadObject("test.txt")) {
            assertThat(is.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void uploadWithPrefixCreatesSubdirectory() throws Exception {
        byte[] content = "data".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", content);
        service.uploadObject(file, "docs/");

        assertThat(Files.exists(tempDir.resolve("docs/report.pdf"))).isTrue();

        TurStorageObjectStat stat = service.statObject("docs/report.pdf");
        assertThat(stat.size()).isEqualTo(content.length);
        assertThat(stat.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void uploadStreamAndDownload() throws Exception {
        byte[] content = "<html>hello</html>".getBytes();
        service.uploadStream("pages/index.html", new ByteArrayInputStream(content), content.length, "text/html");

        try (InputStream is = service.downloadObject("pages/index.html")) {
            assertThat(is.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void createFolderShouldCreateDirectory() {
        service.createFolder("newfolder/sub");
        assertThat(Files.isDirectory(tempDir.resolve("newfolder/sub"))).isTrue();
        assertThat(service.listObjects("newfolder/")).hasSize(1);
    }

    @Test
    void deleteObjectShouldRemoveFile() throws Exception {
        byte[] content = "temp".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "temp.txt", "text/plain", content);
        service.uploadObject(file, "");

        assertThat(Files.exists(tempDir.resolve("temp.txt"))).isTrue();
        service.deleteObject("temp.txt");
        assertThat(Files.exists(tempDir.resolve("temp.txt"))).isFalse();
    }

    @Test
    void deleteObjectsWithPrefixShouldRemoveAll() throws Exception {
        service.uploadStream("site/a.html", new ByteArrayInputStream("a".getBytes()), 1, "text/html");
        service.uploadStream("site/b.html", new ByteArrayInputStream("b".getBytes()), 1, "text/html");

        service.deleteObjectsWithPrefix("site");
        assertThat(Files.exists(tempDir.resolve("site"))).isFalse();
    }

    @Test
    void listAllObjectsShouldReturnRecursiveFiles() throws Exception {
        service.uploadStream("a.txt", new ByteArrayInputStream("a".getBytes()), 1, "text/plain");
        service.uploadStream("sub/b.txt", new ByteArrayInputStream("b".getBytes()), 1, "text/plain");

        assertThat(service.listAllObjects()).hasSize(2);
    }

    @Test
    void statObjectShouldReturnMetadata() throws Exception {
        byte[] content = "test content".getBytes();
        service.uploadStream("doc.txt", new ByteArrayInputStream(content), content.length, "text/plain");

        TurStorageObjectStat stat = service.statObject("doc.txt");
        assertThat(stat.objectName()).isEqualTo("doc.txt");
        assertThat(stat.size()).isEqualTo(content.length);
        assertThat(stat.contentType()).isEqualTo("text/plain");
        assertThat(stat.lastModified()).isNotBlank();
    }

    @Test
    void pathTraversalShouldBeRejected() {
        assertThatThrownBy(() -> service.downloadObject("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path traversal");
    }

    @Test
    void downloadNonExistentFileShouldThrow() {
        assertThatThrownBy(() -> service.downloadObject("nonexistent.txt"))
                .isInstanceOf(IllegalStateException.class);
    }
}

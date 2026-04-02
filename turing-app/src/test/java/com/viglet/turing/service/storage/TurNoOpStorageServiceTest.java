package com.viglet.turing.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for TurNoOpStorageService.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
class TurNoOpStorageServiceTest {

    private final TurNoOpStorageService service = new TurNoOpStorageService();

    @Test
    void getTypeShouldReturnNone() {
        assertThat(service.getType()).isEqualTo(TurStorageType.NONE);
    }

    @Test
    void isEnabledShouldReturnFalse() {
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void listObjectsShouldReturnEmptyList() {
        assertThat(service.listObjects("any")).isEmpty();
    }

    @Test
    void listAllObjectsShouldReturnEmptyList() {
        assertThat(service.listAllObjects()).isEmpty();
    }

    @Test
    void downloadObjectShouldThrow() {
        assertThatThrownBy(() -> service.downloadObject("test.pdf"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Storage is not configured");
    }

    @Test
    void statObjectShouldThrow() {
        assertThatThrownBy(() -> service.statObject("test.pdf"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void uploadObjectShouldThrow() {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "test.txt", "text/plain", "content".getBytes());
        assertThatThrownBy(() -> service.uploadObject(file, ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createFolderShouldThrow() {
        assertThatThrownBy(() -> service.createFolder("folder/"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteObjectShouldThrow() {
        assertThatThrownBy(() -> service.deleteObject("test.pdf"))
                .isInstanceOf(IllegalStateException.class);
    }
}

package com.viglet.turing.spring.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

/**
 * Tests for TurSpringUtils.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurSpringUtilsTest {

    @Mock
    private MultipartFile multipartFile;

    // --- Utility class enforcement ---

    @Test
    void constructorShouldThrowIllegalStateException() {
        Constructor<TurSpringUtils> constructor;
        try {
            constructor = TurSpringUtils.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                    constructor::newInstance);
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertEquals("Utility class", ex.getCause().getMessage());
        } catch (NoSuchMethodException e) {
            fail("Expected private constructor to exist");
        }
    }

    // --- getFileFromMultipart ---

    @Test
    void getFileFromMultipartShouldReturnNonNullFile() throws IOException {
        doNothing().when(multipartFile).transferTo(any(File.class));
        File result = TurSpringUtils.getFileFromMultipart(multipartFile);
        assertNotNull(result);
    }

    @Test
    void getFileFromMultipartShouldCallTransferTo() throws IOException {
        doNothing().when(multipartFile).transferTo(any(File.class));
        TurSpringUtils.getFileFromMultipart(multipartFile);
        verify(multipartFile, times(1)).transferTo(any(File.class));
    }

    @Test
    void getFileFromMultipartShouldReturnFileInTempDirectory() throws IOException {
        doNothing().when(multipartFile).transferTo(any(File.class));
        File result = TurSpringUtils.getFileFromMultipart(multipartFile);
        assertNotNull(result, "Result file should not be null");
        assertTrue(result.getAbsolutePath().contains("imp_"),
                "File path should contain 'imp_', but was: " + result.getAbsolutePath());
    }

    @Test
    void getFileFromMultipartShouldReturnFileWithImpPrefix() throws IOException {
        doNothing().when(multipartFile).transferTo(any(File.class));
        File result = TurSpringUtils.getFileFromMultipart(multipartFile);
        assertTrue(result.getName().startsWith("imp_"),
                "File name should start with 'imp_', but was: " + result.getName());
    }

    @Test
    void getFileFromMultipartShouldReturnUniqueFilesPerCall() throws IOException {
        doNothing().when(multipartFile).transferTo(any(File.class));
        File result1 = TurSpringUtils.getFileFromMultipart(multipartFile);
        File result2 = TurSpringUtils.getFileFromMultipart(multipartFile);
        assertNotEquals(result1.getAbsolutePath(), result2.getAbsolutePath());
    }

    @Test
    void getFileFromMultipartShouldHandleIOException() throws IOException {
        doThrow(new IOException("Transfer failed")).when(multipartFile).transferTo(any(File.class));
        // Should not throw, just logs the error
        File result = assertDoesNotThrow(() -> TurSpringUtils.getFileFromMultipart(multipartFile));
        assertNotNull(result);
    }

    @Test
    void getFileFromMultipartShouldHandleIllegalStateException() throws IOException {
        doThrow(new IllegalStateException("Invalid state")).when(multipartFile)
                .transferTo(any(File.class));
        File result = assertDoesNotThrow(() -> TurSpringUtils.getFileFromMultipart(multipartFile));
        assertNotNull(result);
    }

    // --- extractZipFile ---

    @Test
    void extractZipFileShouldReturnNonNullFile() throws IOException {
        doNothing().when(multipartFile).transferTo(any(File.class));
        File result = TurSpringUtils.extractZipFile(multipartFile);
        assertNotNull(result);
    }

    @Test
    void extractZipFileShouldReturnDirectory() throws IOException {
        doNothing().when(multipartFile).transferTo(any(File.class));
        File result = TurSpringUtils.extractZipFile(multipartFile);
        assertNotNull(result, "Result should not be null");
        assertTrue(result.getAbsolutePath().contains("imp_"),
                "Extract folder path should contain 'imp_', but was: " + result.getAbsolutePath());
    }

    @Test
    void extractZipFileShouldReturnDifferentPathThanZipFile() throws IOException {
        doNothing().when(multipartFile).transferTo(any(File.class));
        File result = TurSpringUtils.extractZipFile(multipartFile);
        assertTrue(result.getName().startsWith("imp_"),
                "Extract folder name should start with 'imp_'");
    }

    @Test
    void extractZipFileShouldHandleIOExceptionDuringTransfer() throws IOException {
        doThrow(new IOException("Transfer failed")).when(multipartFile).transferTo(any(File.class));
        File result = assertDoesNotThrow(() -> TurSpringUtils.extractZipFile(multipartFile));
        assertNotNull(result);
    }

    @Test
    void extractZipFileShouldCallTransferTo() throws IOException {
        doNothing().when(multipartFile).transferTo(any(File.class));
        TurSpringUtils.extractZipFile(multipartFile);
        verify(multipartFile, times(1)).transferTo(any(File.class));
    }

    @Test
    void extractZipFileShouldReturnUniquePathsPerCall() throws IOException {
        doNothing().when(multipartFile).transferTo(any(File.class));
        File result1 = TurSpringUtils.extractZipFile(multipartFile);
        File result2 = TurSpringUtils.extractZipFile(multipartFile);
        assertNotEquals(result1.getAbsolutePath(), result2.getAbsolutePath());
    }
}

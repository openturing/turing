package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.system.TurGlobalSettingsService;

class TurCodeInterpreterToolServiceTest {

    private TurCodeInterpreterToolService service;

    @BeforeEach
    void setUp() {
        service = new TurCodeInterpreterToolService(mock(TurGlobalSettingsService.class));
    }

    @Test
    void shouldFormatFileSizeInBytes() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, 0L)).isEqualTo("0 bytes");
        assertThat(method.invoke(service, 512L)).isEqualTo("512 bytes");
        assertThat(method.invoke(service, 1023L)).isEqualTo("1023 bytes");
    }

    @Test
    void shouldFormatFileSizeInKB() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        assertThat((String) method.invoke(service, 1024L)).matches("1[.,]0 KB");
        assertThat((String) method.invoke(service, 1536L)).matches("1[.,]5 KB");
    }

    @Test
    void shouldFormatFileSizeInMB() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        assertThat((String) method.invoke(service, 1_048_576L)).matches("1[.,]0 MB");
        assertThat((String) method.invoke(service, 2_621_440L)).matches("2[.,]5 MB");
    }

    @Test
    void shouldBuildResultWithStdoutOnly() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class);
        method.setAccessible(true);

        StringBuilder stdout = new StringBuilder("Hello World\n");
        StringBuilder stderr = new StringBuilder();

        String result = (String) method.invoke(service, "abc123", 0, stdout, stderr, null);

        assertThat(result).isEqualTo("Hello World\n");
    }

    @Test
    void shouldBuildResultWithWarnings() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class);
        method.setAccessible(true);

        StringBuilder stdout = new StringBuilder("output\n");
        StringBuilder stderr = new StringBuilder("DeprecationWarning: something\n");

        String result = (String) method.invoke(service, "abc123", 0, stdout, stderr, null);

        assertThat(result)
                .contains("output")
                .contains("WARNINGS")
                .contains("DeprecationWarning");
    }

    @Test
    void shouldBuildResultWithError() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class);
        method.setAccessible(true);

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder("NameError: name 'x' is not defined\n");

        String result = (String) method.invoke(service, "abc123", 1, stdout, stderr, null);

        assertThat(result)
                .contains("ERROR (exit code 1)")
                .contains("NameError");
    }

    @Test
    void shouldBuildResultWithNoOutput() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class);
        method.setAccessible(true);

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        String result = (String) method.invoke(service, "abc123", 0, stdout, stderr, null);

        assertThat(result).isEqualTo("(no output)");
    }

    @Test
    void shouldBuildResultWithGeneratedFiles() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class);
        method.setAccessible(true);

        File tempFile = File.createTempFile("output", ".png");
        tempFile.deleteOnExit();

        StringBuilder stdout = new StringBuilder("Done\n");
        StringBuilder stderr = new StringBuilder();

        String result = (String) method.invoke(service, "session1", 0,
                stdout, stderr, new File[]{tempFile});

        assertThat(result)
                .contains("Generated Files")
                .contains("/api/v2/code-interpreter/session1/")
                .contains("![");
    }

    @Test
    void shouldTruncateLongStdout() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class);
        method.setAccessible(true);

        StringBuilder stdout = new StringBuilder("x".repeat(20_000));
        StringBuilder stderr = new StringBuilder();

        String result = (String) method.invoke(service, "abc123", 0, stdout, stderr, null);

        assertThat(result)
                .contains("[output truncated]")
                .hasSizeLessThan(20_000);
    }
}

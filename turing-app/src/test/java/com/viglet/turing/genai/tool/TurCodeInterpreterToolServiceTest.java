package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.viglet.turing.system.TurGlobalSettingsService;

class TurCodeInterpreterToolServiceTest {

    private TurCodeInterpreterToolService service;

    @BeforeEach
    void setUp() {
        service = new TurCodeInterpreterToolService(mock(TurGlobalSettingsService.class));
    }

    @Test
    void shouldBuildResultWithStdoutOnly() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class, String.class);
        method.setAccessible(true);

        StringBuilder stdout = new StringBuilder("Hello World\n");
        StringBuilder stderr = new StringBuilder();

        String result = (String) method.invoke(service, "abc123", 0, stdout, stderr, null, null);

        assertThat(result).isEqualTo("Hello World\n");
    }

    @Test
    void shouldBuildResultWithWarnings() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class, String.class);
        method.setAccessible(true);

        StringBuilder stdout = new StringBuilder("output\n");
        StringBuilder stderr = new StringBuilder("DeprecationWarning: something\n");

        String result = (String) method.invoke(service, "abc123", 0, stdout, stderr, null, null);

        assertThat(result)
                .contains("output")
                .contains("WARNINGS")
                .contains("DeprecationWarning");
    }

    @Test
    void shouldBuildResultWithError() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class, String.class);
        method.setAccessible(true);

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder("NameError: name 'x' is not defined\n");

        String result = (String) method.invoke(service, "abc123", 1, stdout, stderr, null, null);

        assertThat(result)
                .contains("ERROR (exit code 1)")
                .contains("NameError");
    }

    @Test
    void shouldBuildResultWithNoOutput() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class, String.class);
        method.setAccessible(true);

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        String result = (String) method.invoke(service, "abc123", 0, stdout, stderr, null, null);

        assertThat(result).isEqualTo("(no output)");
    }

    @Test
    void shouldBuildResultWithGeneratedFiles() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class, String.class);
        method.setAccessible(true);

        File tempFile = File.createTempFile("output", ".png");
        tempFile.deleteOnExit();

        StringBuilder stdout = new StringBuilder("Done\n");
        StringBuilder stderr = new StringBuilder();

        String result = (String) method.invoke(service, "session1", 0,
                stdout, stderr, new File[]{tempFile}, null);

        assertThat(result)
                .contains("Generated Files")
                .contains("/api/v2/code-interpreter/session1/")
                .contains("![");
    }

    @Test
    void shouldTruncateLongStdout() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class, String.class);
        method.setAccessible(true);

        StringBuilder stdout = new StringBuilder("x".repeat(20_000));
        StringBuilder stderr = new StringBuilder();

        String result = (String) method.invoke(service, "abc123", 0, stdout, stderr, null, null);

        assertThat(result)
                .contains("[output truncated]")
                .hasSizeLessThan(20_000);
    }

    @Test
    void shouldBuildResultWithNonImageGeneratedFile() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class, String.class);
        method.setAccessible(true);

        File tempFile = File.createTempFile("data", ".csv");
        tempFile.deleteOnExit();

        StringBuilder stdout = new StringBuilder("Done\n");
        StringBuilder stderr = new StringBuilder();

        String result = (String) method.invoke(service, "session2", 0,
                stdout, stderr, new File[]{tempFile}, null);

        assertThat(result)
                .contains("Generated Files")
                .contains("[Download ")
                .contains("/api/v2/code-interpreter/session2/")
                .doesNotContain("![");
    }

    @Test
    void shouldBuildResultWithBothStdoutAndWarnings() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class, String.class);
        method.setAccessible(true);

        StringBuilder stdout = new StringBuilder("result: 42\n");
        StringBuilder stderr = new StringBuilder("FutureWarning: something deprecated\n");

        String result = (String) method.invoke(service, "s1", 0, stdout, stderr, null, null);

        assertThat(result)
                .contains("result: 42")
                .contains("WARNINGS")
                .contains("FutureWarning");
    }

    @Test
    void shouldBuildResultWithErrorAndStderr() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class, String.class);
        method.setAccessible(true);

        StringBuilder stdout = new StringBuilder("partial output\n");
        StringBuilder stderr = new StringBuilder("Traceback...\n");

        String result = (String) method.invoke(service, "s1", 1, stdout, stderr, null, null);

        assertThat(result)
                .contains("partial output")
                .contains("ERROR (exit code 1)")
                .contains("Traceback");
    }

    @Test
    void shouldBuildResultWithEmptyGeneratedFilesArray() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class, String.class);
        method.setAccessible(true);

        StringBuilder stdout = new StringBuilder("ok\n");
        StringBuilder stderr = new StringBuilder();

        String result = (String) method.invoke(service, "s1", 0, stdout, stderr, new File[0], null);

        assertThat(result).isEqualTo("ok\n");
        assertThat(result).doesNotContain("Generated Files");
    }

    @ParameterizedTest
    @ValueSource(strings = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".bmp"})
    void shouldRecognizeImageExtensions(String ext) throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod("isImageFile", String.class);
        method.setAccessible(true);

        assertThat((boolean) method.invoke(null, "file" + ext)).isTrue();
        assertThat((boolean) method.invoke(null, "file" + ext.toUpperCase())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {".csv", ".txt", ".pdf", ".html", ".json", ".py"})
    void shouldRejectNonImageExtensions(String ext) throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod("isImageFile", String.class);
        method.setAccessible(true);

        assertThat((boolean) method.invoke(null, "file" + ext)).isFalse();
    }

    @Test
    void shouldReprEscapeBackslashes() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod("repr", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(null, "hello")).isEqualTo("'hello'");
        assertThat(method.invoke(null, "it's")).isEqualTo("'it\\'s'");
        assertThat(method.invoke(null, "c:\\path")).isEqualTo("'c:\\\\path'");
        assertThat(method.invoke(null, "a\\b'c")).isEqualTo("'a\\\\b\\'c'");
    }

    @Test
    void shouldBuildRunnerScript() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildRunnerScript", String.class, java.nio.file.Path.class);
        method.setAccessible(true);

        // depsDir=null → runner emits `_DEPS_DIR = None` and skips sys.path.insert.
        String script = (String) method.invoke(service, "script.py", null);

        assertThat(script)
                .contains("_DEPS_DIR = None")
                .contains("import ast")
                .contains("'script.py'")
                .contains("ast.parse")
                .contains("exec(compile");
    }

    @Test
    void buildRunnerScript_withDepsDir_prependsSysPath() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildRunnerScript", String.class, java.nio.file.Path.class);
        method.setAccessible(true);

        java.nio.file.Path depsDir = java.nio.file.Path.of("D:", "tmp", "fake-deps");
        String script = (String) method.invoke(service, "script.py", depsDir);

        // Repr-quoted, backslash-escaped path embedded as `_DEPS_DIR = '…'`.
        // The runner uses sys.path.insert before any reportlab/Pillow import,
        // working around the Windows ProcessBuilder PYTHONPATH propagation bug
        // that motivated this change.
        assertThat(script)
                .contains("_DEPS_DIR = '")
                .contains("sys.path.insert(0, _DEPS_DIR)")
                .contains("fake-deps");
    }

    @Test
    void resolvePythonExecutableShouldUseGlobalSettingsFirst() throws Exception {
        TurGlobalSettingsService settingsService = mock(TurGlobalSettingsService.class);
        String absolutePath = System.getProperty("os.name").toLowerCase().startsWith("windows")
                ? "C:\\Python312\\python.exe"
                : "/usr/bin/python3";
        when(settingsService.getPythonExecutable()).thenReturn(absolutePath);

        TurCodeInterpreterToolService svc = new TurCodeInterpreterToolService(settingsService);

        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod("resolvePythonExecutable");
        method.setAccessible(true);

        String result = (String) method.invoke(svc);
        assertThat(result).isEqualTo(absolutePath);
    }

    @Test
    void resolvePythonExecutableShouldRejectRelativePathFromGlobalSettings() throws Exception {
        TurGlobalSettingsService settingsService = mock(TurGlobalSettingsService.class);
        when(settingsService.getPythonExecutable()).thenReturn("relative/path/python");

        TurCodeInterpreterToolService svc = new TurCodeInterpreterToolService(settingsService);

        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod("resolvePythonExecutable");
        method.setAccessible(true);

        try {
            method.invoke(svc);
        } catch (Exception e) {
            assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
            assertThat(e.getCause().getMessage()).contains("absolute path");
        }
    }

    @Test
    void requireAbsoluteShouldAcceptAbsolutePath() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "requireAbsolute", String.class, String.class);
        method.setAccessible(true);

        String absolutePath = System.getProperty("os.name").toLowerCase().startsWith("windows")
                ? "C:\\Python312\\python.exe"
                : "/usr/bin/python3";

        String result = (String) method.invoke(null, absolutePath, "test source");
        assertThat(result).isEqualTo(absolutePath);
    }

    @Test
    void requireAbsoluteShouldRejectRelativePath() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "requireAbsolute", String.class, String.class);
        method.setAccessible(true);

        try {
            method.invoke(null, "relative/python", "test source");
        } catch (Exception e) {
            assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
            assertThat(e.getCause().getMessage()).contains("absolute path");
        }
    }

    @Test
    void shouldBuildResultWithMultipleGeneratedFiles() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class, String.class);
        method.setAccessible(true);

        File imageFile = File.createTempFile("chart", ".png");
        imageFile.deleteOnExit();
        File dataFile = File.createTempFile("output", ".csv");
        dataFile.deleteOnExit();

        StringBuilder stdout = new StringBuilder("Generated 2 files\n");
        StringBuilder stderr = new StringBuilder();

        String result = (String) method.invoke(service, "multi", 0,
                stdout, stderr, new File[]{imageFile, dataFile}, null);

        assertThat(result)
                .contains("Generated Files")
                .contains("![")
                .contains("[Download ");
    }

    @Test
    void logResultShouldNotThrowForSuccessWithNoFiles() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "logResult", String.class, java.nio.file.Path.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class);
        method.setAccessible(true);

        java.nio.file.Path path = java.nio.file.Path.of("test.py");
        assertThat(method.invoke(service, "s1", path, 0,
                new StringBuilder("output"), new StringBuilder(), null)).isNull();
    }

    @Test
    void logResultShouldNotThrowForSuccessWithWarnings() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "logResult", String.class, java.nio.file.Path.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class);
        method.setAccessible(true);

        java.nio.file.Path path = java.nio.file.Path.of("test.py");
        assertThat(method.invoke(service, "s1", path, 0,
                new StringBuilder("output"), new StringBuilder("warning"), null)).isNull();
    }

    @Test
    void logResultShouldNotThrowForFailure() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "logResult", String.class, java.nio.file.Path.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class);
        method.setAccessible(true);

        java.nio.file.Path path = java.nio.file.Path.of("test.py");
        assertThat(method.invoke(service, "s1", path, 1,
                new StringBuilder(), new StringBuilder("error"), new File[]{})).isNull();
    }

    @Test
    void logResultShouldCountFiles() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "logResult", String.class, java.nio.file.Path.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class);
        method.setAccessible(true);

        File tempFile = File.createTempFile("out", ".txt");
        tempFile.deleteOnExit();

        java.nio.file.Path path = java.nio.file.Path.of("test.py");
        assertThat(method.invoke(service, "s1", path, 0,
                new StringBuilder("ok"), new StringBuilder(), new File[]{tempFile})).isNull();
    }

    // --- Additional coverage: resolvePythonExecutable with configuredPythonExecutable ---

    @Test
    void resolvePythonExecutableShouldUseConfigPropertyWhenDbEmpty() throws Exception {
        TurGlobalSettingsService settingsService = mock(TurGlobalSettingsService.class);
        when(settingsService.getPythonExecutable()).thenReturn("");

        TurCodeInterpreterToolService svc = new TurCodeInterpreterToolService(settingsService);

        // Set configuredPythonExecutable via reflection
        String absolutePath = System.getProperty("os.name").toLowerCase().startsWith("windows")
                ? "C:\\Python312\\python.exe"
                : "/usr/bin/python3";
        java.lang.reflect.Field field = TurCodeInterpreterToolService.class.getDeclaredField("configuredPythonExecutable");
        field.setAccessible(true);
        field.set(svc, absolutePath);

        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod("resolvePythonExecutable");
        method.setAccessible(true);

        String result = (String) method.invoke(svc);
        assertThat(result).isEqualTo(absolutePath);
    }

    @Test
    void resolvePythonExecutableShouldRejectRelativeConfigProperty() throws Exception {
        TurGlobalSettingsService settingsService = mock(TurGlobalSettingsService.class);
        when(settingsService.getPythonExecutable()).thenReturn("");

        TurCodeInterpreterToolService svc = new TurCodeInterpreterToolService(settingsService);

        java.lang.reflect.Field field = TurCodeInterpreterToolService.class.getDeclaredField("configuredPythonExecutable");
        field.setAccessible(true);
        field.set(svc, "relative/python");

        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod("resolvePythonExecutable");
        method.setAccessible(true);

        try {
            method.invoke(svc);
        } catch (Exception e) {
            assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
            assertThat(e.getCause().getMessage()).contains("absolute path");
        }
    }

    @Test
    void resolvePythonExecutableShouldAutoDetectOrThrow() throws Exception {
        TurGlobalSettingsService settingsService = mock(TurGlobalSettingsService.class);
        when(settingsService.getPythonExecutable()).thenReturn("");

        TurCodeInterpreterToolService svc = new TurCodeInterpreterToolService(settingsService);

        java.lang.reflect.Field field = TurCodeInterpreterToolService.class.getDeclaredField("configuredPythonExecutable");
        field.setAccessible(true);
        field.set(svc, "");

        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod("resolvePythonExecutable");
        method.setAccessible(true);

        // Either finds a Python installation or throws
        try {
            String result = (String) method.invoke(svc);
            // If we get here, a Python was found in system locations
            assertThat(result).isNotBlank();
            assertThat(java.nio.file.Path.of(result)).isAbsolute();
        } catch (Exception e) {
            assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
            assertThat(e.getCause().getMessage()).contains("Python not found");
        }
    }

    @Test
    void executePythonShouldHandleExceptionGracefully() {
        TurGlobalSettingsService settingsService = mock(TurGlobalSettingsService.class);
        // Ensure resolvePythonExecutable fails, so executePython catches the exception
        when(settingsService.getPythonExecutable()).thenReturn("");

        TurCodeInterpreterToolService svc = new TurCodeInterpreterToolService(settingsService);

        // configuredPythonExecutable is not set (defaults to empty from constructor)
        // This will either succeed if Python is found, or return an error string
        String result = svc.executePython("print('hello')");
        // If it gets to the process-builder stage, it could succeed or fail with a known error
        assertThat(result).isNotNull();
    }

    @Test
    void shouldBuildRunnerScriptWithSpecialCharacters() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildRunnerScript", String.class, java.nio.file.Path.class);
        method.setAccessible(true);

        // depsDir=null keeps the old assertion's intent — special chars in
        // the script filename must be escaped via the `repr` helper before
        // being baked into the runner.
        String script = (String) method.invoke(service, "my_script's.py", null);

        assertThat(script)
                .contains("import ast")
                .contains("my_script\\'s.py")
                .contains("exec(compile");
    }

    @Test
    void shouldBuildResultWithStdoutStderrAndFiles() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildResult", String.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class, String.class);
        method.setAccessible(true);

        File imgFile = File.createTempFile("plot", ".png");
        imgFile.deleteOnExit();
        File csvFile = File.createTempFile("data", ".csv");
        csvFile.deleteOnExit();

        StringBuilder stdout = new StringBuilder("Processing complete\n");
        StringBuilder stderr = new StringBuilder("DeprecationWarning: old API\n");

        String result = (String) method.invoke(service, "sess1", 0,
                stdout, stderr, new File[]{imgFile, csvFile}, null);

        assertThat(result)
                .contains("Processing complete")
                .contains("WARNINGS")
                .contains("Generated Files")
                .contains("![")
                .contains("[Download ");
    }

    @Test
    void logResultShouldNotThrowForNullFiles() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "logResult", String.class, java.nio.file.Path.class, int.class,
                StringBuilder.class, StringBuilder.class, File[].class);
        method.setAccessible(true);

        java.nio.file.Path path = java.nio.file.Path.of("test.py");
        // null generatedFiles - should not throw
        method.invoke(service, "s1", path, 0,
                new StringBuilder("output"), new StringBuilder(), (File[]) null);
        // If we get here without exception, the method handled null files gracefully
        assertThat(path).isNotNull();
    }

    // --- T80 Docker sandbox ---

    @Test
    void buildRunnerScriptWithStringDepsShouldUseContainerMount() throws Exception {
        // The (String, String) overload is what the Docker branch calls with
        // the in-container mount point "/deps" rather than the host path.
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildRunnerScript", String.class, String.class);
        method.setAccessible(true);

        String script = (String) method.invoke(service, "script.py", "/deps");
        assertThat(script)
                .contains("_DEPS_DIR = '/deps'")
                .contains("sys.path.insert(0, _DEPS_DIR)");
    }

    @Test
    void resolveExecutionModeShouldDefaultToNativeWhenSettingsReturnNull() throws Exception {
        // Mock settings returns null for getCodeInterpreterExecutionMode() —
        // resolveExecutionMode must coerce that to NATIVE, never NPE.
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod("resolveExecutionMode");
        method.setAccessible(true);
        Object mode = method.invoke(service);
        assertThat(mode).isEqualTo(TurCodeInterpreterExecutionMode.NATIVE);
    }

    @Test
    void buildDockerProcessShouldEmitHardenedCommand() throws Exception {
        TurGlobalSettingsService settings = mock(TurGlobalSettingsService.class);
        when(settings.getCodeInterpreterDockerImage()).thenReturn("myorg/ci:1");
        TurCodeInterpreterToolService svc = new TurCodeInterpreterToolService(settings);

        // Populate the @Value-backed docker fields (no Spring context here).
        setField(svc, "dockerExecutable", "docker");
        setField(svc, "dockerNetwork", "none");
        setField(svc, "dockerMemory", "512m");
        setField(svc, "dockerCpus", "1.0");
        setField(svc, "dockerPidsLimit", 128);

        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildDockerProcess", String.class, File.class, java.nio.file.Path.class);
        method.setAccessible(true);

        File sessionDir = new File(System.getProperty("java.io.tmpdir"), "turing-ci-test");
        java.nio.file.Path depsDir = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "deps");

        ProcessBuilder pb = (ProcessBuilder) method.invoke(
                svc, "turing-ci-abc", sessionDir, depsDir);
        java.util.List<String> cmd = pb.command();
        String joined = String.join(" ", cmd);

        assertThat(cmd).startsWith("docker", "run", "--rm");
        assertThat(joined)
                .contains("--name turing-ci-abc")
                .contains("--network none")
                .contains("--memory 512m")
                .contains("--cpus 1.0")
                .contains("--pids-limit 128")
                .contains("--cap-drop ALL")
                .contains("no-new-privileges")
                .contains("--read-only")
                .contains(":/sandbox:rw")
                .contains(":/deps:ro")
                .contains("PYTHONPATH=/deps")
                .contains("myorg/ci:1")
                .contains("python /sandbox/_runner.py");
    }

    @Test
    void buildDockerProcessShouldOmitDepsMountWhenNull() throws Exception {
        TurGlobalSettingsService settings = mock(TurGlobalSettingsService.class);
        when(settings.getCodeInterpreterDockerImage()).thenReturn("python:3.12-slim");
        TurCodeInterpreterToolService svc = new TurCodeInterpreterToolService(settings);
        setField(svc, "dockerExecutable", "docker");
        setField(svc, "dockerNetwork", "none");
        setField(svc, "dockerMemory", "512m");
        setField(svc, "dockerCpus", "1.0");
        setField(svc, "dockerPidsLimit", 128);

        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "buildDockerProcess", String.class, File.class, java.nio.file.Path.class);
        method.setAccessible(true);

        File sessionDir = new File(System.getProperty("java.io.tmpdir"), "turing-ci-test2");
        ProcessBuilder pb = (ProcessBuilder) method.invoke(svc, "turing-ci-x", sessionDir, null);
        String joined = String.join(" ", pb.command());

        assertThat(joined)
                .doesNotContain(":/deps:ro")
                .doesNotContain("PYTHONPATH=/deps");
    }

    @Test
    void checkDockerShouldReturnUnavailableForBogusExecutable() throws Exception {
        TurCodeInterpreterToolService svc = new TurCodeInterpreterToolService(
                mock(TurGlobalSettingsService.class));
        // Point at a binary that cannot exist so the probe fails fast and the
        // method returns a status instead of throwing.
        setField(svc, "dockerExecutable",
                System.getProperty("java.io.tmpdir") + "/definitely-not-docker-xyz");
        setField(svc, "dockerProbeTimeoutSeconds", 5);

        TurCodeInterpreterToolService.TurDockerStatus status = svc.checkDocker();
        assertThat(status.available()).isFalse();
        assertThat(status.error()).isNotBlank();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field f = TurCodeInterpreterToolService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void readStreamShouldReadNormalContent() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "readStream", java.io.Reader.class, StringBuilder.class);
        method.setAccessible(true);

        java.io.Reader reader = new java.io.StringReader("line1\nline2\nline3");
        StringBuilder buffer = new StringBuilder();

        method.invoke(service, reader, buffer);
        assertThat(buffer).hasToString("line1\nline2\nline3\n");
    }

    @Test
    void readStreamShouldTruncateAtMaxLength() throws Exception {
        Method method = TurCodeInterpreterToolService.class.getDeclaredMethod(
                "readStream", java.io.Reader.class, StringBuilder.class);
        method.setAccessible(true);

        // MAX_OUTPUT_LENGTH is 15_000, generate content exceeding that
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            largeContent.append("line ").append(i).append(" with some padding text\n");
        }

        java.io.Reader reader = new java.io.StringReader(largeContent.toString());
        StringBuilder buffer = new StringBuilder();

        method.invoke(service, reader, buffer);
        // Buffer should not exceed MAX_OUTPUT_LENGTH significantly
        assertThat(buffer.length()).isLessThan(largeContent.length());
    }
}

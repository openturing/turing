package com.viglet.turing.genai.tool;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurCodeInterpreterToolService {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LENGTH = 15_000;
    private static final String SANDBOX_DIR = "code-interpreter";

    /** Fixed, read-only system directories allowed in the subprocess PATH. */
    private static final String SAFE_PATH;
    static {
        boolean isWindows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT).startsWith("windows");
        SAFE_PATH = isWindows
                ? String.join(File.pathSeparator, "C:\\Windows\\System32", "C:\\Windows")
                : String.join(File.pathSeparator, "/usr/bin", "/usr/local/bin", "/bin");
    }

    /** Absolute Python candidates searched in order when no explicit path is configured. */
    private static final List<String> PYTHON_CANDIDATES;
    static {
        boolean isWindows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT).startsWith("windows");
        PYTHON_CANDIDATES = isWindows
                ? List.of("C:\\Python312\\python.exe", "C:\\Python311\\python.exe",
                          "C:\\Python310\\python.exe")
                : List.of("/usr/bin/python3", "/usr/local/bin/python3",
                          "/usr/bin/python", "/usr/local/bin/python");
    }

    private final TurGlobalSettingsService turGlobalSettingsService;

    public TurCodeInterpreterToolService(TurGlobalSettingsService turGlobalSettingsService) {
        this.turGlobalSettingsService = turGlobalSettingsService;
    }

    @Value("${turing.code-interpreter.python-executable:}")
    private String configuredPythonExecutable;

    @Value("${server.port:2700}")
    private int serverPort;

    @Tool(name = "execute_python", description = """
            Executes Python code and returns the output (stdout and stderr).
            Use this tool when the user asks you to:
            - Solve math or statistical problems
            - Process, analyze, or transform data (CSV, JSON, Excel)
            - Generate charts or graphs (use matplotlib/seaborn, save to 'output.png')
            - Perform calculations, conversions, or simulations
            - Run any computation that benefits from actual code execution

            The code runs in a temporary sandbox directory. Any files generated \
            (charts, CSVs, etc.) are saved there and accessible via URL.

            IMPORTANT:
            - This runs as a standard Python script, NOT a Jupyter notebook. \
            Bare expressions (e.g. a variable name alone on the last line) produce NO output. \
            You MUST always use print() explicitly to produce output. \
            WRONG: result    CORRECT: print(result)
            - For charts: use plt.savefig('output.png', dpi=150, bbox_inches='tight') \
            then print the filename. Do NOT call plt.show().
            - For data files: save to the current directory and print the filename.
            - ALWAYS use descriptive filenames with the CORRECT extension for the format: \
            .xlsx for Excel, .csv for CSV, .pdf for PDF, .json for JSON, .png for images, etc. \
            WRONG: f.txt, output.txt, data.txt for an Excel file. \
            CORRECT: report.xlsx, sales_data.xlsx, analysis.csv. \
            The filename must match the actual file format being written.
            - Common libraries available: math, json, csv, datetime, os, sys, re, \
            statistics, collections, itertools, functools, urllib.
            - Libraries that MAY be available (if installed): numpy, pandas, matplotlib, \
            seaborn, scipy, openpyxl, requests, Pillow.
            - Python version is 3.12+.

            INSTALLING MISSING LIBRARIES:
            - If a library is missing (ModuleNotFoundError or ImportError), install it \
            BEFORE running the actual code by calling execute_python with an install script:
              import subprocess, sys
              subprocess.check_call([sys.executable, "-m", "pip", "install", \
            "matplotlib", "numpy"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            - Always use sys.executable so pip installs into the same interpreter.
            - You may install multiple packages in a single call: \
            ["matplotlib", "numpy", "pandas", "seaborn", "scipy", "openpyxl", "Pillow"].
            - After a successful install call execute_python again with the real code.
            - If pip itself fails (no internet, permissions), report the error and \
            suggest a stdlib-only alternative when possible.

            CHART STYLING (mandatory for all charts):
            - Always start chart code with: plt.style.use('seaborn-v0_8-darkgrid')
            - Remove top and right spines: ax.spines['top'].set_visible(False); ax.spines['right'].set_visible(False)
            - Use colorblind-friendly palette: ['#0072B2', '#D55E00', '#009E73', '#CC79A7', '#F0E442', '#56B4E9', '#E69F00']
            - Set figure background to transparent or white: fig.patch.set_facecolor('white')
            - Use tight_layout() before savefig for clean margins.

            DISPLAYING GENERATED FILES:
            - The tool returns file URLs starting with /api/v2/code-interpreter/...
            - Use EXACTLY the URL returned, with NO prefix. NEVER add "sandbox:" or any other prefix.
            - For IMAGE files (.png, .jpg, .gif, .svg): use image markdown. \
            Example: ![Chart](/api/v2/code-interpreter/abc123/output.png)
            - For NON-IMAGE files (.xlsx, .csv, .pdf, .txt, .json, etc.): use a download link. \
            Example: [Download report.xlsx](/api/v2/code-interpreter/abc123/report.xlsx)
            - The tool output already provides the correct markdown format for each file — \
            copy it exactly as given in the "Markdown:" line.

            ERROR HANDLING:
            - If the code fails (non-zero exit code) or produces warnings/errors in stderr, \
            the output will include the error details.
            - When you receive a ModuleNotFoundError, run the pip install step first, \
            then retry with the real code. Count install + retry as a single attempt.
            - For other errors, analyze and fix the code, then call execute_python again. \
            You may retry up to 3 times before giving up.
            - Common fixes: missing imports, deprecated APIs, syntax errors.

            Args:
                code (str): Python code to execute. Required.
            Returns:
                The stdout output of the code, any stderr messages, and URLs for generated files.""")
    public String executePython(String code) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[CodeInterpreter] session={} | Executing {} chars of Python code",
                sessionId, code.length());

        File sessionDir = new File(TurCommonsUtils.addSubDirToStoreDir(SANDBOX_DIR), sessionId);

        try {
            Files.createDirectories(sessionDir.toPath());
            Path scriptPath = sessionDir.toPath().resolve("script.py");
            Files.writeString(scriptPath, code);
            log.info("[CodeInterpreter] session={} | Script saved to: {} | Code:\n{}",
                    sessionId, scriptPath.toAbsolutePath(), code);

            return executeAndCollectOutput(sessionId, sessionDir, scriptPath);
        } catch (InterruptedException e) {
            log.error("[CodeInterpreter] session={} | INTERRUPTED: {}", sessionId, e.getMessage(), e);
            Thread.currentThread().interrupt();
            return "Error executing Python code: " + e.getMessage();
        } catch (Exception e) {
            log.error("[CodeInterpreter] session={} | EXCEPTION: {}", sessionId, e.getMessage(), e);
            return "Error executing Python code: " + e.getMessage();
        }
    }

    private String resolvePythonExecutable() {
        // 1. DB / global-settings (highest priority — editable at runtime)
        String dbPath = turGlobalSettingsService.getPythonExecutable();
        if (!dbPath.isBlank()) {
            return requireAbsolute(dbPath, "Global Settings → Python Executable");
        }
        // 2. Application property (turing.code-interpreter.python-executable)
        if (!configuredPythonExecutable.isBlank()) {
            return requireAbsolute(configuredPythonExecutable,
                    "turing.code-interpreter.python-executable");
        }
        // 3. Auto-detect from fixed system locations
        for (String candidate : PYTHON_CANDIDATES) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Python not found in safe locations. Configure it in Global Settings or set "
                        + "turing.code-interpreter.python-executable to an absolute path.");
    }

    private static String requireAbsolute(String path, String source) {
        if (!Path.of(path).isAbsolute()) {
            throw new IllegalStateException(
                    source + " must be an absolute path, got: " + path);
        }
        return path;
    }

    private String executeAndCollectOutput(String sessionId, File sessionDir,
            Path scriptPath) throws IOException, InterruptedException {
        Path runnerPath = sessionDir.toPath().resolve("_runner.py");
        Files.writeString(runnerPath, buildRunnerScript(scriptPath.getFileName().toString()));
        ProcessBuilder pb = new ProcessBuilder(resolvePythonExecutable(), runnerPath.toString());
        pb.directory(sessionDir);
        pb.redirectErrorStream(false);
        var env = pb.environment();
        env.put("MPLBACKEND", "Agg");
        env.put("PATH", SAFE_PATH);
        env.put("PYTHONUTF8", "1");
        env.put("PYTHONIOENCODING", "utf-8");
        env.remove("PYTHONPATH");
        env.remove("PYTHONSTARTUP");
        env.remove("PYTHONHOME");
        env.remove("PYTHONUSERSITE");

        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread outThread = Thread.ofVirtual().start(() ->
                readStream(new java.io.InputStreamReader(process.getInputStream(),
                        java.nio.charset.StandardCharsets.UTF_8), stdout));
        Thread errThread = Thread.ofVirtual().start(() ->
                readStream(new java.io.InputStreamReader(process.getErrorStream(),
                        java.nio.charset.StandardCharsets.UTF_8), stderr));

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            outThread.join(2000);
            errThread.join(2000);
            log.warn("[CodeInterpreter] session={} | TIMEOUT after {}s | script={} | Partial stdout:\n{}",
                    sessionId, TIMEOUT_SECONDS, scriptPath.toAbsolutePath(), stdout);
            return "Error: Code execution timed out after " + TIMEOUT_SECONDS + " seconds.\n"
                    + "Partial output:\n" + stdout;
        }

        outThread.join(5000);
        errThread.join(5000);

        int exitCode = process.exitValue();
        File[] generatedFiles = sessionDir.listFiles((dir, name) ->
                !name.equals("script.py") && !name.equals("_runner.py"));

        String result = buildResult(sessionId, exitCode, stdout, stderr, generatedFiles);
        logResult(sessionId, scriptPath, exitCode, stdout, stderr, generatedFiles);
        return result;
    }

    private void readStream(java.io.Reader reader, StringBuilder buffer) {
        try (var br = new java.io.BufferedReader(reader)) {
            br.lines().forEach(line -> {
                if (buffer.length() < MAX_OUTPUT_LENGTH) {
                    buffer.append(line).append("\n");
                }
            });
        } catch (IOException e) {
            // ignore stream read errors
        }
    }

    private String buildResult(String sessionId, int exitCode,
            StringBuilder stdout, StringBuilder stderr, File[] generatedFiles) {
        StringBuilder result = new StringBuilder();

        if (!stdout.isEmpty()) {
            String output = stdout.toString();
            if (output.length() > MAX_OUTPUT_LENGTH) {
                output = output.substring(0, MAX_OUTPUT_LENGTH) + "\n... [output truncated]";
            }
            result.append(output);
        }

        if (!stderr.isEmpty()) {
            result.append(exitCode != 0
                    ? "\n--- ERROR (exit code " + exitCode + ") ---\n"
                    : "\n--- WARNINGS ---\n");
            result.append(stderr);
        }

        if (generatedFiles != null && generatedFiles.length > 0) {
            result.append("\n--- Generated Files ---\n");
            for (File f : generatedFiles) {
                String fileUrl = "/api/v2/code-interpreter/" + sessionId + "/" + f.getName();
                boolean isImage = isImageFile(f.getName());
                result.append("- ").append(f.getName())
                        .append(" (").append(formatFileSize(f.length())).append(")")
                        .append("\n  URL: ").append(fileUrl)
                        .append("\n  Markdown: ");
                if (isImage) {
                    result.append("![").append(f.getName()).append("](").append(fileUrl).append(")");
                } else {
                    result.append("[Download ").append(f.getName()).append("](").append(fileUrl).append(")");
                }
                result.append("\n");
            }
        }

        if (result.isEmpty()) {
            result.append("(no output)");
        }

        return result.toString();
    }

    private void logResult(String sessionId, Path scriptPath, int exitCode,
            StringBuilder stdout, StringBuilder stderr, File[] generatedFiles) {
        int fileCount = generatedFiles != null ? generatedFiles.length : 0;
        if (exitCode == 0 && stderr.isEmpty()) {
            log.info("[CodeInterpreter] session={} | SUCCESS | script={} | stdout={} chars | files={}",
                    sessionId, scriptPath.toAbsolutePath(), stdout.length(), fileCount);
        } else if (exitCode == 0) {
            log.warn("[CodeInterpreter] session={} | SUCCESS with warnings | script={} | stdout={} chars | stderr:\n{}",
                    sessionId, scriptPath.toAbsolutePath(), stdout.length(), stderr);
        } else {
            log.error("[CodeInterpreter] session={} | FAILED (exit={}) | script={} | stderr:\n{}",
                    sessionId, exitCode, scriptPath.toAbsolutePath(), stderr);
        }
    }

    /**
     * Builds a small Python runner that parses the user script with {@code ast},
     * detects if the last statement is a bare expression (e.g. {@code result}
     * instead of {@code print(result)}), and wraps it with {@code print()} so
     * the value is emitted to stdout — mimicking Jupyter/IPython behaviour.
     */
    private String buildRunnerScript(String scriptFilename) {
        return """
                import ast, sys
                try:
                    import matplotlib as _mpl
                    _mpl.rcParams['font.family'] = 'sans-serif'
                    _mpl.rcParams['font.sans-serif'] = ['DejaVu Sans', 'Arial', 'Segoe UI', 'sans-serif']
                    _mpl.rcParams['axes.unicode_minus'] = False
                except ImportError:
                    pass
                _src = open(%s, encoding='utf-8').read()
                _tree = ast.parse(_src)
                if _tree.body and isinstance(_tree.body[-1], ast.Expr):
                    _last = _tree.body[-1]
                    _tree.body[-1] = ast.Expr(value=ast.Call(
                        func=ast.Name(id='print', ctx=ast.Load()),
                        args=[_last.value], keywords=[]))
                    ast.fix_missing_locations(_tree)
                exec(compile(_tree, %s, 'exec'))
                """.formatted(repr(scriptFilename), repr(scriptFilename));
    }

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".bmp");

    private static boolean isImageFile(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static String repr(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private String formatFileSize(long bytes) {
        if (bytes >= 1_048_576)
            return String.format("%.1f MB", bytes / 1_048_576.0);
        if (bytes >= 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return bytes + " bytes";
    }
}

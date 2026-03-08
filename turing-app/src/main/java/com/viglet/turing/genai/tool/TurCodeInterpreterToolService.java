package com.viglet.turing.genai.tool;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.viglet.turing.commons.utils.TurCommonsUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurCodeInterpreterToolService {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LENGTH = 15_000;
    private static final String SANDBOX_DIR = "code-interpreter";

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
            - Use print() to produce output that will be returned to you.
            - For charts: use plt.savefig('output.png', dpi=150, bbox_inches='tight') \
            then print the filename. Do NOT call plt.show().
            - For data files: save to the current directory and print the filename.
            - Common libraries available: math, json, csv, datetime, os, sys, re, \
            statistics, collections, itertools, functools, urllib.
            - Libraries that MAY be available (if installed): numpy, pandas, matplotlib, \
            seaborn, scipy, openpyxl, requests, Pillow.
            - If a library is not available, the error will tell you. Try an alternative.
            - Python version is 3.12+.

            CHART STYLING (mandatory for all charts):
            - Always start chart code with: plt.style.use('seaborn-v0_8-darkgrid')
            - Remove top and right spines: ax.spines['top'].set_visible(False); ax.spines['right'].set_visible(False)
            - Use colorblind-friendly palette: ['#0072B2', '#D55E00', '#009E73', '#CC79A7', '#F0E442', '#56B4E9', '#E69F00']
            - Set figure background to transparent or white: fig.patch.set_facecolor('white')
            - Use tight_layout() before savefig for clean margins.

            DISPLAYING GENERATED FILES:
            - The tool returns file URLs starting with /api/v2/code-interpreter/...
            - To display an image, use EXACTLY the URL returned, with NO prefix. \
            Example: ![Chart](/api/v2/code-interpreter/abc123/output.png)
            - NEVER add "sandbox:" or any other prefix to the URL.

            ERROR HANDLING:
            - If the code fails (non-zero exit code) or produces warnings/errors in stderr, \
            the output will include the error details.
            - When you receive an error, analyze it, fix the code, and call execute_python again \
            with the corrected version. You may retry up to 3 times before giving up.
            - Common fixes: missing imports, deprecated APIs, syntax errors, missing libraries \
            (try an alternative).

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

    private String executeAndCollectOutput(String sessionId, File sessionDir,
            Path scriptPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("python", scriptPath.toString());
        pb.directory(sessionDir);
        pb.redirectErrorStream(false);
        pb.environment().put("MPLBACKEND", "Agg");

        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread outThread = Thread.ofVirtual().start(() -> readStream(process.inputReader(), stdout));
        Thread errThread = Thread.ofVirtual().start(() -> readStream(process.errorReader(), stderr));

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
        File[] generatedFiles = sessionDir.listFiles((dir, name) -> !name.equals("script.py"));

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
                result.append("- ").append(f.getName())
                        .append(" (").append(formatFileSize(f.length())).append(")")
                        .append("\n  URL: ").append(fileUrl)
                        .append("\n  Markdown: ![").append(f.getName()).append("](").append(fileUrl).append(")\n");
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

    private String formatFileSize(long bytes) {
        if (bytes >= 1_048_576)
            return String.format("%.1f MB", bytes / 1_048_576.0);
        if (bytes >= 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return bytes + " bytes";
    }
}

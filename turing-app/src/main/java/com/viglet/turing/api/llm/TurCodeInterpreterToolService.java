package com.viglet.turing.api.llm;

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

            DISPLAYING GENERATED FILES:
            - The tool returns file URLs starting with /api/v2/code-interpreter/...
            - To display an image, use EXACTLY the URL returned, with NO prefix. \
            Example: ![Chart](/api/v2/code-interpreter/abc123/output.png)
            - NEVER add "sandbox:" or any other prefix to the URL.

            Args:
                code (str): Python code to execute. Required.
            Returns:
                The stdout output of the code, any stderr messages, and URLs for generated files.""")
    public String executePython(String code) {
        log.info("[CodeInterpreter Tool] execute_python called with {} chars of code", code.length());

        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        File sandboxRoot = TurCommonsUtils.addSubDirToStoreDir(SANDBOX_DIR);
        File sessionDir = new File(sandboxRoot, sessionId);

        try {
            Files.createDirectories(sessionDir.toPath());

            // Write the Python script
            Path scriptPath = sessionDir.toPath().resolve("script.py");
            Files.writeString(scriptPath, code);

            // Execute Python
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath.toString());
            pb.directory(sessionDir);
            pb.redirectErrorStream(false);
            pb.environment().put("MPLBACKEND", "Agg"); // matplotlib non-interactive backend

            Process process = pb.start();

            // Read stdout and stderr in separate threads to avoid deadlock
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread outThread = Thread.ofVirtual().start(() -> {
                try (var reader = process.inputReader()) {
                    reader.lines().forEach(line -> {
                        if (stdout.length() < MAX_OUTPUT_LENGTH) {
                            stdout.append(line).append("\n");
                        }
                    });
                } catch (IOException e) {
                    // ignore
                }
            });

            Thread errThread = Thread.ofVirtual().start(() -> {
                try (var reader = process.errorReader()) {
                    reader.lines().forEach(line -> {
                        if (stderr.length() < MAX_OUTPUT_LENGTH) {
                            stderr.append(line).append("\n");
                        }
                    });
                } catch (IOException e) {
                    // ignore
                }
            });

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                outThread.join(2000);
                errThread.join(2000);
                return "Error: Code execution timed out after " + TIMEOUT_SECONDS + " seconds.\n"
                        + "Partial output:\n" + stdout;
            }

            outThread.join(5000);
            errThread.join(5000);

            int exitCode = process.exitValue();

            StringBuilder result = new StringBuilder();

            if (stdout.length() > 0) {
                String output = stdout.toString();
                if (output.length() > MAX_OUTPUT_LENGTH) {
                    output = output.substring(0, MAX_OUTPUT_LENGTH) + "\n... [output truncated]";
                }
                result.append(output);
            }

            if (exitCode != 0 && stderr.length() > 0) {
                result.append("\n--- STDERR (exit code ").append(exitCode).append(") ---\n");
                result.append(stderr);
            }

            // Check for generated files
            File[] generatedFiles = sessionDir.listFiles(
                    (dir, name) -> !name.equals("script.py"));
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

            String output = result.toString();
            log.info("[CodeInterpreter Tool] execute_python: exit={}, output={} chars, session={}",
                    exitCode, output.length(), sessionId);
            return output;

        } catch (Exception e) {
            log.error("[CodeInterpreter Tool] execute_python failed: {}", e.getMessage(), e);
            return "Error executing Python code: " + e.getMessage();
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes >= 1_048_576) return String.format("%.1f MB", bytes / 1_048_576.0);
        if (bytes >= 1024) return String.format("%.1f KB", bytes / 1024.0);
        return bytes + " bytes";
    }
}

package com.viglet.turing.genai.tool;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
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
    /**
     * Subdir of {@link #SANDBOX_DIR} where ephemeral per-call session
     * directories live WHEN no tenant context is available (LLM-direct
     * {@code execute_python} calls, unit tests). Kept as a fallback for
     * back-compat with pre-2026.2.7 URLs.
     */
    static final String SESSIONS_DIR = "sessions";
    /**
     * Subdir of {@link #SANDBOX_DIR} where tenant-isolated session dirs
     * live: {@code tenants/{agentId}/{conversationId}/YYYY-MM-DD/{sessionId}/}.
     * Used whenever both agent + conversation context are known (which
     * is the common path in production — Custom Tools always run inside
     * a chat turn). Path-level isolation lets the cleanup task scope
     * deletes by tenant and lets future hardening (signed URLs, cookie
     * auth) verify the caller owns the session.
     *
     * <p><b>Important:</b> this is OPERATIONAL isolation, NOT a runtime
     * security boundary. A malicious Python script still runs as the
     * JVM user and can {@code open("../../other-tenant/file")}. Proper
     * runtime isolation needs containerization (Docker / gVisor /
     * Firecracker) — separate roadmap.
     */
    static final String TENANTS_DIR = "tenants";

    /** Fixed, read-only system directories allowed in the subprocess PATH. */
    private static final String SAFE_PATH;
    static {
        boolean isWindows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT).startsWith("windows");
        SAFE_PATH = isWindows
                ? String.join(File.pathSeparator, "C:\\Windows\\System32", "C:\\Windows")
                : String.join(File.pathSeparator, "/usr/bin", "/usr/local/bin", "/bin");
    }

    /**
     * Absolute Python candidates searched in order when no explicit path is
     * configured.
     */
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
    private final TurCustomToolDependencyService dependencyService;
    private final TurCodeInterpreterUrlSigner urlSigner;

    /**
     * Test-friendly constructor — wires the sandbox without auto-install
     * and without URL signing. Equivalent to
     * {@code new TurCodeInterpreterToolService(settings, null, null)}; URLs
     * are emitted unsigned (legacy behavior) and PYTHONPATH stays unset.
     */
    public TurCodeInterpreterToolService(TurGlobalSettingsService turGlobalSettingsService) {
        this(turGlobalSettingsService, null, null);
    }

    public TurCodeInterpreterToolService(TurGlobalSettingsService turGlobalSettingsService,
            TurCustomToolDependencyService dependencyService) {
        this(turGlobalSettingsService, dependencyService, null);
    }

    // Mark the full-args ctor as the one Spring should use for autowiring.
    // With 3 public constructors none of them is the obvious "longest
    // satisfiable" — Spring falls back to the no-arg ctor (which doesn't
    // exist) and the whole context fails to load. Annotation pins the choice.
    @Autowired
    public TurCodeInterpreterToolService(TurGlobalSettingsService turGlobalSettingsService,
            TurCustomToolDependencyService dependencyService,
            TurCodeInterpreterUrlSigner urlSigner) {
        this.turGlobalSettingsService = turGlobalSettingsService;
        this.dependencyService = dependencyService;
        this.urlSigner = urlSigner;
    }

    @Value("${turing.code-interpreter.python-executable:}")
    private String configuredPythonExecutable;

    @Value("${server.port:2700}")
    private int serverPort;

    /**
     * Concurrency cap on simultaneous Python subprocesses. A burst of
     * 100 visitors clicking "Baixar PDF" at once otherwise spawns 100
     * Python procs (each ~50-100 MB RSS for reportlab) and OOMs the
     * host. The semaphore queues requests above the cap and fails
     * fast after a short timeout — the caller (typically Marina/Lucas
     * in the chat) surfaces the error to the visitor with a "try again
     * in a moment" message.
     *
     * <p>Default {@code Runtime.availableProcessors()} (matches the
     * sweet-spot for CPU-bound work like reportlab/matplotlib);
     * override via {@code turing.code-interpreter.max-concurrent} for
     * environments where Python tools are mostly I/O-bound (higher
     * cap fine) or memory-constrained (lower cap).
     */
    @Value("${turing.code-interpreter.max-concurrent:#{T(java.lang.Runtime).getRuntime().availableProcessors()}}")
    private int maxConcurrent;

    @Value("${turing.code-interpreter.acquire-timeout-ms:2000}")
    private long acquireTimeoutMs;

    /**
     * Lazy-initialized semaphore — the @Value field {@link #maxConcurrent}
     * is populated AFTER the constructor runs, so we can't size the
     * semaphore at construction time. {@link AtomicReference} keeps the
     * lazy init thread-safe without synchronization on the hot path.
     */
    private final AtomicReference<Semaphore> concurrencyLimiter = new AtomicReference<>();

    /** Per-call carrier for the agent's pythonRequirements addendum. */
    private final ThreadLocal<String> extraRequirements = new ThreadLocal<>();
    /**
     * Per-call carrier for the active agent id. When set with
     * {@link #tenantConversationId} the session dir lands under
     * {@code tenants/{agentId}/{conversationId}/YYYY-MM-DD/{sid}/} —
     * path-isolated from other tenants. Null when called outside a
     * Custom Tool context (LLM-direct {@code execute_python}), in which
     * case the legacy flat layout under {@code sessions/} is used.
     */
    private final ThreadLocal<String> tenantAgentId = new ThreadLocal<>();
    private final ThreadLocal<String> tenantConversationId = new ThreadLocal<>();

    @Tool(name = "execute_python", description = ".")
    public String executePython(String code) {
        return executePythonWithExtraRequirements(code, null);
    }

    /**
     * Tenant-aware overload — used by
     * {@link TurCustomToolCodeHelper} when both agent + conversation
     * context are available. Routes the session dir under
     * {@code tenants/{agentId}/{conversationId}/...} so cleanup and
     * future authorization layers can scope by tenant.
     *
     * <p>Either id can be null/blank — in which case the legacy
     * {@code sessions/} layout is used (preserving back-compat with
     * pre-2026.2.7 invocations).
     */
    public String executePythonForTenant(String code, String extraReqs,
            String agentId, String conversationId) {
        tenantAgentId.set(sanitizePathSegment(agentId));
        tenantConversationId.set(sanitizePathSegment(conversationId));
        try {
            return executePythonWithExtraRequirements(code, extraReqs);
        } finally {
            tenantAgentId.remove();
            tenantConversationId.remove();
        }
    }

    /**
     * Strips any path-traversal danger from server-generated ids before
     * they're appended to a filesystem path. UUIDs in the codebase use
     * {@code [a-zA-Z0-9-]} only, so anything outside that set is either
     * adversarial or an upstream bug — coerce to a safe form.
     *
     * <p>Defense in depth: agent/conversation ids are minted by Turing
     * (never user-controlled), but a future API tweak could relax the
     * format. Doing the sanitize here guards against the regression.
     */
    static String sanitizePathSegment(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // Keep only safe filename characters.
        String safe = raw.replaceAll("[^a-zA-Z0-9._-]", "");
        if (safe.isEmpty()) return null;
        // Reject path-traversal sentinels that survive the char filter:
        // `.` (current dir) and `..` (parent dir). Without this check, a
        // raw id like "../etc" reduces to "..etc" (OK) but "../" reduces
        // to ".." which `new File(parent, "..")` resolves to the parent
        // → cross-tenant traversal. Same for "." which is a no-op segment
        // but lets an adversary land in their own dir's parent on the
        // next level.
        if (".".equals(safe) || "..".equals(safe)) return null;
        return safe;
    }

    /**
     * Same as {@link #executePython(String)} but with an agent-specific
     * {@code requirements.txt} addendum that gets unioned with the global
     * {@code GLOBAL_PYTHON_REQUIREMENTS} setting before pip installs.
     *
     * <p>Called by {@link TurCustomToolCodeHelper} when a Groovy Custom
     * Tool invokes {@code code.executePython(script)} — the helper
     * carries the agent's {@code pythonRequirements} from
     * {@link TurCustomToolCallbackService}'s wiring of the helper at
     * binding time, so per-agent Python envs work end-to-end without
     * threading state through the {@code @Tool} method signature (which
     * would alter the LLM-facing schema).
     *
     * @param code Python source code to execute.
     * @param extraReqs agent-specific {@code requirements.txt} addendum,
     *                  or null/blank when only the global env is needed.
     */
    public String executePythonWithExtraRequirements(String code, String extraReqs) {
        // Concurrency gate: cap simultaneous Python subprocesses so a
        // 100-visitor burst doesn't fork 100 procs and OOM the host.
        // tryAcquire with a short timeout — if capacity is exhausted
        // longer than 2s the caller gets a fail-fast error rather than
        // blocking the whole chat turn indefinitely.
        Semaphore limiter = resolveLimiter();
        boolean acquired;
        try {
            acquired = limiter.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: code interpreter wait interrupted";
        }
        if (!acquired) {
            log.warn("[CodeInterpreter] concurrency exhausted (cap={} available=0); rejecting call",
                    maxConcurrent);
            return "Error: code interpreter is at capacity right now — please try again in a moment.";
        }
        try {
            return executePythonInternal(code, extraReqs);
        } finally {
            limiter.release();
        }
    }

    private Semaphore resolveLimiter() {
        Semaphore existing = concurrencyLimiter.get();
        if (existing != null) return existing;
        int permits = Math.max(1, maxConcurrent);
        Semaphore created = new Semaphore(permits, true); // fair = FIFO
        if (concurrencyLimiter.compareAndSet(null, created)) return created;
        return concurrencyLimiter.get();
    }

    private String executePythonInternal(String code, String extraReqs) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[CodeInterpreter] session={} | Executing {} chars of Python code (agentExtras={})",
                sessionId, code.length(), (extraReqs == null || extraReqs.isBlank()) ? "no" : "yes");

        // Tenant-aware date-bucketed session layout:
        //   - With tenant: tenants/{agentId}/{conversationId}/YYYY-MM-DD/{sessionId}/
        //   - Without (LLM-direct @Tool, unit tests): legacy sessions/YYYY-MM-DD/{sessionId}/
        // Path-level isolation lets cleanup scope by tenant and lets future
        // hardening (signed URLs, cookie auth) verify ownership. URLs stay
        // /api/v2/code-interpreter/{sessionId}/{file} — the file API
        // walks both layouts to resolve at serve time.
        String dateBucket = java.time.LocalDate.now().toString();
        String agentId = tenantAgentId.get();
        String convId = tenantConversationId.get();
        File sessionDir;
        if (agentId != null && convId != null) {
            File tenantsRoot = TurCommonsUtils.addSubDirToStoreDir(
                    SANDBOX_DIR + "/" + TENANTS_DIR);
            sessionDir = new File(new File(new File(
                    new File(tenantsRoot, agentId), convId), dateBucket), sessionId);
            log.debug("[CodeInterpreter] session={} | tenant-scoped dir agentId={} convId={}",
                    sessionId, agentId, convId);
        } else {
            File sessionsRoot = TurCommonsUtils.addSubDirToStoreDir(
                    SANDBOX_DIR + "/" + SESSIONS_DIR);
            sessionDir = new File(new File(sessionsRoot, dateBucket), sessionId);
        }
        extraRequirements.set(extraReqs);
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
        } finally {
            // Mandatory: clear the ThreadLocal so virtual-thread pools don't
            // leak the value into unrelated tool invocations.
            extraRequirements.remove();
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
        // Resolve auto-installed deps BEFORE building the env. The dep
        // service unions the global `GLOBAL_PYTHON_REQUIREMENTS` setting
        // with the optional agent-specific addendum (carried via the
        // ThreadLocal set by the public entry point) — hash-keyed cache
        // dir on first call, instant on subsequent (cache hit). When
        // both sources are blank, returns null → no PYTHONPATH injection
        // and behavior matches the pre-2026.2.7 era (operator manages
        // the Python env manually). Failures propagate as
        // DependencyInstallException → caught by the outer try/catch and
        // surfaced as the script's error message.
        Path depsDir = dependencyService.ensureInstalled(extraRequirements.get());
        ProcessBuilder pb = new ProcessBuilder(resolvePythonExecutable(), runnerPath.toString());
        pb.directory(sessionDir);
        pb.redirectErrorStream(false);
        var env = pb.environment();
        env.put("MPLBACKEND", "Agg");
        env.put("PATH", SAFE_PATH);
        env.put("PYTHONUTF8", "1");
        env.put("PYTHONIOENCODING", "utf-8");
        if (depsDir != null) {
            // Replace any inherited PYTHONPATH — keeping it isolated to
            // ONLY the platform-managed deps dir is the whole point of
            // this feature (otherwise operator system-packages leak in).
            env.put("PYTHONPATH", depsDir.toAbsolutePath().toString());
        } else {
            env.remove("PYTHONPATH");
        }
        env.remove("PYTHONSTARTUP");
        env.remove("PYTHONHOME");
        env.remove("PYTHONUSERSITE");

        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread outThread = Thread.ofVirtual()
                .start(() -> readStream(new java.io.InputStreamReader(process.getInputStream(),
                        java.nio.charset.StandardCharsets.UTF_8), stdout));
        Thread errThread = Thread.ofVirtual()
                .start(() -> readStream(new java.io.InputStreamReader(process.getErrorStream(),
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
        File[] generatedFiles = sessionDir
                .listFiles((dir, name) -> !name.equals("script.py") && !name.equals("_runner.py"));

        // Snapshot the tenant convId BEFORE buildResult — the ThreadLocal
        // gets cleared in the outer finally and buildResult would see null
        // otherwise. The convId carries into the HMAC payload so the
        // server can later verify cookie-binding.
        String convForUrl = tenantConversationId.get();
        String result = buildResult(sessionId, exitCode, stdout, stderr, generatedFiles, convForUrl);
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
            StringBuilder stdout, StringBuilder stderr, File[] generatedFiles,
            String convId) {
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

        appendGeneratedFiles(result, sessionId, generatedFiles, convId);

        if (result.isEmpty()) {
            result.append("(no output)");
        }

        return result.toString();
    }

    /**
     * Emits the markdown bullet list for files produced by the Python
     * script. Extracted from {@link #buildResult} to keep the latter
     * within the project's cognitive-complexity budget. Each URL gets a
     * signed query string when the signer is wired (production); the
     * legacy 1-arg test constructor leaves {@code urlSigner == null} so
     * the URL stays unsigned and tests don't have to mint signatures.
     */
    private void appendGeneratedFiles(StringBuilder result, String sessionId,
            File[] generatedFiles, String convId) {
        if (generatedFiles == null || generatedFiles.length == 0) return;
        result.append("\n--- Generated Files ---\n");
        result.append(
                "IMPORTANT: Copy the markdown below EXACTLY as-is. URLs are relative paths starting with /. Do NOT add https:// or any domain.\n");
        for (File f : generatedFiles) {
            String signedSuffix = (urlSigner == null)
                    ? ""
                    : urlSigner.signQueryString(sessionId, f.getName(), convId);
            String fileUrl = "/api/v2/code-interpreter/" + sessionId + "/" + f.getName() + signedSuffix;
            String prefix = isImageFile(f.getName()) ? "!" : "Download ";
            // Image markdown: ![alt](url). Plain link: [Download name](url).
            // The "Download " prefix label lives inside the [] for non-image
            // links so the LLM gets a verbatim label it can echo.
            if (isImageFile(f.getName())) {
                result.append("![").append(f.getName()).append("](").append(fileUrl).append(")\n");
            } else {
                result.append("[").append(prefix).append(f.getName()).append("](").append(fileUrl).append(")\n");
            }
        }
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
                    # Headless capture: the LLM-generated code typically calls
                    # plt.show() to display the chart, but in MPLBACKEND=Agg
                    # show() does nothing and emits a warning. Replace it with
                    # a no-arg shim that saves every open figure to a numbered
                    # PNG in the session directory; the agent then picks them
                    # up via the "Generated Files" section and surfaces them
                    # as inline images in the chat response.
                    import matplotlib.pyplot as _plt
                    _fig_counter = [0]
                    def _save_show(*args, **kwargs):
                        for _num in _plt.get_fignums():
                            _fig_counter[0] += 1
                            _plt.figure(_num).savefig(
                                f'figure_{_fig_counter[0]}.png',
                                bbox_inches='tight', dpi=120)
                        _plt.close('all')
                    _plt.show = _save_show
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
                # If the user code created figures but never called show(),
                # flush them too so nothing is silently dropped.
                try:
                    if _plt.get_fignums():
                        _save_show()
                except NameError:
                    pass
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
}

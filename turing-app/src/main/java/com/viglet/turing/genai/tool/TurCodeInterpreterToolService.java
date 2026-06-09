package com.viglet.turing.genai.tool;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurCodeInterpreterToolService {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LENGTH = 15_000;
    private static final String SANDBOX_DIR = "code-interpreter";
    /**
     * Turing's virtual URI scheme for sandboxed artifacts (code-interpreter
     * output, workspace files). The LLM-facing markdown prefixes generated-file
     * URLs with this scheme; the chat client (admin console + SDK) resolves it
     * back to the served path. Adopting OpenAI's own {@code sandbox:} convention
     * is deliberate: OpenAI-family models echo a {@code sandbox:} URL verbatim
     * (it matches their Code Interpreter training) instead of corrupting a bare
     * relative path — the root cause of the "image won't render" reports. The
     * scheme carries no host, so the client decides whether to resolve it
     * relative (same-origin admin) or against a configured base (embedded SDK).
     */
    static final String SANDBOX_SCHEME = "sandbox:";
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

    /** True when running on Windows — native resource limiters are Linux-only. */
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT).startsWith("windows");

    /** Fixed, read-only system directories allowed in the subprocess PATH. */
    private static final String SAFE_PATH;
    static {
        SAFE_PATH = IS_WINDOWS
                ? String.join(File.pathSeparator, "C:\\Windows\\System32", "C:\\Windows")
                : String.join(File.pathSeparator, "/usr/bin", "/usr/local/bin", "/bin");
    }

    /**
     * Absolute Python candidates searched in order when no explicit path is
     * configured.
     */
    private static final List<String> PYTHON_CANDIDATES;
    static {
        PYTHON_CANDIDATES = IS_WINDOWS
                ? List.of("C:\\Python312\\python.exe", "C:\\Python311\\python.exe",
                        "C:\\Python310\\python.exe")
                : List.of("/usr/bin/python3", "/usr/local/bin/python3",
                        "/usr/bin/python", "/usr/local/bin/python");
    }

    /** Absolute {@code prlimit} candidates searched in order (util-linux). */
    private static final List<String> PRLIMIT_CANDIDATES = List.of(
            "/usr/bin/prlimit", "/bin/prlimit", "/usr/local/bin/prlimit");
    /** Absolute {@code systemd-run} candidates searched in order. */
    private static final List<String> SYSTEMD_RUN_CANDIDATES = List.of(
            "/usr/bin/systemd-run", "/bin/systemd-run",
            "/run/current-system/sw/bin/systemd-run");

    private final TurGlobalSettingsService turGlobalSettingsService;
    private final TurCustomToolDependencyService dependencyService;
    private final TurCodeInterpreterUrlSigner urlSigner;
    /**
     * Pre-warmed interpreter pool (T82). Null in the test constructors and
     * whenever the bean is absent — every consumer treats null as "warm pool
     * unavailable" and cold-spawns, so the field is always optional.
     */
    private final TurCodeInterpreterWarmPool warmPool;

    /**
     * Test-friendly constructor — wires the sandbox without auto-install
     * and without URL signing. Equivalent to
     * {@code new TurCodeInterpreterToolService(settings, null, null)}; URLs
     * are emitted unsigned (legacy behavior) and PYTHONPATH stays unset.
     */
    public TurCodeInterpreterToolService(TurGlobalSettingsService turGlobalSettingsService) {
        this(turGlobalSettingsService, null, null, null);
    }

    public TurCodeInterpreterToolService(TurGlobalSettingsService turGlobalSettingsService,
            TurCustomToolDependencyService dependencyService) {
        this(turGlobalSettingsService, dependencyService, null, null);
    }

    // Mark the full-args ctor as the one Spring should use for autowiring.
    // With several public constructors none of them is the obvious "longest
    // satisfiable" — Spring falls back to the no-arg ctor (which doesn't
    // exist) and the whole context fails to load. Annotation pins the choice.
    @Autowired
    public TurCodeInterpreterToolService(TurGlobalSettingsService turGlobalSettingsService,
            TurCustomToolDependencyService dependencyService,
            TurCodeInterpreterUrlSigner urlSigner,
            TurCodeInterpreterWarmPool warmPool) {
        this.turGlobalSettingsService = turGlobalSettingsService;
        this.dependencyService = dependencyService;
        this.urlSigner = urlSigner;
        this.warmPool = warmPool;
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

    // ───────────────────── T80 Docker sandbox config ─────────────────────
    // Used only when the Global Settings execution mode is DOCKER. Defaults
    // are conservative: no network, 512 MB RAM, 1 CPU, 128 PIDs. Operators
    // override per environment. The native path ignores every one of these.

    /** Docker CLI binary (on PATH by default). */
    @Value("${turing.code-interpreter.docker.executable:docker}")
    private String dockerExecutable;
    /** Container network: {@code none} (default, no egress) or a named network. */
    @Value("${turing.code-interpreter.docker.network:none}")
    private String dockerNetwork;
    /** Hard memory cap (docker {@code --memory} syntax, e.g. {@code 512m}). */
    @Value("${turing.code-interpreter.docker.memory:512m}")
    private String dockerMemory;
    /** CPU quota (docker {@code --cpus} syntax, e.g. {@code 1.0}). */
    @Value("${turing.code-interpreter.docker.cpus:1.0}")
    private String dockerCpus;
    /** Max PIDs to thwart fork bombs. */
    @Value("${turing.code-interpreter.docker.pids-limit:128}")
    private int dockerPidsLimit;
    /** How long to wait for the `docker version` probe before giving up. */
    @Value("${turing.code-interpreter.docker.probe-timeout-seconds:8}")
    private int dockerProbeTimeoutSeconds;

    // ───────────────── T81 NATIVE per-execution resource limits ──────────────
    // Only consulted on the NATIVE path (DOCKER already caps via the container
    // runtime). Opt-in (disabled by default) so existing native deployments
    // keep their exact current behavior — see the "opt-in per-entity" doctrine.

    /** Whether to wrap the native Python subprocess with a resource limiter. */
    @Value("${turing.code-interpreter.native.limits.enabled:false}")
    private boolean nativeLimitsEnabled;
    /**
     * Hard memory cap per execution. Docker-style size string ({@code 512m},
     * {@code 1g}, {@code 2gb}, or a bare byte count). Applied as
     * {@code prlimit --as} (virtual address space) or cgroup {@code MemoryMax}.
     */
    @Value("${turing.code-interpreter.native.limits.memory-max:1g}")
    private String nativeMemoryMax;
    /**
     * CPU-time cap in seconds ({@code prlimit --cpu} / {@code RLIMIT_CPU}). A
     * busy loop is killed with {@code SIGXCPU} after this much CPU time.
     * {@code <= 0} disables the CPU cap (memory cap still applies). Defaults
     * slightly above the 30s wall-clock {@link #TIMEOUT_SECONDS} so the
     * wall-clock guard normally fires first; this is the defense-in-depth
     * backstop. Ignored by the {@code systemd-run} backend.
     */
    @Value("${turing.code-interpreter.native.limits.cpu-seconds:35}")
    private int nativeCpuSeconds;
    /**
     * Which limiter backend to use: {@code auto} (default), {@code prlimit},
     * {@code systemd-run}, or {@code none}. See
     * {@link TurCodeInterpreterResourceLimiter}.
     */
    @Value("${turing.code-interpreter.native.limits.limiter:auto}")
    private String nativeLimiter;

    // ─────────────────── T82 NATIVE pre-warmed interpreter pool ──────────────
    // Keeps a few host interpreters booted-and-blocked so a frequently-invoked
    // tool doesn't pay the ~200ms cold-boot every call. NATIVE only, bypassed
    // when T81 limits are on (a running worker can't be retro-wrapped by
    // prlimit). Opt-in; the bean is primed in @PostConstruct.

    @PostConstruct
    void primeWarmPool() {
        if (warmPool != null && warmPool.isEnabled()) {
            warmPool.initialize(this::spawnWarmWorker);
        }
    }

    /** Mount point inside the container for the per-call session dir. */
    private static final String DOCKER_SANDBOX_MOUNT = "/sandbox";
    /** Read-only mount point inside the container for the platform deps dir. */
    private static final String DOCKER_DEPS_MOUNT = "/deps";
    /** Fallback image when the setting is somehow blank (defense in depth). */
    private static final String DOCKER_IMAGE_FALLBACK = "python:3.12-slim";

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
        return executePythonStructured(code, extraReqs).markdown();
    }

    /**
     * Structured sibling of {@link #executePythonWithExtraRequirements} (T83).
     * Runs the same gated execution but returns a {@link TurCodeInterpreterResult}
     * — {@code stdout}, {@code stderr}, the generated {@code files} (name +
     * signed URL + image flag + size), {@code exitCode}, {@code timedOut}, and
     * {@code durationMs} — so programmatic callers (Groovy Custom Tools,
     * webhooks, slot writers) read fields directly instead of regex-parsing the
     * markdown bullet list. The {@link TurCodeInterpreterResult#markdown()}
     * field still carries the exact LLM-facing rendering for callers that want
     * it (e.g. echoing into chat).
     *
     * <p>Behavior of the legacy markdown path is unchanged — the String
     * overloads simply return {@code .markdown()} of this result, so existing
     * LLM tool calls and Custom Tools that parse the markdown keep working.
     */
    public TurCodeInterpreterResult executePythonStructured(String code, String extraReqs) {
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
            return errorResult(null, "Error: code interpreter wait interrupted");
        }
        if (!acquired) {
            log.warn("[CodeInterpreter] concurrency exhausted (cap={} available=0); rejecting call",
                    maxConcurrent);
            return errorResult(null,
                    "Error: code interpreter is at capacity right now — please try again in a moment.");
        }
        try {
            return executePythonInternal(code, extraReqs);
        } finally {
            limiter.release();
        }
    }

    /**
     * Tenant-aware structured overload (T83) — mirror of
     * {@link #executePythonForTenant} that returns the structured
     * {@link TurCodeInterpreterResult} instead of markdown. Routes the session
     * dir under {@code tenants/{agentId}/{conversationId}/...} exactly as the
     * markdown path does.
     */
    public TurCodeInterpreterResult executePythonStructuredForTenant(String code, String extraReqs,
            String agentId, String conversationId) {
        tenantAgentId.set(sanitizePathSegment(agentId));
        tenantConversationId.set(sanitizePathSegment(conversationId));
        try {
            return executePythonStructured(code, extraReqs);
        } finally {
            tenantAgentId.remove();
            tenantConversationId.remove();
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

    private TurCodeInterpreterResult executePythonInternal(String code, String extraReqs) {
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
            return errorResult(sessionId, "Error executing Python code: " + e.getMessage());
        } catch (Exception e) {
            log.error("[CodeInterpreter] session={} | EXCEPTION: {}", sessionId, e.getMessage(), e);
            return errorResult(sessionId, "Error executing Python code: " + e.getMessage());
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

    // ───────────────────────── T80 Docker sandbox ─────────────────────────

    /**
     * Reads the configured execution mode from Global Settings, defaulting
     * to {@link TurCodeInterpreterExecutionMode#DEFAULT} (NATIVE) whenever
     * settings are unavailable (e.g. unit-test mock that returns null) so a
     * misconfiguration never bricks the sandbox — it just runs natively.
     */
    private TurCodeInterpreterExecutionMode resolveExecutionMode() {
        try {
            TurCodeInterpreterExecutionMode mode = turGlobalSettingsService.getCodeInterpreterExecutionMode();
            return mode == null ? TurCodeInterpreterExecutionMode.DEFAULT : mode;
        } catch (Exception e) {
            log.debug("[CodeInterpreter] could not resolve execution mode, defaulting to NATIVE: {}",
                    e.getMessage());
            return TurCodeInterpreterExecutionMode.DEFAULT;
        }
    }

    private String resolveDockerImage() {
        try {
            String image = turGlobalSettingsService.getCodeInterpreterDockerImage();
            return (image == null || image.isBlank()) ? DOCKER_IMAGE_FALLBACK : image.trim();
        } catch (Exception e) {
            return DOCKER_IMAGE_FALLBACK;
        }
    }

    /**
     * Sets the locked-down environment for the NATIVE subprocess path.
     * Extracted so the docker branch can opt out entirely (it sets env via
     * {@code -e} flags on the {@code docker run} command instead).
     */
    private void configureNativeEnv(ProcessBuilder pb, Path depsDir) {
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
    }

    // ──────────────── T82 pre-warmed interpreter pool plumbing ───────────────

    /**
     * Spawns one warm worker: a host Python booted with the same locked-down
     * NATIVE env as a cold call but parked on {@code stdin.readline()} via
     * {@link #buildWarmBootstrapScript()}. The factory handed to
     * {@link TurCodeInterpreterWarmPool}. PYTHONPATH is deliberately left unset
     * here — the per-call {@code _runner.py} injects the deps dir itself, so a
     * worker is deps-agnostic and reusable for any agent.
     *
     * @throws IllegalStateException when no Python executable is configured
     *         (propagated to the pool, which logs and stays under target).
     */
    Process spawnWarmWorker() throws java.io.IOException {
        String python = resolvePythonExecutable();
        ProcessBuilder pb = new ProcessBuilder(python, "-c", buildWarmBootstrapScript());
        pb.redirectErrorStream(false);
        configureNativeEnv(pb, null);
        return pb.start();
    }

    /**
     * The {@code python -c} bootstrap a warm worker runs. It blocks on a single
     * stdin line {@code "<sessionDir>\t<runnerFile>"}, {@code chdir}s into the
     * session dir, then {@code exec}s that per-call runner under a fresh
     * {@code __main__} namespace and exits — single-use, identical to a cold
     * spawn except the interpreter was already booted. Prints nothing of its
     * own before exec, so the caller's stdout/stderr capture is unchanged.
     */
    String buildWarmBootstrapScript() {
        return """
                import sys, os
                _line = sys.stdin.readline()
                if _line:
                    _parts = _line.rstrip('\\r\\n').split('\\t')
                    os.chdir(_parts[0])
                    _runner = _parts[1]
                    with open(_runner, encoding='utf-8') as _f:
                        _code = _f.read()
                    exec(compile(_code, _runner, 'exec'),
                         {'__name__': '__main__', '__file__': _runner})
                """;
    }

    /**
     * Tries to run this call on a pre-warmed worker instead of cold-spawning.
     * Returns the live worker {@link Process} (already fed its instructions),
     * or {@code null} to signal the caller to cold-spawn. Null whenever: the
     * T81 limiter prefix is non-empty (a running worker can't be wrapped), the
     * pool is absent/disabled, no worker is ready, the worker died, or feeding
     * it failed. Never throws.
     */
    private Process tryWarmStart(String sessionId, File sessionDir, Path runnerPath,
            List<String> limitPrefix) {
        if (!limitPrefix.isEmpty() || warmPool == null || !warmPool.isEnabled()) {
            return null;
        }
        Process worker = warmPool.acquire();
        if (worker == null || !worker.isAlive()) {
            return null;
        }
        try {
            feedWarmWorker(worker, sessionDir, runnerPath);
            log.info("[CodeInterpreter] session={} | NATIVE mode (warm pool worker)", sessionId);
            return worker;
        } catch (java.io.IOException e) {
            log.debug("[CodeInterpreter] session={} | warm worker feed failed ({}); cold fallback",
                    sessionId, e.getMessage());
            worker.destroyForcibly();
            return null;
        }
    }

    /**
     * Writes the single {@code "<sessionDir>\t<runnerFile>\n"} instruction line
     * to the warm worker's stdin and closes it (EOF). The booted interpreter,
     * blocked on {@code readline()}, picks it up and runs the runner.
     */
    private void feedWarmWorker(Process worker, File sessionDir, Path runnerPath)
            throws java.io.IOException {
        String instruction = sessionDir.getAbsolutePath() + "\t"
                + runnerPath.getFileName().toString() + "\n";
        try (var stdin = worker.getOutputStream()) {
            stdin.write(instruction.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            stdin.flush();
        }
    }

    // ──────────────── T81 NATIVE resource-limit command prefix ───────────────

    /**
     * Builds the limiter command prefix prepended to the native Python
     * invocation (e.g. {@code [prlimit, --as=1073741824, --cpu=35, --]}).
     * Returns an empty list — i.e. legacy unwrapped behavior — when:
     * <ul>
     *   <li>limits are disabled ({@code turing.code-interpreter.native.limits.enabled=false});</li>
     *   <li>running on Windows (rlimits / cgroups are Linux-only);</li>
     *   <li>the selected / auto-detected limiter binary is not present;</li>
     *   <li>the configured memory string can't be parsed.</li>
     * </ul>
     * Never throws — a misconfiguration degrades to "run without caps" + a
     * warning rather than breaking the sandbox.
     */
    List<String> nativeLimitPrefix() {
        if (!nativeLimitsEnabled) {
            return List.of();
        }
        if (IS_WINDOWS) {
            log.debug("[CodeInterpreter] native resource limits are Linux-only; ignoring on Windows");
            return List.of();
        }
        try {
            TurCodeInterpreterResourceLimiter requested =
                    TurCodeInterpreterResourceLimiter.fromString(nativeLimiter);
            String prlimit = findExecutable(PRLIMIT_CANDIDATES);
            String systemdRun = findExecutable(SYSTEMD_RUN_CANDIDATES);

            TurCodeInterpreterResourceLimiter chosen = switch (requested) {
                case PRLIMIT -> prlimit != null
                        ? TurCodeInterpreterResourceLimiter.PRLIMIT
                        : TurCodeInterpreterResourceLimiter.NONE;
                case SYSTEMD_RUN -> systemdRun != null
                        ? TurCodeInterpreterResourceLimiter.SYSTEMD_RUN
                        : TurCodeInterpreterResourceLimiter.NONE;
                case NONE -> TurCodeInterpreterResourceLimiter.NONE;
                case AUTO -> prlimit != null
                        ? TurCodeInterpreterResourceLimiter.PRLIMIT
                        : (systemdRun != null
                                ? TurCodeInterpreterResourceLimiter.SYSTEMD_RUN
                                : TurCodeInterpreterResourceLimiter.NONE);
            };

            return switch (chosen) {
                case PRLIMIT -> buildPrlimitPrefix(prlimit);
                case SYSTEMD_RUN -> buildSystemdRunPrefix(systemdRun);
                default -> {
                    log.warn("[CodeInterpreter] native resource limits enabled (limiter={}) but no "
                            + "prlimit/systemd-run binary found — running without caps", nativeLimiter);
                    yield List.of();
                }
            };
        } catch (RuntimeException e) {
            log.warn("[CodeInterpreter] could not build native resource-limit prefix ({}); "
                    + "running without caps", e.getMessage());
            return List.of();
        }
    }

    /**
     * {@code prlimit --as=<bytes> [--cpu=<seconds>] --} — POSIX rlimits via
     * util-linux. {@code RLIMIT_AS} caps virtual address space (a memory-bomb
     * fails with {@code MemoryError}); {@code RLIMIT_CPU} caps CPU seconds
     * (a busy loop gets {@code SIGXCPU}). prlimit {@code exec}s into Python,
     * so the limited process keeps the subprocess PID and the timeout
     * {@code destroyForcibly} kills it cleanly.
     */
    private List<String> buildPrlimitPrefix(String prlimitPath) {
        long bytes = parseMemoryToBytes(nativeMemoryMax);
        List<String> prefix = new ArrayList<>();
        prefix.add(prlimitPath);
        prefix.add("--as=" + bytes);
        if (nativeCpuSeconds > 0) {
            prefix.add("--cpu=" + nativeCpuSeconds);
        }
        prefix.add("--");
        return prefix;
    }

    /**
     * {@code systemd-run --scope -p MemoryMax=<bytes> -p MemorySwapMax=0
     * -p CPUQuota=100% --} — a transient cgroup. Memory is RSS-accounted by
     * the kernel and a memory-bomb is OOM-killed inside the cgroup; CPU is
     * bounded to one core. {@code --collect} GCs the unit on exit and
     * {@code --quiet} suppresses the "Running as unit" banner.
     * {@code cpu-seconds} has no cgroup equivalent, so the wall-clock timeout
     * remains the CPU-time guard here.
     */
    private List<String> buildSystemdRunPrefix(String systemdRunPath) {
        long bytes = parseMemoryToBytes(nativeMemoryMax);
        List<String> prefix = new ArrayList<>();
        prefix.add(systemdRunPath);
        prefix.add("--scope");
        prefix.add("--quiet");
        prefix.add("--collect");
        prefix.add("-p");
        prefix.add("MemoryMax=" + bytes);
        prefix.add("-p");
        prefix.add("MemorySwapMax=0");
        prefix.add("-p");
        prefix.add("CPUQuota=100%");
        prefix.add("--");
        return prefix;
    }

    /** First executable path in {@code candidates}, or null if none exists. */
    private static String findExecutable(List<String> candidates) {
        for (String candidate : candidates) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Parses a Docker-style memory size into bytes. Accepts a bare byte count
     * ({@code 536870912}), a binary suffix {@code k}/{@code m}/{@code g}/{@code t}
     * (1024-based, matching Docker), and an optional trailing {@code b}
     * ({@code 512mb} == {@code 512m}). Case-insensitive.
     *
     * @throws IllegalArgumentException when the value is blank or non-numeric.
     */
    static long parseMemoryToBytes(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("memory limit is blank");
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.endsWith("b")) {
            v = v.substring(0, v.length() - 1);
        }
        long multiplier = 1L;
        if (!v.isEmpty()) {
            char suffix = v.charAt(v.length() - 1);
            multiplier = switch (suffix) {
                case 'k' -> 1024L;
                case 'm' -> 1024L * 1024L;
                case 'g' -> 1024L * 1024L * 1024L;
                case 't' -> 1024L * 1024L * 1024L * 1024L;
                default -> 1L;
            };
            if (multiplier != 1L) {
                v = v.substring(0, v.length() - 1);
            }
        }
        long base = Long.parseLong(v.trim());
        if (base < 0) {
            throw new IllegalArgumentException("memory limit must be non-negative: " + value);
        }
        return base * multiplier;
    }

    /**
     * Builds the {@code docker run} command for one execution. Hardening:
     * <ul>
     *   <li>{@code --rm} — container removed on exit (incl. after kill);</li>
     *   <li>{@code --network} — {@code none} by default (no egress);</li>
     *   <li>{@code --memory} / {@code --cpus} / {@code --pids-limit} —
     *       resource caps (fork-bomb / OOM containment);</li>
     *   <li>{@code --cap-drop ALL} + {@code --security-opt no-new-privileges}
     *       — strips Linux capabilities and privilege escalation;</li>
     *   <li>{@code --read-only} root fs with a small {@code /tmp} tmpfs and
     *       the session dir as the ONLY writable bind mount — a malicious
     *       script can't reach other tenants' files or the host;</li>
     *   <li>deps dir (when present) bind-mounted <b>read-only</b> at
     *       {@code /deps} and put on {@code PYTHONPATH}.</li>
     * </ul>
     *
     * <p>Generated files land in the bind-mounted session dir, so the host
     * sees them and the existing "Generated Files" logic is unchanged.
     */
    private ProcessBuilder buildDockerProcess(String containerName, File sessionDir, Path depsDir) {
        List<String> cmd = new ArrayList<>();
        cmd.add(dockerExecutable);
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add("--network");
        cmd.add(dockerNetwork);
        cmd.add("--memory");
        cmd.add(dockerMemory);
        cmd.add("--cpus");
        cmd.add(dockerCpus);
        cmd.add("--pids-limit");
        cmd.add(String.valueOf(dockerPidsLimit));
        cmd.add("--cap-drop");
        cmd.add("ALL");
        cmd.add("--security-opt");
        cmd.add("no-new-privileges");
        cmd.add("--read-only");
        cmd.add("--tmpfs");
        cmd.add("/tmp:rw,size=64m");
        // The session dir is the ONLY writable mount — script.py / _runner.py
        // live here and generated files are written here.
        cmd.add("-v");
        cmd.add(sessionDir.getAbsolutePath() + ":" + DOCKER_SANDBOX_MOUNT + ":rw");
        if (depsDir != null) {
            cmd.add("-v");
            cmd.add(depsDir.toAbsolutePath() + ":" + DOCKER_DEPS_MOUNT + ":ro");
        }
        cmd.add("-w");
        cmd.add(DOCKER_SANDBOX_MOUNT);
        cmd.add("-e");
        cmd.add("MPLBACKEND=Agg");
        cmd.add("-e");
        cmd.add("PYTHONUTF8=1");
        cmd.add("-e");
        cmd.add("PYTHONIOENCODING=utf-8");
        // matplotlib/fontconfig need a writable HOME; /tmp is the tmpfs.
        cmd.add("-e");
        cmd.add("HOME=/tmp");
        cmd.add("-e");
        cmd.add("MPLCONFIGDIR=/tmp");
        if (depsDir != null) {
            cmd.add("-e");
            cmd.add("PYTHONPATH=" + DOCKER_DEPS_MOUNT);
        }
        cmd.add(resolveDockerImage());
        cmd.add("python");
        cmd.add(DOCKER_SANDBOX_MOUNT + "/_runner.py");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        return pb;
    }

    private void killDockerContainer(String containerName) {
        try {
            Process kill = new ProcessBuilder(dockerExecutable, "kill", containerName)
                    .redirectErrorStream(true)
                    .start();
            kill.waitFor(5, TimeUnit.SECONDS);
        } catch (IOException e) {
            log.debug("[CodeInterpreter] docker kill {} failed: {}", containerName, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Probes Docker availability for the admin UI ("Check Docker" button)
     * by running {@code docker version --format {{.Server.Version}}}. Never
     * throws — failures (binary missing, daemon down, timeout) come back as
     * {@code available=false} with an {@code error} message.
     */
    public TurDockerStatus checkDocker() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    dockerExecutable, "version", "--format", "{{.Server.Version}}");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            Thread reader = Thread.ofVirtual().start(() -> readStream(
                    new java.io.InputStreamReader(p.getInputStream(),
                            java.nio.charset.StandardCharsets.UTF_8),
                    out));
            int timeout = dockerProbeTimeoutSeconds > 0 ? dockerProbeTimeoutSeconds : 8;
            boolean finished = p.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                reader.join(2000);
                return new TurDockerStatus(false, null, "docker version probe timed out");
            }
            reader.join(2000);
            String output = out.toString().trim();
            if (p.exitValue() == 0 && !output.isBlank()) {
                return new TurDockerStatus(true, output, null);
            }
            return new TurDockerStatus(false, null,
                    output.isBlank() ? "docker daemon not reachable" : output);
        } catch (IOException e) {
            return new TurDockerStatus(false, null,
                    "docker executable not found or not runnable: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TurDockerStatus(false, null, "docker probe interrupted");
        }
    }

    /**
     * Result of {@link #checkDocker()}. {@code available} true means the
     * daemon answered and {@code serverVersion} is populated; otherwise
     * {@code error} explains why.
     */
    public record TurDockerStatus(boolean available, String serverVersion, String error) {
    }

    /**
     * One file produced by an execution (T83). {@code url} is the (optionally
     * signed) relative serve path under {@code /api/v2/code-interpreter/...};
     * {@code image} mirrors {@link #isImageFile} so a caller can choose between
     * inline render and download link without re-deriving it from the
     * extension; {@code sizeBytes} is the on-disk size at capture time.
     *
     * @author Alexandre Oliveira
     * @since 2026.3.1
     */
    public record TurCodeInterpreterFile(String name, String url, boolean image, long sizeBytes) {
    }

    /**
     * Structured result of a Python execution (T83). Gives programmatic callers
     * (Groovy Custom Tools, webhooks, slot writers) the {@code stdout},
     * {@code stderr}, generated {@code files}, {@code exitCode}, {@code timedOut}
     * flag and {@code durationMs} as typed fields instead of forcing them to
     * regex-parse the markdown. {@code success} is {@code exitCode == 0 &&
     * !timedOut}. {@code markdown} retains the exact LLM-facing rendering for
     * callers that still want it (e.g. echoing the download link into chat);
     * the legacy String overloads return precisely this field.
     *
     * @author Alexandre Oliveira
     * @since 2026.3.1
     */
    public record TurCodeInterpreterResult(
            String sessionId,
            boolean success,
            int exitCode,
            boolean timedOut,
            long durationMs,
            String stdout,
            String stderr,
            List<TurCodeInterpreterFile> files,
            String markdown) {
    }

    private TurCodeInterpreterResult executeAndCollectOutput(String sessionId, File sessionDir,
            Path scriptPath) throws IOException, InterruptedException {
        // Resolve auto-installed deps BEFORE building the env / runner. The
        // dep service unions the global `GLOBAL_PYTHON_REQUIREMENTS` setting
        // with the optional agent-specific addendum (carried via the
        // ThreadLocal set by the public entry point) — hash-keyed cache
        // dir on first call, instant on subsequent (cache hit). When
        // both sources are blank, returns null → no path injection and
        // behavior matches the pre-2026.2.7 era (operator manages the
        // Python env manually). Failures propagate as
        // DependencyInstallException → caught by the outer try/catch and
        // surfaced as the script's error message.
        //
        // depsDir is passed BOTH into the runner script (sys.path.insert) AND
        // the env PYTHONPATH below — belt-and-suspenders. The runner-side
        // inject is what actually works on Windows (PYTHONPATH propagation
        // through ProcessBuilder.environment() doesn't always reach the
        // child process); the env var stays for any Python-native helper
        // that reads it directly.
        Path depsDir = dependencyService.ensureInstalled(extraRequirements.get());
        Path runnerPath = sessionDir.toPath().resolve("_runner.py");

        // T80 — branch on execution mode. NATIVE forks the host Python
        // subprocess (legacy default); DOCKER bind-mounts only the session
        // dir into a throwaway hardened container. The post-start stream /
        // timeout / generated-file handling below is identical for both.
        TurCodeInterpreterExecutionMode mode = resolveExecutionMode();
        // T83 — wall-clock timing for the structured result's durationMs. Starts
        // just before process spawn so it measures execution, not dep install.
        long startNanos = System.nanoTime();
        Process process;
        String containerName = null;
        if (mode == TurCodeInterpreterExecutionMode.DOCKER) {
            // Inside the container the deps dir (when present) is bind-mounted
            // read-only at /deps; the runner must reference THAT path, not the
            // host absolute path.
            String depsLiteral = depsDir != null ? DOCKER_DEPS_MOUNT : null;
            Files.writeString(runnerPath,
                    buildRunnerScript(scriptPath.getFileName().toString(), depsLiteral));
            containerName = "turing-ci-" + sessionId;
            ProcessBuilder pb = buildDockerProcess(containerName, sessionDir, depsDir);
            log.info("[CodeInterpreter] session={} | DOCKER mode | image={} container={} network={}",
                    sessionId, resolveDockerImage(), containerName, dockerNetwork);
            process = pb.start();
        } else {
            Files.writeString(runnerPath,
                    buildRunnerScript(scriptPath.getFileName().toString(), depsDir));
            // T81 — optionally wrap the host Python in a resource limiter
            // (prlimit / systemd-run) so a memory-bomb script is capped
            // before it can exhaust host RAM. Empty prefix = legacy behavior.
            List<String> limitPrefix = nativeLimitPrefix();
            // T82 — when no limiter wrapper is needed, try a pre-warmed
            // interpreter to skip the ~200ms cold boot. Returns null whenever
            // the pool is off/empty/unavailable → transparent cold fallback.
            Process warm = tryWarmStart(sessionId, sessionDir, runnerPath, limitPrefix);
            if (warm != null) {
                process = warm;
            } else {
                List<String> nativeCmd = new ArrayList<>(limitPrefix);
                nativeCmd.add(resolvePythonExecutable());
                nativeCmd.add(runnerPath.toString());
                ProcessBuilder pb = new ProcessBuilder(nativeCmd);
                pb.directory(sessionDir);
                pb.redirectErrorStream(false);
                configureNativeEnv(pb, depsDir);
                process = pb.start();
            }
        }

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
            // Killing the `docker run` client doesn't stop the container —
            // it keeps running detached. Fire a best-effort `docker kill` so
            // a runaway script doesn't outlive its timeout. With `--rm` the
            // container is auto-removed once killed.
            if (containerName != null) {
                killDockerContainer(containerName);
            }
            outThread.join(2000);
            errThread.join(2000);
            long durationMs = elapsedMs(startNanos);
            log.warn("[CodeInterpreter] session={} | TIMEOUT after {}s | script={} | Partial stdout:\n{}",
                    sessionId, TIMEOUT_SECONDS, scriptPath.toAbsolutePath(), stdout);
            String markdown = "Error: Code execution timed out after " + TIMEOUT_SECONDS + " seconds.\n"
                    + "Partial output:\n" + stdout;
            return new TurCodeInterpreterResult(sessionId, false, -1, true, durationMs,
                    stdout.toString(), stderr.toString(), List.of(), markdown);
        }

        outThread.join(5000);
        errThread.join(5000);
        long durationMs = elapsedMs(startNanos);

        int exitCode = process.exitValue();
        File[] generatedFiles = sessionDir
                .listFiles((dir, name) -> !name.equals("script.py") && !name.equals("_runner.py"));

        // Snapshot the tenant convId BEFORE buildResult — the ThreadLocal
        // gets cleared in the outer finally and buildResult would see null
        // otherwise. The convId carries into the HMAC payload so the
        // server can later verify cookie-binding.
        String convForUrl = tenantConversationId.get();
        String markdown = buildResult(sessionId, exitCode, stdout, stderr, generatedFiles, convForUrl);
        List<TurCodeInterpreterFile> files = buildFileDescriptors(sessionId, generatedFiles, convForUrl);
        logResult(sessionId, scriptPath, exitCode, stdout, stderr, generatedFiles);
        return new TurCodeInterpreterResult(sessionId, exitCode == 0, exitCode, false, durationMs,
                stdout.toString(), stderr.toString(), files, markdown);
    }

    /** Elapsed wall-clock ms since {@code startNanos} (from {@link System#nanoTime()}). */
    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * Builds a failed {@link TurCodeInterpreterResult} whose {@code markdown}
     * is the supplied diagnostic — used for infra failures that occur before
     * (or instead of) a subprocess run: capacity rejection, interrupt, Python
     * not found, or an unexpected exception. {@code exitCode} is {@code -1},
     * {@code success} false, {@code timedOut} false, no files; the message is
     * mirrored into {@code stderr} for structured consumers. Returning the same
     * string as {@code markdown} keeps the legacy String overloads byte-for-byte
     * identical to their pre-T83 behavior.
     */
    private static TurCodeInterpreterResult errorResult(String sessionId, String message) {
        return new TurCodeInterpreterResult(sessionId, false, -1, false, 0L,
                "", message, List.of(), message);
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
                "IMPORTANT: Copy the markdown below EXACTLY as-is, including the `sandbox:` URL scheme. "
                        + "Do NOT rewrite, strip, or add a domain to these URLs — the chat client resolves "
                        + "`sandbox:` to the real file location.\n");
        for (File f : generatedFiles) {
            // LLM-facing markdown uses the Turing `sandbox:` virtual scheme
            // (see SANDBOX_SCHEME): OpenAI-family models are trained on their
            // own Code Interpreter, which emits `sandbox:/...` URIs, so they
            // copy a `sandbox:`-prefixed URL verbatim instead of mangling a
            // bare relative path (the recurring `sandbox:/api/...` artifact).
            // The chat client resolves the scheme back to the served path.
            // The STRUCTURED descriptor (buildFileDescriptors) keeps the clean
            // relative URL for programmatic callers.
            String fileUrl = SANDBOX_SCHEME + signedFileUrl(sessionId, f.getName(), convId);
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

    /**
     * Structured-output mirror of {@link #appendGeneratedFiles} (T83): the same
     * files with the same signed URLs, exposed as {@link TurCodeInterpreterFile}
     * records so programmatic callers read {@code name} / {@code url} /
     * {@code image} / {@code sizeBytes} directly instead of regex-parsing the
     * markdown bullet list.
     */
    private List<TurCodeInterpreterFile> buildFileDescriptors(String sessionId,
            File[] generatedFiles, String convId) {
        if (generatedFiles == null || generatedFiles.length == 0) return List.of();
        List<TurCodeInterpreterFile> files = new ArrayList<>(generatedFiles.length);
        for (File f : generatedFiles) {
            files.add(new TurCodeInterpreterFile(
                    f.getName(),
                    signedFileUrl(sessionId, f.getName(), convId),
                    isImageFile(f.getName()),
                    f.length()));
        }
        return files;
    }

    /**
     * Builds the (optionally HMAC-signed) relative serve URL for a generated
     * file. Shared by the markdown rendering ({@link #appendGeneratedFiles})
     * and the structured descriptors ({@link #buildFileDescriptors}) so the
     * two never drift. Unsigned when the signer is absent (legacy 1-arg test
     * ctor leaves {@code urlSigner == null}).
     */
    private String signedFileUrl(String sessionId, String fileName, String convId) {
        String signedSuffix = (urlSigner == null)
                ? ""
                : urlSigner.signQueryString(sessionId, fileName, convId);
        return "/api/v2/code-interpreter/" + sessionId + "/" + fileName + signedSuffix;
    }

    /**
     * Chars of stdout to inline in the failure log. Long enough to surface
     * "reportlab not installed" / "ModuleNotFoundError" / SystemExit prints,
     * short enough that a runaway script's "Hello World" loop doesn't blow
     * the log line.
     */
    private static final int FAILURE_STDOUT_TAIL_CHARS = 1500;

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
            // Include stdout tail too. Failures like `raise SystemExit(1)`
            // after a `print("…")` (e.g. our reportlab-missing branch) put
            // the diagnostic on stdout and leave stderr empty — without
            // this, the operator sees "FAILED stderr:<empty>" and has no
            // signal to act on.
            log.error("[CodeInterpreter] session={} | FAILED (exit={}) | script={} | stdout (last {} chars):\n{}\nstderr:\n{}",
                    sessionId, exitCode, scriptPath.toAbsolutePath(),
                    FAILURE_STDOUT_TAIL_CHARS, tail(stdout, FAILURE_STDOUT_TAIL_CHARS), stderr);
        }
    }

    /**
     * Returns the trailing {@code n} chars of {@code buf}, prefixed with
     * a "[…N chars truncated]" marker when the buffer was longer than
     * the cap. Empty buffer renders as {@code "(empty)"} so the log line
     * is never ambiguous (vs. an actual empty placeholder).
     */
    private static String tail(StringBuilder buf, int n) {
        if (buf == null || buf.isEmpty()) return "(empty)";
        if (buf.length() <= n) return buf.toString();
        int dropped = buf.length() - n;
        return "[… " + dropped + " chars truncated]\n" + buf.substring(buf.length() - n);
    }

    /**
     * Builds a small Python runner that parses the user script with {@code ast},
     * detects if the last statement is a bare expression (e.g. {@code result}
     * instead of {@code print(result)}), and wraps it with {@code print()} so
     * the value is emitted to stdout — mimicking Jupyter/IPython behaviour.
     *
     * <p>When {@code depsDir} is non-null, the runner prepends it to
     * {@code sys.path} BEFORE any import. This is the load-bearing fix for
     * Windows where {@code ProcessBuilder.environment().put("PYTHONPATH", …)}
     * sometimes doesn't propagate to the child Python — verified empirically
     * (`pip install --target=<dir>` succeeded, the dir contained
     * {@code reportlab/}, but the script's {@code from reportlab…} import
     * failed with {@code ImportError} until the path was injected into the
     * runner). Embedding the path in the script bypasses every layer of env
     * var inheritance and works regardless of the OS quirk.
     */
    private String buildRunnerScript(String scriptFilename, Path depsDir) {
        return buildRunnerScript(scriptFilename,
                depsDir != null ? depsDir.toAbsolutePath().toString() : null);
    }

    /**
     * Overload that takes the deps path as a raw string so the Docker path
     * can pass the in-container mount point ({@code /deps}) rather than the
     * host absolute path. {@code null} → no {@code sys.path} injection.
     */
    private String buildRunnerScript(String scriptFilename, String depsPath) {
        String depsPathLiteral = depsPath != null
                ? repr(depsPath)
                : "None";
        return """
                import sys
                _DEPS_DIR = %s
                if _DEPS_DIR is not None and _DEPS_DIR not in sys.path:
                    sys.path.insert(0, _DEPS_DIR)
                import ast
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
                """.formatted(depsPathLiteral, repr(scriptFilename), repr(scriptFilename));
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

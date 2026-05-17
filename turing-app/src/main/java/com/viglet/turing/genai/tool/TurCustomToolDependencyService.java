/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * Global Python dependency manager for the Code Interpreter sandbox.
 * Reads the {@code requirements.txt}-style declaration from
 * {@link TurGlobalSettingsService#getPythonRequirements()} and ensures
 * the packages are available on a cache directory suitable for
 * {@code PYTHONPATH} injection:
 *
 * <ol>
 *   <li><b>Normalizes</b> the requirements text (strip comments, trim,
 *       sort lines) so equivalent content always produces the same hash.</li>
 *   <li><b>Hashes</b> the normalized text with SHA-256 to derive a cache
 *       directory name.</li>
 *   <li><b>Returns</b> the cache dir path if it's already installed
 *       (presence of a {@code .installed} marker file inside).</li>
 *   <li><b>Installs</b> the requirements via {@code pip install
 *       --target=<cache-dir> -r requirements.txt} on cache miss, then
 *       writes the marker.</li>
 * </ol>
 *
 * <p>The cache lives under
 * {@code <store>/code-interpreter/_deps/<sha256-hex>/}. Each unique
 * requirements set gets its own directory; version bumps automatically
 * allocate a new one without manual cleanup. Old hash dirs sit until
 * disk pressure — garbage collection is a v2 concern (see
 * {@code turing.code-interpreter.deps-cache-max-mb}).
 *
 * <p>Failure semantics: pip exit != 0 throws {@link DependencyInstallException}
 * with stderr surfaced. The {@code .installed} marker is NOT written, so
 * the next call re-tries cleanly (cache only pins successes).
 *
 * <h2>Why global, not per-tool / per-agent?</h2>
 * <p>The Code Interpreter sandbox is a single Python process per call,
 * spawned with a single {@code PYTHONPATH}. Operating multiple deps
 * dirs per-tool would mean either running multiple interpreters in
 * parallel (heavy) or hot-swapping {@code PYTHONPATH} between tool
 * calls in the same agent turn (fragile). One global env keeps the
 * mental model simple: "the Python sandbox has these packages."
 *
 * <h2>Subprocess injection for testability</h2>
 * <p>The actual pip invocation goes through {@link PipRunner} —
 * abstracted so unit tests inject a stub instead of touching real pip.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@Service
public class TurCustomToolDependencyService {

    static final String DEPS_SUBDIR = "code-interpreter/_deps";
    static final String INSTALLED_MARKER = ".installed";
    static final int PIP_TIMEOUT_SECONDS = 180;

    /** In-process lock map keyed by hash — prevents two threads racing on the same install. */
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    private final TurGlobalSettingsService globalSettings;
    private final PipRunner pipRunner;

    // Disambiguates Spring's constructor selection — without this Spring sees
    // two ctors and tries to wire the longest (2-arg), which needs a PipRunner
    // bean that doesn't exist (it's a package-private test seam). Pinning the
    // public 1-arg ctor as @Autowired sends Spring down the production path
    // (defaultPipRunner) and leaves the 2-arg ctor available for the test.
    @Autowired
    public TurCustomToolDependencyService(TurGlobalSettingsService globalSettings) {
        this(globalSettings, defaultPipRunner());
    }

    /** Constructor for tests: inject a stub {@link PipRunner}. */
    TurCustomToolDependencyService(TurGlobalSettingsService globalSettings,
            PipRunner pipRunner) {
        this.globalSettings = globalSettings;
        this.pipRunner = pipRunner;
    }

    /**
     * Convenience overload — installs only the global requirements (no
     * agent-specific addendum). Used by code paths that don't know which
     * agent is in scope (e.g. an LLM directly invoking the
     * {@code execute_python} {@code @Tool} without going through a
     * Custom Tool).
     */
    public Path ensureInstalled() {
        return ensureInstalled(null);
    }

    /**
     * Ensures the UNION of global + agent-specific Python requirements is
     * installed and returns a directory to prepend to {@code PYTHONPATH}.
     *
     * <p>Reads {@link TurGlobalSettingsService#getPythonRequirements()}
     * for the baseline; appends {@code extraRequirements} (typically the
     * active agent's {@code pythonRequirements}). After normalization +
     * dedup (sorted unique lines), hashes the union and uses the hash as
     * the cache dir name — different agent + global combinations each
     * get their own dir without conflict.
     *
     * @param extraRequirements agent-specific addendum, or null/blank
     *                          when no extra packages are needed.
     * @return path to the cache dir to prepend to {@code PYTHONPATH}, or
     *         {@code null} when the union is blank (neither global nor
     *         extra has anything to install).
     * @throws DependencyInstallException when pip exits non-zero or the
     *         subprocess times out. Caller decides whether to retry or
     *         surface the error.
     */
    public Path ensureInstalled(String extraRequirements) {
        String normalized = normalize(unionOfGlobalAnd(extraRequirements));
        if (normalized.isEmpty()) {
            return null;
        }
        String hash = sha256Hex(normalized);
        Path depsRoot = Path.of(TurCommonsUtils.addSubDirToStoreDir(DEPS_SUBDIR).getPath());
        Path depsDir = depsRoot.resolve(hash);
        Path marker = depsDir.resolve(INSTALLED_MARKER);

        // Fast path: marker exists → already installed.
        if (Files.exists(marker)) {
            log.debug("[ToolDeps] cache hit hash={} dir={}", hash, depsDir);
            return depsDir;
        }

        // Slow path: one-thread-at-a-time per hash, double-checked.
        synchronized (locks.computeIfAbsent(hash, k -> new Object())) {
            if (Files.exists(marker)) {
                return depsDir;
            }
            try {
                Files.createDirectories(depsDir);
                Path reqFile = depsDir.resolve("requirements.txt");
                Files.writeString(reqFile, normalized, StandardCharsets.UTF_8);
                runPip(depsDir, reqFile);
                Files.writeString(marker, hash, StandardCharsets.UTF_8);
                log.info("[ToolDeps] installed hash={} dir={} packages={}",
                        hash, depsDir, normalized.lines().filter(s -> !s.isBlank()).count());
                return depsDir;
            } catch (IOException e) {
                throw new DependencyInstallException(
                        "Failed to write requirements/marker file in " + depsDir, e);
            }
        }
    }

    private void runPip(Path depsDir, Path reqFile) {
        String python = globalSettings.getPythonExecutable();
        if (python == null || python.isBlank()) {
            throw new DependencyInstallException(
                    "No Python executable configured. Set Global Settings → "
                            + "Python Executable or `turing.code-interpreter.python-executable`.");
        }
        List<String> cmd = List.of(
                python,
                "-m", "pip", "install",
                "--no-input",
                "--disable-pip-version-check",
                "--target=" + depsDir.toAbsolutePath(),
                "-r", reqFile.toAbsolutePath().toString());
        PipResult result = pipRunner.run(cmd, PIP_TIMEOUT_SECONDS);
        if (result.exitCode() != 0) {
            String stderr = result.stderr().length() > 4096
                    ? result.stderr().substring(0, 4096) + "...[truncated]"
                    : result.stderr();
            throw new DependencyInstallException(
                    "pip install failed (exit=" + result.exitCode() + "): " + stderr);
        }
    }

    /**
     * Concatenates the global {@code pythonRequirements} setting with an
     * agent-specific addendum. Both pieces may be null/blank; the result
     * is a single newline-joined text that {@link #normalize} dedups and
     * sorts. The order in the concat doesn't matter because normalize
     * sorts before hashing.
     */
    private String unionOfGlobalAnd(String extraRequirements) {
        String global = globalSettings.getPythonRequirements();
        StringBuilder sb = new StringBuilder();
        if (global != null && !global.isBlank()) {
            sb.append(global);
            if (!global.endsWith("\n")) sb.append('\n');
        }
        if (extraRequirements != null && !extraRequirements.isBlank()) {
            sb.append(extraRequirements);
        }
        return sb.toString();
    }

    /**
     * Normalizes the raw requirements text so equivalent content (different
     * whitespace, comment lines, blank lines, ordering, AND duplicates
     * from the global+agent union) hashes the same.
     *
     * <p>Sorting + dedup is important: when global has {@code reportlab}
     * and agent also lists {@code reportlab}, the union must still hash
     * the same as if only one declared it. {@link TreeSet} (sorted +
     * unique) plus stable iteration gives both for free.
     */
    static String normalize(String raw) {
        if (raw == null) return "";
        TreeSet<String> uniqueSorted = new TreeSet<>();
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            // Drop inline comments too (`reportlab==4.0  # for PDF`).
            int hash = trimmed.indexOf('#');
            if (hash >= 0) trimmed = trimmed.substring(0, hash).trim();
            if (!trimmed.isEmpty()) uniqueSorted.add(trimmed);
        }
        if (uniqueSorted.isEmpty()) return "";
        return String.join("\n", uniqueSorted) + "\n";
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every Java implementation.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ─────────────────────── Subprocess abstraction ───────────────────────

    /** Functional indirection over the pip subprocess — swap in tests. */
    @FunctionalInterface
    interface PipRunner {
        PipResult run(List<String> command, int timeoutSeconds);
    }

    /** Result envelope for the pip subprocess invocation. */
    record PipResult(int exitCode, String stdout, String stderr) {
    }

    /**
     * Production {@link PipRunner} — actually forks pip. Stderr/stdout
     * captured on virtual threads so a chatty pip doesn't deadlock by
     * filling the OS pipe buffer.
     */
    static PipRunner defaultPipRunner() {
        return (cmd, timeoutSeconds) -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(false);
                Process p = pb.start();
                StreamBuffer outBuf = new StreamBuffer();
                StreamBuffer errBuf = new StreamBuffer();
                Thread outT = Thread.ofVirtual().start(() -> drain(p.getInputStream(), outBuf));
                Thread errT = Thread.ofVirtual().start(() -> drain(p.getErrorStream(), errBuf));
                boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    p.destroyForcibly();
                    outT.join(2_000);
                    errT.join(2_000);
                    return new PipResult(-1, outBuf.snapshot(),
                            errBuf.snapshot() + "\n[ToolDeps] pip timed out after " + timeoutSeconds + "s");
                }
                outT.join(5_000);
                errT.join(5_000);
                return new PipResult(p.exitValue(), outBuf.snapshot(), errBuf.snapshot());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new PipResult(-1, "", "pip subprocess interrupted: " + e.getMessage());
            } catch (IOException e) {
                return new PipResult(-1, "", "pip subprocess failed: " + e.getMessage());
            }
        };
    }

    private static void drain(java.io.InputStream in, StreamBuffer buffer) {
        try (var br = new java.io.BufferedReader(
                new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
            br.lines().forEach(buffer::appendLine);
        } catch (@SuppressWarnings("unused") IOException ignored) {
            // best-effort drain; pip exit code is the source of truth
        }
    }

    /**
     * Thread-safe accumulator for one of pip's subprocess streams (stdout
     * or stderr). Encapsulates the internal lock so callers don't
     * synchronize on a method parameter — keeps the lint clean and the
     * lock invariant local.
     */
    private static final class StreamBuffer {
        private final StringBuilder sink = new StringBuilder();

        void appendLine(String line) {
            synchronized (sink) {
                sink.append(line).append('\n');
            }
        }

        String snapshot() {
            synchronized (sink) {
                return sink.toString();
            }
        }
    }

    /** Thrown when pip fails to install the requested packages. */
    public static class DependencyInstallException extends RuntimeException {
        public DependencyInstallException(String message) {
            super(message);
        }

        public DependencyInstallException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

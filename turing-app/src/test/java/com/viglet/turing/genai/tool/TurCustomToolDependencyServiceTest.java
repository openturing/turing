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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.viglet.turing.commons.utils.TurCommonsUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.viglet.turing.genai.tool.TurCustomToolDependencyService.DependencyInstallException;
import com.viglet.turing.genai.tool.TurCustomToolDependencyService.PipResult;
import com.viglet.turing.genai.tool.TurCustomToolDependencyService.PipRunner;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * Pure unit tests for {@link TurCustomToolDependencyService} — pins the
 * normalization, hash-keyed caching, and union-of-global-plus-agent
 * behavior without invoking real pip. The {@link PipRunner} is stubbed;
 * {@link TurGlobalSettingsService} is mocked.
 *
 * <p>No Spring boot, no DB, no subprocess. Each test runs in &lt;50 ms.
 *
 * <h2>What gets pinned here</h2>
 * <ul>
 *   <li>{@code normalize()} trims, dedupes, sorts, strips comments —
 *       semantically equivalent inputs produce identical normalized
 *       output (and identical hash).</li>
 *   <li>{@code ensureInstalled()} runs pip exactly once per unique
 *       {@code (global ∪ agent)} combination; second call with the same
 *       combo is a cache hit (no pip invocation).</li>
 *   <li>Different agent addendums under the same global produce different
 *       deps dirs (no cross-pollution).</li>
 *   <li>Same packages declared in both global and agent dedupe — one
 *       deps dir for both.</li>
 *   <li>Blank global + blank agent → returns null (no auto-install).</li>
 *   <li>Pip failure throws + does NOT write the marker; next call
 *       re-runs (cache pins only successes).</li>
 *   <li>Marker contents = hash (audit trail on disk).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
class TurCustomToolDependencyServiceTest {

    private TurGlobalSettingsService globalSettings;
    private CountingPipRunner pipRunner;
    private TurCustomToolDependencyService service;
    private Path storeRoot;

    @BeforeEach
    void setUp() throws Exception {
        globalSettings = Mockito.mock(TurGlobalSettingsService.class);
        Mockito.when(globalSettings.getPythonExecutable()).thenReturn("/usr/bin/python3");
        Mockito.when(globalSettings.getPythonRequirements()).thenReturn("");

        pipRunner = new CountingPipRunner();
        service = new TurCustomToolDependencyService(globalSettings, pipRunner);

        // The dep service installs into `./store/code-interpreter/_deps/<hash>/`.
        // Tests in the same JVM run share that dir — the FIRST test would
        // write a `.installed` marker that later tests using the same hash
        // would treat as a cache hit (zero pip invocations). Wipe the
        // entire `_deps/` tree before each test so every test starts
        // from a clean slate.
        storeRoot = Path.of(TurCommonsUtils.addSubDirToStoreDir(
                TurCustomToolDependencyService.DEPS_SUBDIR).getPath());
        deleteRecursively(storeRoot);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(java.io.File::delete);
        }
    }

    // ─────────────────────── Normalize ───────────────────────

    @Test
    void normalize_trimsLines_dropsBlanks_dropsCommentLines_sortsAndDedupes() {
        String raw = """
                # PDF rendering
                reportlab==4.0.7

                qrcode>=7.4
                  reportlab==4.0.7
                # inline comments dropped
                matplotlib  # for charts
                """;

        String normalized = TurCustomToolDependencyService.normalize(raw);

        assertThat(normalized)
                .as("Sort + dedup, drop blanks, strip both line and inline comments")
                .isEqualTo("matplotlib\nqrcode>=7.4\nreportlab==4.0.7\n");
    }

    @Test
    void normalize_emptyInput_returnsEmpty() {
        assertThat(TurCustomToolDependencyService.normalize(null)).isEmpty();
        assertThat(TurCustomToolDependencyService.normalize("")).isEmpty();
        assertThat(TurCustomToolDependencyService.normalize("  \n  \n# comment only\n")).isEmpty();
    }

    @Test
    void normalize_packagesInDifferentOrder_produceSameHash() {
        String a = "reportlab\nqrcode\nmatplotlib";
        String b = "matplotlib\nreportlab\nqrcode";
        assertThat(TurCustomToolDependencyService.normalize(a))
                .isEqualTo(TurCustomToolDependencyService.normalize(b));
    }

    // ─────────────────────── Blank → null ───────────────────────

    @Test
    void ensureInstalled_blankGlobalAndAgent_returnsNull_noPipCall() {
        Mockito.when(globalSettings.getPythonRequirements()).thenReturn("");

        assertThat(service.ensureInstalled(null)).isNull();
        assertThat(service.ensureInstalled("")).isNull();
        assertThat(service.ensureInstalled("  ")).isNull();
        assertThat(service.ensureInstalled("# only comments\n")).isNull();

        assertThat(pipRunner.invocationCount.get())
                .as("Nothing to install → no pip invocation")
                .isZero();
    }

    // ─────────────────────── Global only ───────────────────────

    @Test
    void ensureInstalled_globalOnly_runsPipOnce_thenCachesByHash() {
        Mockito.when(globalSettings.getPythonRequirements()).thenReturn("reportlab==4.0.7\nqrcode");

        Path first = service.ensureInstalled(null);
        Path second = service.ensureInstalled(null);
        Path third = service.ensureInstalled("");

        assertThat(first).isNotNull();
        assertThat(second).as("Cache hit returns same path").isEqualTo(first);
        assertThat(third).as("Blank extra equivalent to null — same cache key").isEqualTo(first);
        assertThat(pipRunner.invocationCount.get())
                .as("3 calls, 1 pip invocation (first); rest cache hits")
                .isEqualTo(1);
        assertThat(Files.exists(first.resolve(".installed")))
                .as("Marker file written on successful install")
                .isTrue();
    }

    // ─────────────────────── Global + Agent union ───────────────────────

    @Test
    void ensureInstalled_globalPlusAgent_unioned_andCachedAtUnionHash() throws Exception {
        Mockito.when(globalSettings.getPythonRequirements()).thenReturn("reportlab\nqrcode");

        Path withPandas = service.ensureInstalled("pandas==2.0");
        Path withPandasAgain = service.ensureInstalled("pandas==2.0");

        assertThat(withPandas).isNotNull();
        assertThat(withPandasAgain).isEqualTo(withPandas);
        assertThat(pipRunner.invocationCount.get())
                .as("Union hash hits once on first call, cache thereafter")
                .isEqualTo(1);

        // Verify the requirements.txt actually contains the union.
        String reqs = Files.readString(withPandas.resolve("requirements.txt"));
        assertThat(reqs)
                .as("requirements.txt must contain BOTH global and agent packages")
                .contains("reportlab")
                .contains("qrcode")
                .contains("pandas==2.0");
    }

    @Test
    void ensureInstalled_differentAgents_underSameGlobal_getDifferentDepsDirs() {
        Mockito.when(globalSettings.getPythonRequirements()).thenReturn("reportlab");

        Path agentA = service.ensureInstalled("pandas==2.0");
        Path agentB = service.ensureInstalled("scikit-learn==1.4");

        assertThat(agentA).isNotEqualTo(agentB);
        assertThat(pipRunner.invocationCount.get())
                .as("Two distinct union hashes → two pip invocations")
                .isEqualTo(2);
        // Both dirs sit under the same store root but in different
        // hash-named subdirs.
        assertThat(agentA.getParent()).isEqualTo(agentB.getParent());
    }

    @Test
    void ensureInstalled_samePackageDeclaredInBothGlobalAndAgent_dedupedInUnion() {
        Mockito.when(globalSettings.getPythonRequirements()).thenReturn("reportlab==4.0\nqrcode");

        // Agent re-declares reportlab — should be deduped by normalize().
        Path withDup = service.ensureInstalled("reportlab==4.0");
        // Same union when agent declares nothing (because dup is the same
        // version that's already in global) — both hash to the same key
        // since normalize() deduplicates.
        Path withoutDup = service.ensureInstalled(null);

        assertThat(withDup)
                .as("Duplicate package dedupes — same union hash as global-only")
                .isEqualTo(withoutDup);
        assertThat(pipRunner.invocationCount.get())
                .as("Only one install because the deduped union is identical")
                .isEqualTo(1);
    }

    // ─────────────────────── Failure semantics ───────────────────────

    @Test
    void ensureInstalled_pipFailure_throws_markerNotWritten_nextCallRetries() {
        Mockito.when(globalSettings.getPythonRequirements()).thenReturn("nonexistent-package-xyz");
        pipRunner.cannedResult = new PipResult(1, "", "ERROR: Could not find a version that satisfies the requirement nonexistent-package-xyz");

        assertThatThrownBy(() -> service.ensureInstalled(null))
                .isInstanceOf(DependencyInstallException.class)
                .hasMessageContaining("pip install failed")
                .hasMessageContaining("nonexistent-package-xyz");

        assertThat(pipRunner.invocationCount.get()).isEqualTo(1);

        // The failed call's dir might exist (we wrote requirements.txt)
        // but MUST NOT have the .installed marker — next call will retry.
        pipRunner.cannedResult = new PipResult(0, "", ""); // pretend pip works now
        Path retry = service.ensureInstalled(null);

        assertThat(retry).isNotNull();
        assertThat(pipRunner.invocationCount.get())
                .as("Marker absent → retry re-invokes pip")
                .isEqualTo(2);
        assertThat(Files.exists(retry.resolve(".installed")))
                .as("Marker now present after successful retry")
                .isTrue();
    }

    @Test
    void ensureInstalled_pipFailure_stderrSurfacedInExceptionMessage() {
        Mockito.when(globalSettings.getPythonRequirements()).thenReturn("foo");
        pipRunner.cannedResult = new PipResult(2, "", "Pip-side error message visible here");

        assertThatThrownBy(() -> service.ensureInstalled(null))
                .isInstanceOf(DependencyInstallException.class)
                .hasMessageContaining("Pip-side error message visible here");
    }

    @Test
    void ensureInstalled_noPythonExecutable_throwsClearError() {
        Mockito.when(globalSettings.getPythonExecutable()).thenReturn("");
        Mockito.when(globalSettings.getPythonRequirements()).thenReturn("reportlab");

        assertThatThrownBy(() -> service.ensureInstalled(null))
                .isInstanceOf(DependencyInstallException.class)
                .hasMessageContaining("No Python executable configured");
        assertThat(pipRunner.invocationCount.get())
                .as("Bail BEFORE calling pip — no python means no point trying")
                .isZero();
    }

    // ─────────────────────── Marker contract ───────────────────────

    @Test
    void ensureInstalled_markerFileContentsEqualHash() throws Exception {
        Mockito.when(globalSettings.getPythonRequirements()).thenReturn("reportlab");

        Path depsDir = service.ensureInstalled(null);

        String markerContent = Files.readString(depsDir.resolve(".installed")).trim();
        String expectedHash = depsDir.getFileName().toString();
        assertThat(markerContent)
                .as("Marker file content = SHA-256 hex of the normalized requirements (audit trail)")
                .isEqualTo(expectedHash);
    }

    // ─────────────────────── Concurrency guard ───────────────────────

    @Test
    void ensureInstalled_concurrentCallsForSameHash_runPipOnlyOnce() throws Exception {
        Mockito.when(globalSettings.getPythonRequirements()).thenReturn("reportlab");
        // Simulate slow pip so threads pile up on the lock.
        pipRunner.delayMillis = 100;

        int threads = 16;
        Thread[] workers = new Thread[threads];
        List<Path> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            workers[i] = Thread.ofVirtual().start(() -> {
                Path p = service.ensureInstalled(null);
                synchronized (results) {
                    results.add(p);
                }
            });
        }
        for (Thread t : workers) t.join();

        assertThat(results)
                .hasSize(threads)
                .allMatch(p -> p.equals(results.get(0)));
        assertThat(pipRunner.invocationCount.get())
                .as("Lock prevents N concurrent installs of the same hash — exactly 1 pip call")
                .isEqualTo(1);
    }

    // ─────────────────────── Helpers ───────────────────────

    /**
     * Simple {@link PipRunner} stub: counts invocations, optionally delays,
     * returns a canned {@link PipResult}.
     */
    static class CountingPipRunner implements PipRunner {
        final AtomicInteger invocationCount = new AtomicInteger();
        PipResult cannedResult = new PipResult(0, "", ""); // default: success
        long delayMillis;

        @Override
        public PipResult run(List<String> command, int timeoutSeconds) {
            invocationCount.incrementAndGet();
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return cannedResult;
        }
    }
}

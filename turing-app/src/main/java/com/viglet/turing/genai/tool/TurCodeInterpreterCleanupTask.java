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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.viglet.turing.commons.utils.TurCommonsUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled sweeper for the Code Interpreter session directories.
 *
 * <p>The {@link TurCodeInterpreterToolService} writes per-call session
 * dirs at
 * {@code store/code-interpreter/sessions/YYYY-MM-DD/{sessionId}/}. They
 * accumulate forever otherwise — a moderately busy site (1k chats/day,
 * 30% generating a file each) leaks ~50 MB/day. This task deletes
 * entire date buckets older than the configured TTL.
 *
 * <p>The persistent {@code _deps/} pip cache (see
 * {@link TurCustomToolDependencyService}) lives at a sibling path and
 * is intentionally NOT touched — those packages are referenced by
 * subsequent invocations and a separate v2 GC will manage them based
 * on hash-LRU semantics.
 *
 * <h2>Configuration</h2>
 * <table>
 *   <tr>
 *     <th>Property</th><th>Default</th><th>Meaning</th>
 *   </tr><tr>
 *     <td>{@code turing.code-interpreter.cleanup.enabled}</td>
 *     <td>{@code true}</td>
 *     <td>Master switch — set to {@code false} during forensic debug
 *         where you want session files preserved for inspection.</td>
 *   </tr><tr>
 *     <td>{@code turing.code-interpreter.cleanup.session-ttl-hours}</td>
 *     <td>24</td>
 *     <td>Session dirs whose date bucket is older than this get
 *         deleted. The grain is days (the bucket is named by date),
 *         so values below 24h round up to 1 day.</td>
 *   </tr><tr>
 *     <td>{@code turing.code-interpreter.cleanup.fixed-delay-ms}</td>
 *     <td>3600000 (1h)</td>
 *     <td>How often the sweeper runs after the previous run finishes.</td>
 *   </tr>
 * </table>
 *
 * <p>Safety: the sweeper only acts on date buckets whose name parses
 * cleanly as an ISO {@code yyyy-MM-dd} date. Unknown dirs (manual
 * operator drops, accidental files) are left untouched so an admin
 * dropping a sentinel file into the sandbox to "pin" something never
 * loses it.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@Component
public class TurCodeInterpreterCleanupTask {

    static final String SANDBOX_DIR = "code-interpreter";
    static final String SESSIONS_DIR = "sessions";
    static final String TENANTS_DIR = "tenants";

    @Value("${turing.code-interpreter.cleanup.enabled:true}")
    private boolean enabled;

    @Value("${turing.code-interpreter.cleanup.session-ttl-hours:24}")
    private int sessionTtlHours;

    /**
     * Sweeps date-bucketed session dirs older than the TTL. Runs on the
     * configured fixed delay; if a sweep takes longer than the delay,
     * the next run waits for it to finish (Spring's default
     * {@code @Scheduled} semantics).
     */
    @Scheduled(fixedDelayString = "${turing.code-interpreter.cleanup.fixed-delay-ms:3600000}",
            initialDelayString = "${turing.code-interpreter.cleanup.initial-delay-ms:60000}")
    public void sweep() {
        if (!enabled) {
            log.debug("[CodeInterpreterCleanup] disabled via config — skipping sweep");
            return;
        }
        File sandboxRoot = TurCommonsUtils.addSubDirToStoreDir(SANDBOX_DIR);
        LocalDate cutoff = LocalDate.now().minusDays(Math.max(1, sessionTtlHours / 24));
        Stats total = new Stats();
        // Legacy flat layout: sessions/YYYY-MM-DD/{sid}/
        sweepDateBucketedTree(new File(sandboxRoot, SESSIONS_DIR), cutoff, total);
        // Tenant-isolated layout: tenants/{agentId}/{conversationId}/YYYY-MM-DD/{sid}/
        sweepTenantTree(new File(sandboxRoot, TENANTS_DIR), cutoff, total);
        if (total.deletedBuckets > 0) {
            log.info("[CodeInterpreterCleanup] swept {} bucket(s) | freed ~{} MB | ttl={}h | cutoff={}",
                    total.deletedBuckets, (total.deletedBytes / 1_048_576),
                    sessionTtlHours, cutoff);
        }
    }

    /**
     * Walks {@code tenants/{agentId}/{conversationId}/} and sweeps the
     * date-bucketed subtree inside each conversation. Empty parent dirs
     * (no surviving date buckets after the sweep) are also pruned so
     * deleted-agent footprints don't linger forever.
     */
    private void sweepTenantTree(File tenantsRoot, LocalDate cutoff, Stats stats) {
        if (!tenantsRoot.isDirectory()) return;
        File[] agentDirs = tenantsRoot.listFiles(File::isDirectory);
        if (agentDirs == null) return;
        for (File agentDir : agentDirs) {
            File[] convDirs = agentDir.listFiles(File::isDirectory);
            if (convDirs == null) continue;
            for (File convDir : convDirs) {
                sweepDateBucketedTree(convDir, cutoff, stats);
                // Prune empty conversation dir.
                if (isEmptyDirectory(convDir)) convDir.delete();
            }
            // Prune empty agent dir.
            if (isEmptyDirectory(agentDir)) agentDir.delete();
        }
    }

    /**
     * Walks {@code root/YYYY-MM-DD/...} and deletes buckets whose date
     * is strictly before {@code cutoff}. Non-date sibling dirs (operator
     * scratch space) are left untouched.
     */
    private void sweepDateBucketedTree(File root, LocalDate cutoff, Stats stats) {
        if (!root.isDirectory()) return;
        File[] buckets = root.listFiles(File::isDirectory);
        if (buckets == null) return;
        for (File bucket : buckets) {
            LocalDate bucketDate;
            try {
                bucketDate = LocalDate.parse(bucket.getName());
            } catch (DateTimeParseException e) {
                // Unknown subdir — leave alone. Could be operator scratch
                // space or a sentinel file the team uses for QA.
                continue;
            }
            // Keep buckets with date >= cutoff (inclusive). At TTL=24h
            // cutoff is yesterday, so today + yesterday both survive;
            // anything from 2+ days ago is deleted. The inclusive
            // boundary is intentional — a bucket dated "yesterday"
            // could be as recent as a minute ago.
            if (!bucketDate.isBefore(cutoff)) continue;
            long sizeBefore = sizeOf(bucket.toPath());
            if (deleteRecursively(bucket.toPath())) {
                stats.deletedBuckets++;
                stats.deletedBytes += sizeBefore;
            }
        }
    }

    private static boolean isEmptyDirectory(File dir) {
        if (!dir.isDirectory()) return false;
        File[] children = dir.listFiles();
        return children == null || children.length == 0;
    }

    /** Aggregated counters threaded through the recursive sweep. */
    private static final class Stats {
        long deletedBuckets;
        long deletedBytes;
    }

    private static long sizeOf(Path root) {
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(p -> p.toFile().length())
                    .sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * Best-effort recursive delete. Returns true when the root was
     * removed. Files locked by another process (e.g. an in-flight
     * download on Windows) are skipped — the next sweep retries.
     */
    private static boolean deleteRecursively(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            log.warn("[CodeInterpreterCleanup] failed to walk {}: {}", root, e.getMessage());
            return false;
        }
        return !Files.exists(root);
    }

    // Test hooks — package-private so the same-package test can probe
    // the sweep without re-instantiating the bean.
    void setEnabledForTest(boolean v) { this.enabled = v; }
    void setTtlHoursForTest(int v) { this.sessionTtlHours = v; }

    /** Diagnostic: how long the configured TTL maps to in real time. */
    Duration getTtl() { return Duration.ofHours(Math.max(1, sessionTtlHours)); }
}

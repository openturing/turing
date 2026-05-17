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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.utils.TurCommonsUtils;

/**
 * Pure tests for {@link TurCodeInterpreterCleanupTask} — pins the
 * date-bucketed sweep contract:
 *
 * <ul>
 *   <li>Buckets older than the TTL get deleted;</li>
 *   <li>Buckets within the TTL stay;</li>
 *   <li>Non-date sibling dirs (operator scratch, sentinel files) are
 *       NEVER touched — only ISO-{@code yyyy-MM-dd} bucket names are
 *       candidates;</li>
 *   <li>The {@code _deps/} pip cache (sibling to {@code sessions/})
 *       is untouched even when ancient.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
class TurCodeInterpreterCleanupTaskTest {

    private TurCodeInterpreterCleanupTask task;
    private Path sandboxRoot;
    private Path sessionsRoot;
    private Path depsRoot;

    @BeforeEach
    void setUp() throws IOException {
        task = new TurCodeInterpreterCleanupTask();
        task.setEnabledForTest(true);
        task.setTtlHoursForTest(24);
        sandboxRoot = Path.of(TurCommonsUtils.addSubDirToStoreDir(
                TurCodeInterpreterCleanupTask.SANDBOX_DIR).getPath());
        sessionsRoot = sandboxRoot.resolve(TurCodeInterpreterCleanupTask.SESSIONS_DIR);
        depsRoot = sandboxRoot.resolve("_deps");
        deleteRecursively(sessionsRoot);
        deleteRecursively(depsRoot);
        Files.createDirectories(sessionsRoot);
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(sessionsRoot);
        deleteRecursively(depsRoot);
    }

    @Test
    void sweep_deletesBucketsOlderThanTtl_keepsRecent() throws IOException {
        // Layout (TTL = 24h → cutoff = yesterday inclusive):
        //   sessions/<today>/         keep
        //   sessions/<yesterday>/     keep (boundary — cutoff is yesterday)
        //   sessions/<3 days ago>/    DELETE
        //   sessions/<10 days ago>/   DELETE
        LocalDate today = LocalDate.now();
        Path keep1 = makeBucketWithFile(today, "alive.pdf");
        Path keep2 = makeBucketWithFile(today.minusDays(1), "recent.pdf");
        Path old1 = makeBucketWithFile(today.minusDays(3), "stale.pdf");
        Path old2 = makeBucketWithFile(today.minusDays(10), "ancient.pdf");

        task.sweep();

        assertThat(Files.exists(keep1)).as("today's bucket survives").isTrue();
        assertThat(Files.exists(keep2)).as("yesterday's bucket survives (boundary)").isTrue();
        assertThat(Files.exists(old1)).as("3-day-old bucket deleted").isFalse();
        assertThat(Files.exists(old2)).as("10-day-old bucket deleted").isFalse();
    }

    @Test
    void sweep_leavesNonDateDirsAlone() throws IOException {
        // Operator scratch dir / sentinel file — NEVER auto-deleted.
        Path scratch = sessionsRoot.resolve("scratch-do-not-delete");
        Files.createDirectories(scratch);
        Files.writeString(scratch.resolve("note.txt"), "ops sentinel");

        task.sweep();

        assertThat(Files.exists(scratch))
                .as("Non-date-named dirs are untouched (operator sentinel safety)")
                .isTrue();
        assertThat(Files.exists(scratch.resolve("note.txt"))).isTrue();
    }

    @Test
    void sweep_doesNotTouchDepsCache_evenWhenAncient() throws IOException {
        // The pip cache lives at code-interpreter/_deps/<hash>/ — sibling
        // to code-interpreter/sessions/. The cleanup task must NEVER walk
        // into _deps because installed packages are referenced by future
        // Python runs and re-installing is expensive (~10-30s per pip
        // call). Simulate an old dir there and verify it survives.
        Files.createDirectories(depsRoot.resolve("ancienthash"));
        Files.writeString(depsRoot.resolve("ancienthash").resolve(".installed"), "x");
        // Make also a "date-named" dir under _deps (defensive — _deps/2026-01-01/
        // would NEVER happen in practice, but verify the task scans only sessions/).
        Files.createDirectories(depsRoot.resolve("2020-01-01"));

        task.sweep();

        assertThat(Files.exists(depsRoot.resolve("ancienthash")))
                .as("Persistent pip cache untouched by session sweep")
                .isTrue();
        assertThat(Files.exists(depsRoot.resolve("2020-01-01")))
                .as("Even a date-named dir under _deps stays — sweeper only walks sessions/")
                .isTrue();
    }

    @Test
    void sweep_disabled_doesNothing() throws IOException {
        task.setEnabledForTest(false);
        Path old = makeBucketWithFile(LocalDate.now().minusDays(30), "would-delete.pdf");

        task.sweep();

        assertThat(Files.exists(old))
                .as("Disabled sweeper must not touch anything")
                .isTrue();
    }

    @Test
    void sweep_emptySessionsRoot_isNoop() {
        // sessionsRoot exists but is empty — sweep must not throw.
        task.sweep();
    }

    @Test
    void sweep_noSessionsRoot_isNoop() throws IOException {
        // sessionsRoot doesn't exist yet (fresh install before any tool ran).
        deleteRecursively(sessionsRoot);

        task.sweep(); // must not throw
    }

    @Test
    void sweep_ttlHoursBelowOneDay_roundsUpToOneDay() throws IOException {
        // Configured TTL = 6h (sub-day). Date buckets are 1-day grain
        // so anything below 24h rounds up to 1 day — equivalent to TTL=24h.
        // Cutoff = yesterday; keep today + yesterday, delete 2-days-old.
        task.setTtlHoursForTest(6);
        Path today = makeBucketWithFile(LocalDate.now(), "x.pdf");
        Path yesterday = makeBucketWithFile(LocalDate.now().minusDays(1), "y.pdf");
        Path twoDays = makeBucketWithFile(LocalDate.now().minusDays(2), "z.pdf");

        task.sweep();

        assertThat(Files.exists(today)).isTrue();
        assertThat(Files.exists(yesterday)).as("Boundary day (= cutoff) survives — inclusive keep").isTrue();
        assertThat(Files.exists(twoDays)).isFalse();
    }

    // ─────────────────────── Helpers ───────────────────────

    private Path makeBucketWithFile(LocalDate date, String filename) throws IOException {
        Path bucket = sessionsRoot.resolve(date.toString());
        Path session = bucket.resolve("abcd1234");
        Files.createDirectories(session);
        Files.writeString(session.resolve(filename), "test payload");
        return bucket;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}

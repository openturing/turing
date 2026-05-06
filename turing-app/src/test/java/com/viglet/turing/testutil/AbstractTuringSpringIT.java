/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.testutil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for {@code *IT.java} integration tests that boot the full
 * Spring context. Each subclass gets its own ephemeral in-memory H2
 * database so test data never leaks into the dev file store at
 * {@code ./store/db/turingDB} (the default configured in
 * {@code application.yaml}).
 *
 * <p>The unique database name is generated once per JVM at class load
 * time, so all tests in the same class share one in-memory DB (Liquibase
 * runs once at context init), but two separate IT classes never collide.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@SpringBootTest(properties = "spring.jmx.enabled=true")
public abstract class AbstractTuringSpringIT {

    private static final String DIR_PREFIX = "turing-it-";

    /**
     * Per-JVM-run temp directory holding the H2 file. Living under
     * {@link System#getProperty(String) java.io.tmpdir} means the OS reaps
     * it eventually and it never collides with the dev store. File-based
     * was chosen over {@code jdbc:h2:mem:} after measurement: cold context
     * startup with 380+ Liquibase changesets is roughly 10× faster on a
     * file-backed H2 (the engine reuses page cache across statements,
     * the in-memory variant doesn't).
     */
    private static final Path DB_DIR = Paths.get(System.getProperty("java.io.tmpdir"),
            DIR_PREFIX + UUID.randomUUID());

    static {
        // Best-effort: sweep up dirs left over from previous runs that
        // were killed before the shutdown hook below could fire.
        sweepStaleItDirs();
        // Normal exit path — delete THIS run's dir on shutdown.
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> deleteRecursively(DB_DIR), "turing-it-cleanup"));
    }

    @DynamicPropertySource
    static void overrideDatasourceWithEphemeralH2(DynamicPropertyRegistry registry) {
        String url = "jdbc:h2:file:" + DB_DIR.resolve("turingDB").toString().replace('\\', '/')
                + ";DATABASE_TO_UPPER=false"
                + ";CASE_INSENSITIVE_IDENTIFIERS=true";
        registry.add("spring.datasource.url", () -> url);
    }

    /**
     * Deletes every {@code turing-it-*} directory under
     * {@code java.io.tmpdir} that isn't ours. A previous IT run that was
     * {@code kill -9}'d (or whose JVM crashed before
     * {@link Runtime#addShutdownHook} fired) leaves the directory behind;
     * we wipe it on the next run so the temp dir doesn't grow without bound.
     */
    private static void sweepStaleItDirs() {
        Path tmp = Paths.get(System.getProperty("java.io.tmpdir"));
        if (!Files.isDirectory(tmp)) {
            return;
        }
        try (Stream<Path> entries = Files.list(tmp)) {
            entries.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith(DIR_PREFIX))
                    .filter(p -> !p.equals(DB_DIR))
                    .forEach(AbstractTuringSpringIT::deleteRecursively);
        } catch (IOException ignored) {
            // Sweep is best-effort — never fail a test because cleanup raced.
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // The H2 file may still be locked momentarily on Windows;
                    // the OS will reap the orphan on the next run's sweep.
                }
            });
        } catch (IOException ignored) {
            // Same as above — best effort.
        }
    }
}

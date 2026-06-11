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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
     * Classpath location of an optional H2 snapshot copied from a running
     * Turing instance ({@code turing-app/store/db/turingDB.mv.db}). When
     * present, ITs boot against the snapshot — meaning persona catalog,
     * Custom Tool definitions, MCP servers, agent ↔ tool bindings, RAG
     * configuration, indexed sites etc. are all already there, just as
     * the developer configured them in the dev console. Without the
     * snapshot the IT falls back to a fully-empty H2 and Liquibase runs
     * the full migration set from scratch — the original behavior.
     *
     * <p>Liquibase still runs on top of the snapshot at context startup:
     * its {@code databasechangelog} table tracks applied changesets, so
     * only NEW changesets added since the snapshot was taken get applied.
     * This keeps schema drift in check without invalidating the snapshot.
     */
    private static final String SNAPSHOT_RESOURCE = "/h2-snapshot/turingDB.mv.db";
    private static final String H2_DB_FILE = "turingDB.mv.db";

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
        // Seed the snapshot into the per-run dir BEFORE Spring boots. The
        // datasource URL points at {@code <DB_DIR>/turingDB} so the snapshot
        // file has to land there with the right name before the first DB
        // connection opens.
        seedSnapshotIfPresent();
        // Normal exit path — delete THIS run's dir on shutdown.
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> deleteRecursively(DB_DIR), "turing-it-cleanup"));
    }

    /**
     * Copies the bundled H2 snapshot into {@link #DB_DIR} when the classpath
     * resource is present. No-op when the resource is absent — ITs run
     * against a freshly-Liquibase-migrated empty DB just like before. The
     * copy is best-effort: any IO error is logged-and-skipped so a missing
     * or corrupt snapshot never stops the test run.
     */
    private static void seedSnapshotIfPresent() {
        try (InputStream in = AbstractTuringSpringIT.class.getResourceAsStream(SNAPSHOT_RESOURCE)) {
            if (in == null) {
                return;
            }
            Files.createDirectories(DB_DIR);
            Path target = DB_DIR.resolve(H2_DB_FILE);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.printf("[IT] Seeded H2 from snapshot %s → %s (%d bytes)%n",
                    SNAPSHOT_RESOURCE, target, Files.size(target));
        } catch (IOException e) {
            System.err.printf("[IT] Could not seed H2 snapshot (%s) — falling back to empty DB%n",
                    e.getMessage());
        }
    }

    @DynamicPropertySource
    static void overrideDatasourceWithEphemeralH2(DynamicPropertyRegistry registry) {
        String url = "jdbc:h2:file:" + DB_DIR.resolve("turingDB").toString().replace('\\', '/')
                + ";DATABASE_TO_UPPER=false"
                + ";CASE_INSENSITIVE_IDENTIFIERS=true";
        registry.add("spring.datasource.url", () -> url);
    }

    /**
     * Routes the embedded Artemis journal/bindings/paging/lock files to a
     * per-JVM directory under {@link #DB_DIR} AND disables persistence.
     *
     * <p><b>Why per-JVM dir matters</b>: the production default
     * ({@code store/queue}) is a CWD-relative path, so multiple JVMs
     * (the running app + every forked Surefire IT) would compete for the
     * same {@code server.lock} file and serialize via
     * {@code FileLockNodeManager}'s 60-second retry loop — observed in the
     * wild as a 5+ minute IT hang on Spring context startup with no error
     * logged.
     *
     * <p><b>Why persistence is also disabled</b>: even with per-JVM dirs,
     * Spring Test caches multiple {@code ApplicationContext}s within ONE
     * Maven fork (IT classes with distinct {@code @Import}/{@code @MockitoSpyBean}
     * configs each load their own context, AND Spring keeps prior contexts
     * alive in the cache for reuse). Each cached context owns its own
     * {@code EmbeddedActiveMQ} bean — and they all share {@link #DB_DIR}
     * because it is computed once per JVM. Result: context B's Artemis
     * blocks for ~minutes trying to acquire the journal lock that context A
     * still holds. Stack trace pattern:
     *
     * <pre>
     *   at o.a.a.a.core.server.impl.FileLockNodeManager.lock(FileLockNodeManager.java:433)
     *   at o.a.a.a.core.server.impl.FileLockNodeManager.startPrimaryNode(...:253)
     *   at o.a.a.a.core.server.embedded.EmbeddedActiveMQ.start(EmbeddedActiveMQ.java:124)
     * </pre>
     *
     * <p>Setting {@code persistent=false} skips the {@code FileLockNodeManager}
     * code path entirely — Artemis runs purely in-memory in test JVMs.
     * Production behavior is untouched. The data-directory property is still
     * pointed at the per-JVM path as defense-in-depth: if a future Artemis
     * version writes ANY file regardless of the persistence flag (e.g. for
     * paging temp files), it lands under {@link #DB_DIR} where the shutdown
     * hook can clean it up.
     */
    @DynamicPropertySource
    static void overrideArtemisForTestIsolation(DynamicPropertyRegistry registry) {
        String artemisDir = DB_DIR.resolve("queue").toString().replace('\\', '/');
        registry.add("spring.artemis.embedded.data-directory", () -> artemisDir);
        registry.add("spring.artemis.embedded.persistent", () -> "false");
        // Multiple distinct @SpringBootTest contexts can be cached in ONE Maven
        // fork (ITs with different @DynamicPropertySource values each load their
        // own context). With spring.jmx.enabled=true they would otherwise collide
        // on the fixed Hikari `dataSource` MBean name
        // (InstanceAlreadyExistsException → context load failure). Unique JMX
        // names let the contexts coexist.
        registry.add("spring.jmx.unique-names", () -> "true");
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

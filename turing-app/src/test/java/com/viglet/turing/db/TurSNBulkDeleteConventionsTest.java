/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * T899 / §LXVIII.7 — <strong>no bulk delete on an entity whose removal has to run a
 * callback.</strong>
 *
 * <p>
 * T896 hooked JPA lifecycle callbacks precisely so that no write path could forget to
 * evict: the callback fires whichever service saved the entity. JPA has one hole in that
 * argument, and it is a hole in JPA rather than in the wiring — lifecycle callbacks fire
 * for entities the persistence context manages, and a bulk operation manages none.
 * {@code deleteAllInBatch(…)} and {@code @Modifying @Query("delete …")} issue SQL
 * directly, so {@code @PostRemove} never runs.
 * </p>
 *
 * <p>
 * That was not hypothetical. Five {@code deleteAllInBatch} calls in
 * {@code TurSNSpotlightProcess} deleted spotlights without evicting anything, and two
 * {@code @Modifying} deletes on {@code TurSNSiteFieldExtRepository} did the same for field
 * configuration — the second of which also bypassed the *pre-existing* snapshot eviction
 * listener, so that one had been leaving a stale snapshot since well before the search
 * cache existed. Both call sites had already loaded the rows they were deleting, which
 * made the batched form pure loss: {@code deleteAll} costs no extra query there.
 * </p>
 *
 * <p>
 * This lint reads the sources rather than the bytecode, because what it forbids is a
 * <em>call</em> and an annotation, not a shape in a class file. It is deliberately blunt:
 * any bulk delete anywhere in the SN packages fails, and the exemption is a code comment
 * naming this test, so an author who genuinely needs one has to say why in the diff.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNBulkDeleteConventionsTest {

    /** Bulk delete calls that bypass the persistence context, and so every callback. */
    private static final Pattern BATCH_DELETE_CALL =
            Pattern.compile("\\.delete(All)?InBatch\\s*\\(");

    /** A repository-declared bulk delete: {@code @Modifying} over a {@code delete} JPQL. */
    private static final Pattern MODIFYING_DELETE_QUERY = Pattern.compile(
            "@Modifying[^;]*?@Query\\s*\\(\\s*\"\\s*delete\\s", Pattern.DOTALL);

    /**
     * The escape hatch: one occurrence of this marker exempts one bulk delete in the same
     * file. Counted rather than merely present, so an exemption written for one call cannot
     * silently cover a second one added later. Spelled out in the source so the reason is
     * visible in review rather than implied.
     */
    private static final String ALLOW_MARKER = "bulk-delete-ok";

    /**
     * Packages whose entities carry an eviction callback
     * ({@code TurSNSearchCacheEvictionListener} / {@code TurSNSiteSnapshotEvictionListener}),
     * plus the services that write them.
     */
    private static final List<String> GUARDED_ROOTS = List.of(
            "src/main/java/com/viglet/turing/persistence/repository/sn",
            "src/main/java/com/viglet/turing/persistence/model/sn",
            "src/main/java/com/viglet/turing/sn",
            "src/main/java/com/viglet/turing/api/sn");

    @Test
    void noBulkDeleteWhereRemovalMustFireACallback() throws IOException {
        Set<String> violations = new TreeSet<>();

        for (String root : GUARDED_ROOTS) {
            Path dir = Paths.get(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".java"))
                        .forEach(p -> scan(p, violations));
            }
        }

        assertThat(violations)
                .as("""
                        A bulk delete manages no entity, so no @PostRemove fires and the caches that
                        depend on it are left stale (T899 / §LXVIII.7). Load the rows and use
                        delete(entity) / deleteAll(iterable) — every site that hit this had already
                        loaded them, so it costs no extra query. If a batch really is required,
                        evict explicitly and mark the line '%s' with the reason. Offenders:"""
                        .formatted(ALLOW_MARKER))
                .isEmpty();
    }

    private static void scan(Path javaFile, Set<String> violations) {
        try {
            String source = Files.readString(javaFile, StandardCharsets.UTF_8);
            String name = javaFile.getFileName().toString();
            long batched = count(BATCH_DELETE_CALL, source);
            long declared = count(MODIFYING_DELETE_QUERY, source);
            long offences = batched + declared;
            if (offences == 0) {
                return;
            }
            // One marker per offence, not one per file: a single exemption must not
            // silently cover a second bulk delete added to the same file later.
            long allowed = source.split(Pattern.quote(ALLOW_MARKER), -1).length - 1L;
            if (offences <= allowed) {
                return;
            }
            violations.add("%s — %d bulk delete(s) (%d batched call(s), %d declared query(ies)), "
                    .formatted(name, offences, batched, declared)
                    + "%d marked '%s'".formatted(allowed, ALLOW_MARKER));
        } catch (IOException ignored) {
            // A source file we cannot read cannot be asserted on.
        }
    }

    private static long count(Pattern pattern, String source) {
        return pattern.matcher(source).results().count();
    }
}

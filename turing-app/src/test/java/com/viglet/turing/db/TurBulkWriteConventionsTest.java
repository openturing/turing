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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

import jakarta.persistence.EntityListeners;

/**
 * <strong>No bulk write on an entity whose lifecycle has to run a callback.</strong>
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
 * That was never hypothetical. T899 found five {@code deleteAllInBatch} calls and two
 * {@code @Modifying} deletes in the SN packages; T906 found three more entities outside
 * them, two already patched by hand under comments diagnosing this exact bug and one
 * silently broken. The lint has been widened twice and narrowed once, and this file is the
 * record of what each pass learned.
 * </p>
 *
 * <p>
 * <b>T909 — the rule is {@code @Modifying}, not the word "delete".</b> Every task in this
 * block was about deletes, because that is what was broken; the listeners declare
 * {@code @PostPersist}, {@code @PostUpdate} <em>and</em> {@code @PostRemove}, so a bulk
 * <em>update</em> skips {@code @PostUpdate} by the same mechanism and the earlier
 * verb-matching version said nothing about it. Matching the verb was always a proxy:
 * {@code @Modifying} exists precisely to mark a query run through
 * {@code Query.executeUpdate()}, which is the bulk-DML path that manages no entity. So the
 * annotation is the rule, and no verb is parsed.
 * </p>
 *
 * <p>
 * <b>T908 — annotations are read from bytecode, calls from source, and the split is the
 * point.</b> Verifying T907 by injecting a violation exposed the previous version's limit:
 * a probe written {@code @org.springframework.data.jpa.repository.Modifying} walked past a
 * regex looking for {@code @Modifying}, and that spelling is one this codebase already
 * uses about fifty times. An annotation can be written many ways and means one thing, so
 * it is read where that is already resolved — {@link MetadataReader}, the same mechanism
 * {@code TurCacheAnnotationConventionsTest} uses. A <em>call</em> is the opposite: its
 * simple name must appear literally at the call site, whatever the imports say, so source
 * text is a sound signal for it and buys a readable escape hatch that bytecode would not.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurBulkWriteConventionsTest {

    /** Bulk delete calls that bypass the persistence context, and so every callback. */
    private static final Pattern BATCH_DELETE_CALL =
            Pattern.compile("\\.delete(All)?InBatch\\s*\\(");

    /**
     * The entity a repository manages, read from its own declaration. Tolerant of a
     * qualified base interface and a qualified type argument, so the mapping cannot be
     * escaped the way the annotation match could.
     */
    private static final Pattern MANAGED_ENTITY = Pattern.compile(
            "extends\\s+(?:[\\w.]*\\.)?JpaRepository\\s*<\\s*([\\w.]+)\\s*,", Pattern.DOTALL);

    /**
     * The escape hatch for a <em>call</em>: one occurrence of this marker exempts one bulk
     * delete in the same file. Counted rather than merely present, so an exemption written
     * for one call cannot silently cover a second one added later.
     */
    private static final String ALLOW_MARKER = "bulk-delete-ok";

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");
    private static final Path CLASSES_ROOT = Paths.get("target/classes");
    private static final Path REPOSITORY_ROOT =
            SOURCE_ROOT.resolve("com/viglet/turing/persistence/repository");

    private static final String MODIFYING = "org.springframework.data.jpa.repository.Modifying";

    // ------------------------------------------------------------------
    // The guarded set
    // ------------------------------------------------------------------

    /**
     * Repository simple names whose managed entity declares {@code @EntityListeners}.
     *
     * <p>
     * T906 — derived, not listed: a guarded list written from where a bug was found is a
     * list that stops at the bug. T907 — and derived from the <em>declaration</em>
     * ({@code extends JpaRepository<Entity, Id>}), not from the {@code <Entity>Repository}
     * file-name convention, which held everywhere and was held by nothing.
     * </p>
     */
    private static Map<String, String> guardedRepositories() throws IOException {
        Set<String> entitiesWithListeners = entitiesDeclaringListeners();
        Map<String, String> guarded = new HashMap<>();
        try (Stream<Path> paths = Files.walk(REPOSITORY_ROOT)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .forEach(p -> managedEntity(p)
                            .filter(entitiesWithListeners::contains)
                            .ifPresent(entity -> guarded
                                    .put(p.getFileName().toString().replace(".java", ""), entity)));
        }
        return guarded;
    }

    /** Read from bytecode: {@code @EntityListeners} cannot be spelled around. */
    private static Set<String> entitiesDeclaringListeners() throws IOException {
        MetadataReaderFactory readers = new SimpleMetadataReaderFactory();
        Set<String> entities = new TreeSet<>();
        Path entityClasses = CLASSES_ROOT.resolve("com/viglet/turing/persistence/model");
        try (Stream<Path> paths = Files.walk(entityClasses)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .forEach(p -> readClass(readers, p).ifPresent(reader -> {
                        if (reader.getAnnotationMetadata().isAnnotated(EntityListeners.class
                                .getName())) {
                            String name = reader.getClassMetadata().getClassName();
                            entities.add(name.substring(name.lastIndexOf('.') + 1));
                        }
                    }));
        }
        return entities;
    }

    // ------------------------------------------------------------------
    // The rules
    // ------------------------------------------------------------------

    @Test
    void theDerivationFindsTheRepositoriesItMustGuard() throws IOException {
        assertThat(guardedRepositories().keySet())
                .as("derived from @EntityListeners (bytecode) + extends JpaRepository<Entity, Id>")
                .contains("TurSNSiteFieldExtRepository", "TurAIAgentRepository",
                        "TurChatFlowRepository", "TurPersonaRepository");
    }

    /**
     * A guarded repository must declare no {@code @Modifying} query at all. Read from
     * bytecode, so the annotation's identity is what is checked rather than its spelling.
     */
    @Test
    void noGuardedRepositoryDeclaresABulkWrite() throws IOException {
        Map<String, String> guarded = guardedRepositories();
        assertThat(guarded).as("the derivation found nothing to guard").isNotEmpty();

        MetadataReaderFactory readers = new SimpleMetadataReaderFactory();
        Set<String> violations = new TreeSet<>();
        Path repositoryClasses = CLASSES_ROOT.resolve("com/viglet/turing/persistence/repository");
        try (Stream<Path> paths = Files.walk(repositoryClasses)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .forEach(p -> readClass(readers, p).ifPresent(reader -> {
                        String className = reader.getClassMetadata().getClassName();
                        String simpleName = className.substring(className.lastIndexOf('.') + 1);
                        String entity = guarded.get(simpleName);
                        if (entity == null) {
                            return;
                        }
                        reader.getAnnotationMetadata().getDeclaredMethods().forEach(method -> {
                            String member = simpleName + "#" + method.getMethodName();
                            if (method.isAnnotated(MODIFYING) && !ALLOWED_BULK_WRITES
                                    .containsKey(member)) {
                                violations.add("%s (manages %s, which declares a listener)"
                                        .formatted(member, entity));
                            }
                        });
                    }));
        }

        assertThat(violations)
                .as("""
                        @Modifying runs the query through executeUpdate(), which manages no
                        entity — so no @PostRemove or @PostUpdate fires and the listener that
                        entity declares never runs. Use the inherited delete(entity) /
                        deleteById(id) / deleteAll(iterable), or load-mutate-save for an
                        update; every site that hit this had already loaded the rows, so it
                        costs no extra query. A bulk update is a more reasonable thing to want
                        than a bulk delete, so if one is genuinely needed, evict explicitly and
                        add the member to ALLOWED_BULK_WRITES with the reason. Offenders:""")
                .isEmpty();
    }

    /**
     * Bulk writes on a guarded repository that are deliberate, with the reason.
     *
     * <p>
     * Empty today, and expected not to stay that way. Zero bulk <em>deletes</em> survived
     * this block, but a bulk update — a batch status flip, a counter increment — is a
     * reasonable thing to want, and the cost of loading every row to do it is real. This is
     * the door for that: a member and a sentence saying which callback was considered and
     * how its work is done instead. The sibling {@code TurCacheAnnotationConventionsTest}
     * uses the same shape, so an exemption is stated in the diff rather than argued with.
     * </p>
     */
    private static final Map<String, String> ALLOWED_BULK_WRITES = Map.of();

    /**
     * A bulk delete <em>call</em> is refused anywhere in the app's own packages. There are
     * none left, and the cheapest moment to refuse the next one is before it is written.
     * Source text is sound here: unlike an annotation, a method's simple name has to appear
     * literally at the call site.
     */
    @Test
    void noBulkDeleteCallAnywhere() throws IOException {
        Set<String> violations = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(TurBulkWriteConventionsTest::isOwnPackage)
                    .forEach(p -> scanForCalls(p, violations));
        }

        assertThat(violations)
                .as("""
                        deleteAllInBatch/deleteInBatch bypass the persistence context, so no
                        @PostRemove fires. Load the rows and use deleteAll(iterable). If a batch
                        really is required, evict explicitly and mark the line '%s' with the
                        reason. Offenders:""".formatted(ALLOW_MARKER))
                .isEmpty();
    }

    private static void scanForCalls(Path javaFile, Set<String> violations) {
        read(javaFile).ifPresent(source -> {
            long calls = BATCH_DELETE_CALL.matcher(source).results().count();
            if (calls == 0) {
                return;
            }
            // One marker per call, so an exemption written for one cannot cover a second.
            long allowed = source.split(Pattern.quote(ALLOW_MARKER), -1).length - 1L;
            if (calls > allowed) {
                violations.add("%s — %d bulk-delete call(s), %d marked '%s'".formatted(
                        javaFile.getFileName(), calls, allowed, ALLOW_MARKER));
            }
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static boolean isOwnPackage(Path path) {
        return path.toString().replace(File.separatorChar, '/').contains("/com/viglet/turing/");
    }

    private static Optional<String> managedEntity(Path repositoryFile) {
        return read(repositoryFile)
                .map(MANAGED_ENTITY::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .map(entity -> entity.substring(entity.lastIndexOf('.') + 1));
    }

    private static Optional<MetadataReader> readClass(MetadataReaderFactory readers, Path file) {
        try {
            return Optional.of(readers.getMetadataReader(new FileSystemResource(file)));
        } catch (IOException | RuntimeException ignored) {
            // A class file we cannot parse carries no annotation we can assert on.
            return Optional.empty();
        }
    }

    private static Optional<String> read(Path file) {
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // A source we cannot read cannot be asserted on.
            return Optional.empty();
        }
    }
}

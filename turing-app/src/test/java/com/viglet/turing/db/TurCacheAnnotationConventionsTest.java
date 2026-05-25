/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

/**
 * Lints the "every {@code @Cacheable} cache name needs a paired {@code @CacheEvict}" convention
 * documented under "Adding a {@code @Cacheable} query" in {@code agents.md}.
 *
 * <p>Walks the compiled {@code target/classes} tree, reads annotation metadata via Spring's
 * bytecode-level {@link MetadataReader} (no class loading, no Spring context), collects the union
 * of cache names from every {@link Cacheable} site and the union from every {@link CacheEvict}
 * site, and fails when a {@code @Cacheable} name is never evicted anywhere.
 *
 * <p>Mirrors {@link TurLiquibaseChangelogAgnosticTest} in style: file-tree walk plus accumulated
 * violations, then a single {@code assertThat(violations).isEmpty()} at the end.
 */
class TurCacheAnnotationConventionsTest {

    /**
     * Cache names allowed to exist as {@code @Cacheable} without a paired {@code @CacheEvict}.
     *
     * <p>Add an entry here ONLY with a comment explaining why declarative eviction is
     * unnecessary. Two legitimate reasons exist today: (a) the cached value is truly immutable
     * (sourced from compile-time configuration or static resources), or (b) eviction is driven
     * by a JPA {@code @EntityListeners} that calls {@code cacheManager.getCache(name).clear()}
     * directly — in which case this lint can't see the link, but the eviction still happens.
     * Anything that depends on JPA entities, request state, or user input should always have a
     * matching {@code @CacheEvict} instead.
     */
    private static final Set<String> EVICTION_EXEMPT = Set.of(
            // Evicted programmatically by TurSNSiteSnapshotEvictionListener
            // (@PostPersist/@PostUpdate/@PostRemove → cacheManager.getCache(...).clear()) rather
            // than declaratively on the SN repositories. See agents.md "Cache, Scheduling &
            // Serialization Notes" for the rationale.
            "turSNSiteSearchSnapshot");

    @Test
    void everyCacheableMustHaveAMatchingCacheEvict() throws IOException {
        Path classesDir = Paths.get("target/classes");
        assertThat(Files.isDirectory(classesDir))
                .as("Expected compiled classes at %s. Run 'mvn -DskipTests compile' first.",
                        classesDir)
                .isTrue();

        MetadataReaderFactory readers = new SimpleMetadataReaderFactory();
        Set<String> cacheableNames = new TreeSet<>();
        Set<String> evictNames = new TreeSet<>();

        try (Stream<Path> paths = Files.walk(classesDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(TurCacheAnnotationConventionsTest::isClassFile)
                    .forEach(p -> scan(p, readers, cacheableNames, evictNames));
        }

        Set<String> notEvicted = new TreeSet<>(cacheableNames);
        notEvicted.removeAll(evictNames);
        notEvicted.removeAll(EVICTION_EXEMPT);

        assertThat(notEvicted)
                .as("""
                        Every @Cacheable cache name must have a matching @CacheEvict somewhere
                        in the project (see "Adding a @Cacheable query" in agents.md).
                        For each name listed below, add it to a @CacheEvict on save(...) and
                        delete(...) in the owning repository — or, if the cached value is truly
                        immutable, add it to EVICTION_EXEMPT in this test with a justifying
                        comment.""")
                .isEmpty();
    }

    private static boolean isClassFile(Path path) {
        return path.getFileName().toString().endsWith(".class");
    }

    private static void scan(Path classFile, MetadataReaderFactory readers,
            Set<String> cacheableNames, Set<String> evictNames) {
        try {
            MetadataReader reader = readers.getMetadataReader(new FileSystemResource(classFile));
            collect(reader.getAnnotationMetadata().getAnnotations(), cacheableNames, evictNames);
            reader.getAnnotationMetadata().getDeclaredMethods()
                    .forEach(m -> collect(m.getAnnotations(), cacheableNames, evictNames));
        } catch (IOException | RuntimeException ignored) {
            // Skip class files we can't parse (e.g. malformed or synthetic) — they can't carry
            // Spring cache annotations anyway.
        }
    }

    private static void collect(MergedAnnotations annotations, Set<String> cacheableNames,
            Set<String> evictNames) {
        annotations.stream(Cacheable.class)
                .forEach(a -> addNonBlank(cacheableNames, a));
        annotations.stream(CacheEvict.class)
                .forEach(a -> addNonBlank(evictNames, a));
        annotations.stream(Caching.class).forEach(caching -> {
            for (MergedAnnotation<Cacheable> nested : caching.getAnnotationArray("cacheable",
                    Cacheable.class)) {
                addNonBlank(cacheableNames, nested);
            }
            for (MergedAnnotation<CacheEvict> nested : caching.getAnnotationArray("evict",
                    CacheEvict.class)) {
                addNonBlank(evictNames, nested);
            }
        });
    }

    private static void addNonBlank(Set<String> target, MergedAnnotation<?> annotation) {
        for (String name : annotation.getStringArray("value")) {
            if (!name.isBlank()) {
                target.add(name);
            }
        }
        for (String name : annotation.getStringArray("cacheNames")) {
            if (!name.isBlank()) {
                target.add(name);
            }
        }
    }

}

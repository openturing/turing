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
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

/**
 * Enforces the Block AC (T486) policy: <strong>no Spring cache annotations on repositories.</strong>
 *
 * <p>Turing used to cache at the repository layer ({@code @Cacheable} on {@code findById}/
 * {@code findAll} finders, paired with {@code @CacheEvict} on writes). The cached value was a JPA
 * <em>entity</em> carrying lazy {@code @ManyToOne} proxies, so any hot-path read that dereferenced
 * a lazy association outside a transaction threw {@code LazyInitializationException} — masked for
 * years by {@code hibernate.enable_lazy_load_no_trans=true} until that flag was turned off. T486
 * removes the whole class of bug by making the environment cache-free at the repository layer;
 * caching is re-introduced only as a dedicated read-model/DTO cache for the hot path (T487) and
 * never on entities.
 *
 * <p>This lint walks the compiled {@code target/classes} tree, reads annotation metadata via
 * Spring's bytecode-level {@link MetadataReader} (no class loading, no Spring context), and fails
 * when any repository type carries {@link Cacheable}, {@link CacheEvict}, or {@link Caching} on the
 * type itself or on any of its methods. Service-level caches (immutable read-models / DTOs) are
 * deliberately out of scope — the policy is "repositories are uncached", not "nothing is cached".
 *
 * <p>Mirrors {@link TurLiquibaseChangelogAgnosticTest} in style: file-tree walk plus accumulated
 * violations, then a single {@code assertThat(violations).isEmpty()} at the end.
 */
class TurCacheAnnotationConventionsTest {

    @Test
    void noRepositoryMayCarryCacheAnnotations() throws IOException {
        Path classesDir = Paths.get("target/classes");
        assertThat(Files.isDirectory(classesDir))
                .as("Expected compiled classes at %s. Run 'mvn -DskipTests compile' first.",
                        classesDir)
                .isTrue();

        MetadataReaderFactory readers = new SimpleMetadataReaderFactory();
        Set<String> violations = new TreeSet<>();

        try (Stream<Path> paths = Files.walk(classesDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(TurCacheAnnotationConventionsTest::isClassFile)
                    .forEach(p -> scan(p, readers, violations));
        }

        assertThat(violations)
                .as("""
                        Repositories must NOT carry Spring cache annotations (Block AC / T486).
                        @Cacheable/@CacheEvict on a repository caches JPA entities with lazy
                        proxies, which breaks the chat/search hot path with
                        LazyInitializationException. Remove the annotation. If you need caching for
                        the hot path, cache an immutable read-model/DTO in a dedicated cache service
                        (see T487), never a JPA entity. Offending repository members below:""")
                .isEmpty();
    }

    private static boolean isClassFile(Path path) {
        return path.getFileName().toString().endsWith(".class");
    }

    private static boolean isRepository(String className) {
        return className.contains(".persistence.repository.") || className.endsWith("Repository");
    }

    private static void scan(Path classFile, MetadataReaderFactory readers, Set<String> violations) {
        try {
            MetadataReader reader = readers.getMetadataReader(new FileSystemResource(classFile));
            String className = reader.getClassMetadata().getClassName();
            if (!isRepository(className)) {
                return;
            }
            String simpleName = className.substring(className.lastIndexOf('.') + 1);
            if (hasCacheAnnotation(reader.getAnnotationMetadata().getAnnotations())) {
                violations.add(simpleName + " (type-level)");
            }
            reader.getAnnotationMetadata().getDeclaredMethods().forEach(m -> {
                if (hasCacheAnnotation(m.getAnnotations())) {
                    violations.add(simpleName + "#" + m.getMethodName());
                }
            });
        } catch (IOException | RuntimeException ignored) {
            // Skip class files we can't parse (e.g. malformed or synthetic) — they can't carry
            // Spring cache annotations anyway.
        }
    }

    private static boolean hasCacheAnnotation(MergedAnnotations annotations) {
        return annotations.isPresent(Cacheable.class)
                || annotations.isPresent(CacheEvict.class)
                || annotations.isPresent(Caching.class);
    }

}

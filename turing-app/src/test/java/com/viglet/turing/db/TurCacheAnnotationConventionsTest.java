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
import java.util.Map;
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

    /**
     * T895 / §LXVIII.4 — <strong>every explicitly-keyed {@code @Cacheable} is either
     * tenant-scoped or listed here as globally unique.</strong>
     *
     * <p>An explicit {@code key} SpEL bypasses {@code TurTenantCacheKeyGenerator}
     * entirely, so such a cache is partitioned by tenant only if its own key says
     * so. That exemption was taken on the grounds that explicitly-keyed caches are
     * id-keyed and a UUID is already globally unique — which held for five of the
     * eight and failed for the three keyed on the <em>SN site name</em>, a value
     * unique only within a tenant. Two tenants owning a site called {@code intranet}
     * shared cached answers, the site snapshot and the LLM tool's field config.
     *
     * <p>The lint is the part that stops the next one: a new explicitly-keyed cache
     * must either wrap its key in {@code @turTenantCacheKey.scoped(...)} or be added
     * to {@link #GLOBALLY_UNIQUE_KEYS} with the reason its key cannot collide across
     * tenants. Both are cheap; neither is silent.
     */
    @Test
    void everyExplicitlyKeyedCacheIsTenantScopedOrGloballyUnique() throws IOException {
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
                    .forEach(p -> scanExplicitKeys(p, readers, violations));
        }

        assertThat(violations)
                .as("""
                        An explicitly-keyed @Cacheable bypasses TurTenantCacheKeyGenerator, so it is
                        partitioned by tenant only if its own key says so (T895 / §LXVIII.4). Either
                        wrap the key in @turTenantCacheKey.scoped(...), or — if the key genuinely
                        cannot collide across tenants (a UUID, or content-addressed input) — add the
                        member to GLOBALLY_UNIQUE_KEYS in this test with the reason. Unscoped and
                        unlisted below:""")
                .isEmpty();
    }

    /**
     * Explicitly-keyed caches whose key cannot collide across tenants, with the
     * reason. Reviewed as part of T895 — add to this map only after checking that
     * every component of the key is either a globally-unique id or content the value
     * is fully derived from.
     */
    private static final Map<String, String> GLOBALLY_UNIQUE_KEYS = Map.of(
            "TurChatFlowGraphCache#graph", "flowId is a UUID",
            "TurChatFlowStaticPromptCache#staticHead", "flowId + nodeId are UUIDs",
            "TurChatFlowStaticPromptCache#staticTail", "flowId + nodeId are UUIDs",
            "TurPersonaStaticPromptCache#composeStaticBlock", "persona.id is a UUID",
            "TurLLMPriceService#rates",
            "vendorId + modelName address the public model catalog, which is not tenant data",
            "TurRagRerankCache#orderedIds",
            "content-addressed: the ordering is fully derived from strategy/model/topK/"
                    + "candidatesHash/query, so equal keys have equal correct values");

    /** The wrapper that makes a hand-written key tenant-scoped. */
    private static final String TENANT_SCOPED = "@turTenantCacheKey.scoped(";

    private static void scanExplicitKeys(Path classFile, MetadataReaderFactory readers,
            Set<String> violations) {
        try {
            MetadataReader reader = readers.getMetadataReader(new FileSystemResource(classFile));
            String className = reader.getClassMetadata().getClassName();
            String simpleName = className.substring(className.lastIndexOf('.') + 1);
            reader.getAnnotationMetadata().getDeclaredMethods().forEach(method -> {
                MergedAnnotation<Cacheable> cacheable =
                        method.getAnnotations().get(Cacheable.class);
                if (!cacheable.isPresent()) {
                    return;
                }
                String key = cacheable.getString("key");
                String member = simpleName + "#" + method.getMethodName();
                if (key.isBlank() || key.contains(TENANT_SCOPED)
                        || GLOBALLY_UNIQUE_KEYS.containsKey(member)) {
                    return;
                }
                violations.add(member + " -> key = \"" + key + "\"");
            });
        } catch (IOException | RuntimeException ignored) {
            // Skip class files we can't parse — they can't carry a @Cacheable anyway.
        }
    }

    /**
     * T904 / §LXVIII.11 — <strong>no {@code @CacheEvict} in a class that is also
     * transactional.</strong>
     *
     * <p>
     * {@code @CacheEvict} cannot express "after commit". With the default
     * {@code beforeInvocation = false} it fires when the method <em>returns</em>, which
     * inside a transaction is still before the write it invalidates is visible — the
     * window T901 and T903 closed for the search and snapshot caches. Worse, when both
     * annotations sit on the same method the order is decided by two interceptors that
     * both declare {@code LOWEST_PRECEDENCE}.
     * </p>
     *
     * <p>
     * The check is deliberately at class granularity: a method-level pairing is the
     * dangerous case, but a transactional class is where the next one will be written.
     * It cannot see the shape that actually caused this — {@code TurSNSpotlightProcess}
     * had no {@code @Transactional} of its own and inherited one from
     * {@code processIndexingQueue}, its caller. Nothing static catches that, which is
     * why those two methods were converted rather than annotated: this lint narrows
     * where the mistake can be made, it does not eliminate it.
     * </p>
     */
    @Test
    void noCacheEvictInATransactionalClass() throws IOException {
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
                    .forEach(p -> scanCacheEvict(p, readers, violations));
        }

        assertThat(violations)
                .as("""
                        @CacheEvict fires when the method returns, not when the transaction commits,
                        so in a transactional class it discards entries while the write is still
                        invisible — and a concurrent reader re-caches the old value (T904 /
                        §LXVIII.11). Use TurCacheEvictionScheduler.clearOnce(name) instead, which
                        evicts afterCommit and skips a rollback entirely. Offenders:""")
                .isEmpty();
    }

    private static void scanCacheEvict(Path classFile, MetadataReaderFactory readers,
            Set<String> violations) {
        try {
            MetadataReader reader = readers.getMetadataReader(new FileSystemResource(classFile));
            String className = reader.getClassMetadata().getClassName();
            String simpleName = className.substring(className.lastIndexOf('.') + 1);
            boolean transactionalClass = reader.getAnnotationMetadata()
                    .isAnnotated(TRANSACTIONAL);
            reader.getAnnotationMetadata().getDeclaredMethods().forEach(method -> {
                if (!method.getAnnotations().isPresent(CacheEvict.class)) {
                    return;
                }
                if (transactionalClass || method.getAnnotations().isPresent(TRANSACTIONAL)) {
                    violations.add(simpleName + "#" + method.getMethodName());
                }
            });
        } catch (IOException | RuntimeException ignored) {
            // Unparseable class files carry no annotations we care about.
        }
    }

    private static final String TRANSACTIONAL =
            "org.springframework.transaction.annotation.Transactional";

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

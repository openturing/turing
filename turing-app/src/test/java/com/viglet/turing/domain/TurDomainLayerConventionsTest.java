/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * T545 — build-failing convention lint that keeps the domain layer (ports &
 * adapters) inside the boundary the ADR (T541,
 * {@code docs/adr/0001-domain-layer-bounded-completion.md}) fixed. Without this
 * the layer silently drifts back toward the 100% migration Block AF decided
 * <em>not</em> to reach.
 *
 * <p>Mirrors {@code TurCacheAnnotationConventionsTest} /
 * {@code TurLiquibaseChangelogAgnosticTest} in style — a file-tree / reflection
 * walk that accumulates violations, then a single {@code assertThat(...).isEmpty()}.
 * It enforces the three invariants named in the ADR:
 *
 * <ol>
 *   <li><b>Records are annotation-free.</b> Every {@code record} under
 *       {@code com.viglet.turing.domain} carries no JPA / Hibernate / Jackson
 *       annotation — that is what makes it a safe, immutable, lazy-proxy-free
 *       cacheable value.</li>
 *   <li><b>The domain layer depends inward only.</b> No source under
 *       {@code domain/} imports a Spring Data JPA repository or the
 *       {@code persistence.adapter} package — ports must not reach into the
 *       infrastructure they abstract. Plus a curated set of hot-path consumers
 *       that were migrated to a port (T542/T543) must not regress to the JPA
 *       repository they were moved off.</li>
 *   <li><b>Every surviving port has exactly one adapter + one mapper.</b> For
 *       each {@code *RepositoryPort} there is exactly one
 *       {@code *RepositoryAdapter} implementing it and exactly one
 *       {@code *DomainMapper}.</li>
 * </ol>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurDomainLayerConventionsTest {

    /** Module-relative source root — the test runs with the module dir as CWD. */
    private static final Path SRC = Paths.get("src", "main", "java");
    private static final Path DOMAIN_SRC = SRC.resolve(Paths.get("com", "viglet", "turing", "domain"));
    private static final Path ADAPTER_SRC =
            SRC.resolve(Paths.get("com", "viglet", "turing", "persistence", "adapter"));

    /** Annotation packages a proxy-free, JPA-free domain record must never carry. */
    private static final List<String> FORBIDDEN_ANNOTATION_PACKAGES = List.of(
            "jakarta.persistence", "org.hibernate", "com.fasterxml.jackson", "tools.jackson");

    /**
     * Hot-path consumers migrated to a port that must NOT regress to the named
     * JPA repository import. Each value is the substring of the forbidden
     * {@code import} line. These aggregates have no write on that path, so the
     * port fully replaces the repo (unlike, e.g., {@code TurSNSearchProcess},
     * which legitimately keeps the metric-access repo for its access <em>write</em>).
     */
    private static final Map<String, String> PORT_ONLY_HOT_PATH = Map.of(
            "genai/tool/TurDslToolService.java",
            "com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository",
            "sn/spotlight/TurSpotlightService.java",
            "com.viglet.turing.persistence.repository.sn.TurSNSiteRepository",
            "api/sn/console/TurSNSiteNamesAPI.java",
            "com.viglet.turing.persistence.repository.sn.TurSNSiteRepository");

    @Test
    void domainRecordsCarryNoPersistenceOrJacksonAnnotations() throws Exception {
        TreeSet<String> violations = new TreeSet<>();

        for (Class<?> type : domainClasses()) {
            if (!type.isRecord()) {
                continue;
            }
            collectForbidden(type.getDeclaredAnnotations(), type.getSimpleName() + " (type)", violations);
            for (RecordComponent rc : type.getRecordComponents()) {
                collectForbidden(rc.getDeclaredAnnotations(),
                        type.getSimpleName() + "#" + rc.getName() + " (component)", violations);
            }
            for (var field : type.getDeclaredFields()) {
                collectForbidden(field.getDeclaredAnnotations(),
                        type.getSimpleName() + "." + field.getName() + " (field)", violations);
            }
            for (var method : type.getDeclaredMethods()) {
                collectForbidden(method.getDeclaredAnnotations(),
                        type.getSimpleName() + "#" + method.getName() + "() (accessor)", violations);
            }
        }

        assertThat(violations)
                .as("""
                        Domain records must carry NO JPA/Hibernate/Jackson annotation \
                        (ADR 0001 / T545). A record is the safe, immutable, lazy-proxy-free \
                        cacheable value precisely because it is annotation-free; a JPA or \
                        Jackson annotation drags persistence/serialization concerns back into \
                        the domain. Offending members below:""")
                .isEmpty();
    }

    @Test
    void domainLayerDependsInwardOnlyAndHotPathStaysOnPorts() throws IOException {
        TreeSet<String> violations = new TreeSet<>();

        // (a) No domain source may reach into JPA repositories or the adapter package.
        try (Stream<Path> paths = Files.walk(DOMAIN_SRC)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String body = read(p);
                if (body.contains("import com.viglet.turing.persistence.repository.")) {
                    violations.add(DOMAIN_SRC.relativize(p) + " imports a JPA repository");
                }
                if (body.contains("import com.viglet.turing.persistence.adapter.")) {
                    violations.add(DOMAIN_SRC.relativize(p) + " imports the adapter package");
                }
            });
        }

        // (b) Curated hot-path consumers must not regress to the JPA repo they left.
        PORT_ONLY_HOT_PATH.forEach((relative, forbiddenImport) -> {
            Path source = SRC.resolve(Paths.get("com", "viglet", "turing"))
                    .resolve(relative.replace('/', java.io.File.separatorChar));
            assertThat(Files.exists(source))
                    .as("designated hot-path source not found: %s", source)
                    .isTrue();
            if (read(source).contains("import " + forbiddenImport + ";")) {
                violations.add(relative + " regressed to JPA repo " + forbiddenImport);
            }
        });

        assertThat(violations)
                .as("""
                        The domain layer must depend inward only (ADR 0001 / T545): ports & \
                        records must not import JPA repositories or the adapter package, and a \
                        hot-path consumer migrated to a port must not slip back to the JPA \
                        repository. Violations below:""")
                .isEmpty();
    }

    @Test
    void everySurvivingPortHasExactlyOneAdapterAndOneMapper() throws IOException {
        TreeSet<String> violations = new TreeSet<>();

        List<Path> ports;
        try (Stream<Path> paths = Files.walk(DOMAIN_SRC)) {
            ports = paths.filter(p -> p.getFileName().toString().endsWith("RepositoryPort.java"))
                    .toList();
        }
        assertThat(ports).as("expected at least one *RepositoryPort under %s", DOMAIN_SRC).isNotEmpty();

        for (Path port : ports) {
            String portSimple = stripExt(port.getFileName().toString());
            String base = portSimple.substring(0, portSimple.length() - "RepositoryPort".length());

            long adapters = countFiles(ADAPTER_SRC, base + "RepositoryAdapter.java");
            long mappers = countFiles(ADAPTER_SRC, base + "DomainMapper.java");

            if (adapters != 1) {
                violations.add(portSimple + " has " + adapters + " adapters (expected exactly 1: "
                        + base + "RepositoryAdapter)");
            }
            if (mappers != 1) {
                violations.add(portSimple + " has " + mappers + " mappers (expected exactly 1: "
                        + base + "DomainMapper)");
            }
        }

        assertThat(violations)
                .as("""
                        Every surviving *RepositoryPort must have exactly one *RepositoryAdapter \
                        and one *DomainMapper (ADR 0001 / T545) — a dangling port or a port with \
                        two adapters is scaffolding drift. Violations below:""")
                .isEmpty();
    }

    // ---- helpers ----------------------------------------------------------

    private static List<Class<?>> domainClasses() throws IOException, ClassNotFoundException {
        List<Path> sources;
        try (Stream<Path> paths = Files.walk(DOMAIN_SRC)) {
            sources = paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        ClassLoader loader = TurDomainLayerConventionsTest.class.getClassLoader();
        List<Class<?>> classes = new java.util.ArrayList<>();
        for (Path src : sources) {
            String fqcn = SRC.relativize(src).toString()
                    .replace(java.io.File.separatorChar, '.')
                    .replaceAll("\\.java$", "");
            classes.add(Class.forName(fqcn, false, loader));
        }
        return classes;
    }

    private static void collectForbidden(Annotation[] annotations, String where, TreeSet<String> out) {
        for (Annotation a : annotations) {
            String name = a.annotationType().getName();
            if (FORBIDDEN_ANNOTATION_PACKAGES.stream().anyMatch(name::startsWith)) {
                out.add(where + " → " + name);
            }
        }
    }

    private static long countFiles(Path root, String fileName) throws IOException {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(p -> p.getFileName().toString().equals(fileName)).count();
        }
    }

    private static String stripExt(String fileName) {
        return fileName.substring(0, fileName.length() - ".java".length());
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + p, e);
        }
    }
}

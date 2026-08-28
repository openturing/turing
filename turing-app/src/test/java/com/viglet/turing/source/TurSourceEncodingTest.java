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

package com.viglet.turing.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T1053, T1055 / §CIX — no Java source in the reactor may carry a raw NUL byte.
 *
 * <p>A NUL is a fine <em>value</em>: it cannot occur in either half of a
 * composite key, which is exactly what a separator wants, and a path-injection
 * guard needs a real one to be tested against. Seven files used it for those
 * reasons and all seven were right about that.
 *
 * <p>They wrote it as a raw {@code 0x00} rather than as the {@code \0} escape,
 * and that is what this refuses. The two compile to the same byte, so nothing
 * about the behaviour is at stake — but a raw NUL makes the file <b>binary</b> to
 * ripgrep, so a content search over it answers {@code binary file matches} and no
 * lines. A code search that silently skips a file is worse than one that fails,
 * and that is how this was found: a search for a method in the Content AI plugin
 * came back empty while the method was there.
 *
 * <p>It is also fragile in a way plain text is not. Any tool that reads and
 * rewrites the file can drop or transform the byte invisibly, and neither a diff
 * nor a review would show it.
 *
 * <h3>Why it walks the whole reactor (T1055)</h3>
 *
 * <p>It shipped covering {@code turing-app} alone, which left 227 files in three
 * modules outside a gate whose defect had just occurred nine times. The modules
 * are read from the <b>reactor pom</b> rather than listed here, so a module added
 * later is covered without anyone remembering to add it — which is the whole
 * value, since nobody edits this file when opening a module.
 *
 * <p>A test in one module asserting about its siblings is a dependency the build
 * does not otherwise have, and that was the argument for a copy per module
 * instead. {@code TurSeedBundleTest} already resolves the repo root from
 * {@code user.dir} to read the seed bundle, so the precedent exists and three
 * files to keep in step is the worse trade.
 */
class TurSourceEncodingTest {

    private static final List<Path> MODULE_ROOTS =
            List.of(Paths.get("src", "main", "java"), Paths.get("src", "test", "java"));

    private static final Pattern MODULE = Pattern.compile("<module>\\s*([^<\\s]+)\\s*</module>");

    /** How far to climb looking for the reactor pom before giving up. */
    private static final int MAX_ASCENT = 4;

    @Test
    @DisplayName("No Java source in any reactor module carries a raw NUL byte")
    void noSourceCarriesARawNulByte() throws IOException {
        List<Path> sourceRoots = sourceRoots();
        assertThat(sourceRoots)
                .as("expected at least this module's own two source roots")
                .hasSizeGreaterThan(1);

        List<String> offenders = new ArrayList<>();
        for (Path root : sourceRoots) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .forEach(path -> collect(path, offenders));
            }
        }

        assertThat(offenders)
                .as("A raw NUL makes the file binary to code search. Write \"\\0\" instead — "
                        + "it is the same byte at runtime.")
                .isEmpty();
    }

    /**
     * Every {@code src/main/java} and {@code src/test/java} the reactor declares.
     *
     * <p>Falls back to this module's own roots when the reactor pom cannot be
     * found, which is what happens if the test is run from an unexpected working
     * directory. Narrowing beats failing there: the gate still guards the module
     * it lives in, and the size assertion above says which happened.
     */
    private static List<Path> sourceRoots() throws IOException {
        Set<Path> roots = new LinkedHashSet<>();
        Path module = Paths.get("").toAbsolutePath().normalize();
        for (Path root : MODULE_ROOTS) {
            addIfDirectory(roots, module.resolve(root));
        }

        Path reactor = reactorRoot(module);
        if (reactor != null) {
            for (String name : modulesOf(reactor)) {
                for (Path root : MODULE_ROOTS) {
                    addIfDirectory(roots, reactor.resolve(name).resolve(root));
                }
            }
        }
        return List.copyOf(roots);
    }

    /** The nearest ancestor whose {@code pom.xml} declares modules. */
    private static Path reactorRoot(Path from) throws IOException {
        Path candidate = from;
        for (int i = 0; i <= MAX_ASCENT && candidate != null; i++) {
            Path pom = candidate.resolve("pom.xml");
            if (Files.isRegularFile(pom)
                    && Files.readString(pom, StandardCharsets.UTF_8).contains("<module>")) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        return null;
    }

    private static Set<String> modulesOf(Path reactor) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher =
                MODULE.matcher(Files.readString(reactor.resolve("pom.xml"), StandardCharsets.UTF_8));
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static void addIfDirectory(Set<Path> roots, Path candidate) {
        Path normalized = candidate.normalize();
        if (Files.isDirectory(normalized)) {
            roots.add(normalized);
        }
    }

    private static void collect(Path path, List<String> offenders) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == 0) {
                    offenders.add("%s (offset %d)".formatted(
                            path.toString().replace('\\', '/'), i));
                    return;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }
}

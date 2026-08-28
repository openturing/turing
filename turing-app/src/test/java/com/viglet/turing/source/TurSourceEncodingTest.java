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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T1053 / §CVIII — no Java source may carry a raw NUL byte.
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
 */
class TurSourceEncodingTest {

    private static final List<Path> ROOTS =
            List.of(Paths.get("src", "main", "java"), Paths.get("src", "test", "java"));

    @Test
    @DisplayName("No Java source carries a raw NUL byte; write the \\0 escape instead")
    void noSourceCarriesARawNulByte() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path root : ROOTS) {
            assertThat(Files.isDirectory(root)).as("Expected a source root at %s", root).isTrue();
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

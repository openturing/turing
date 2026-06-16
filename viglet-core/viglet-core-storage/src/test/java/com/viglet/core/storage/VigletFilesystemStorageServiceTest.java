/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the lifted {@link VigletFilesystemStorageService}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class VigletFilesystemStorageServiceTest {

    @TempDir
    Path tempDir;

    private VigletFilesystemStorageService service;

    @BeforeEach
    void setUp() {
        VigletStorageProperties properties = new VigletStorageProperties();
        properties.getFilesystem().setPath(tempDir.toString());
        service = new VigletFilesystemStorageService(properties);
        service.init();
    }

    @Test
    void reportsTypeAndEnabled() {
        assertThat(service.getType()).isEqualTo(VigletStorageType.FILESYSTEM);
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void uploadStreamStatAndDownloadRoundTrip() throws Exception {
        byte[] content = "hello world".getBytes();
        service.uploadStream("docs/test.txt", new ByteArrayInputStream(content), content.length, "text/plain");

        VigletStorageObjectStat stat = service.statObject("docs/test.txt");
        assertThat(stat.objectName()).isEqualTo("docs/test.txt");
        assertThat(stat.size()).isEqualTo(content.length);
        assertThat(stat.contentType()).isEqualTo("text/plain");

        try (InputStream is = service.downloadObject("docs/test.txt")) {
            assertThat(is.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void listAllObjectsIsRecursive() throws Exception {
        service.uploadStream("a.txt", new ByteArrayInputStream("a".getBytes()), 1, "text/plain");
        service.uploadStream("sub/b.txt", new ByteArrayInputStream("b".getBytes()), 1, "text/plain");
        assertThat(service.listAllObjects()).hasSize(2);
    }

    @Test
    void deleteObjectsWithPrefixRemovesAll() throws Exception {
        service.uploadStream("site/a.html", new ByteArrayInputStream("a".getBytes()), 1, "text/html");
        service.uploadStream("site/b.html", new ByteArrayInputStream("b".getBytes()), 1, "text/html");
        service.deleteObjectsWithPrefix("site");
        assertThat(Files.exists(tempDir.resolve("site"))).isFalse();
    }

    @Test
    void pathTraversalIsRejected() {
        assertThatThrownBy(() -> service.downloadObject("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path traversal");
    }
}

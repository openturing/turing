/*
 * Copyright (C) 2016-2025 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.api.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.metadata.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for TurTikaFileAttributes.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurTikaFileAttributesTest {

    @TempDir
    Path tempDir;

    @Test
    void testConstructor() throws IOException {
        File testFile = tempDir.resolve("test.txt").toFile();
        Files.writeString(testFile.toPath(), "test content");

        String content = "Extracted content";
        Metadata metadata = new Metadata();
        metadata.set("Content-Type", "text/plain");

        TurTikaFileAttributes attributes = new TurTikaFileAttributes(testFile, content, metadata);

        assertThat(attributes).isNotNull();
        assertThat(attributes.getFile()).isEqualTo(testFile);
        assertThat(attributes.getContent()).isEqualTo(content);
        assertThat(attributes.getMetadata()).isEqualTo(metadata);
    }

    @Test
    void testConstructorWithNullFile() {
        String content = "Some content";
        Metadata metadata = new Metadata();

        TurTikaFileAttributes attributes = new TurTikaFileAttributes(null, content, metadata);

        assertThat(attributes.getFile()).isNull();
        assertThat(attributes.getContent()).isEqualTo(content);
        assertThat(attributes.getMetadata()).isEqualTo(metadata);
    }

    @Test
    void testConstructorWithNullContent() throws IOException {
        File testFile = tempDir.resolve("test.pdf").toFile();
        Files.writeString(testFile.toPath(), "dummy");

        Metadata metadata = new Metadata();

        TurTikaFileAttributes attributes = new TurTikaFileAttributes(testFile, null, metadata);

        assertThat(attributes.getFile()).isEqualTo(testFile);
        assertThat(attributes.getContent()).isNull();
        assertThat(attributes.getMetadata()).isEqualTo(metadata);
    }

    @Test
    void testConstructorWithNullMetadata() throws IOException {
        File testFile = tempDir.resolve("test.docx").toFile();
        Files.writeString(testFile.toPath(), "dummy");

        String content = "Document content";

        TurTikaFileAttributes attributes = new TurTikaFileAttributes(testFile, content, null);

        assertThat(attributes.getFile()).isEqualTo(testFile);
        assertThat(attributes.getContent()).isEqualTo(content);
        assertThat(attributes.getMetadata()).isNull();
    }

    @Test
    void testGettersAndSetters() throws IOException {
        File initialFile = tempDir.resolve("initial.txt").toFile();
        Files.writeString(initialFile.toPath(), "initial");

        Metadata initialMetadata = new Metadata();
        initialMetadata.set("title", "Initial Title");

        TurTikaFileAttributes attributes = new TurTikaFileAttributes(
                initialFile,
                "Initial content",
                initialMetadata);

        File newFile = tempDir.resolve("new.txt").toFile();
        Files.writeString(newFile.toPath(), "new");

        Metadata newMetadata = new Metadata();
        newMetadata.set("title", "New Title");

        attributes.setFile(newFile);
        attributes.setContent("New content");
        attributes.setMetadata(newMetadata);

        assertThat(attributes.getFile()).isEqualTo(newFile);
        assertThat(attributes.getContent()).isEqualTo("New content");
        assertThat(attributes.getMetadata()).isEqualTo(newMetadata);
    }

    @Test
    void testWithComplexMetadata() throws IOException {
        File testFile = tempDir.resolve("document.pdf").toFile();
        Files.writeString(testFile.toPath(), "pdf content");

        String content = "This is a PDF document with complex metadata";

        Metadata metadata = new Metadata();
        metadata.set("Content-Type", "application/pdf");
        metadata.set("title", "Test Document");
        metadata.set("author", "John Doe");
        metadata.set("Creation-Date", "2025-01-10");
        metadata.set("Page-Count", "10");

        TurTikaFileAttributes attributes = new TurTikaFileAttributes(testFile, content, metadata);

        assertThat(attributes.getMetadata().get("Content-Type")).isEqualTo("application/pdf");
        assertThat(attributes.getMetadata().get("title")).isEqualTo("Test Document");
        assertThat(attributes.getMetadata().get("author")).isEqualTo("John Doe");
        assertThat(attributes.getMetadata().get("Creation-Date")).isEqualTo("2025-01-10");
        assertThat(attributes.getMetadata().get("Page-Count")).isEqualTo("10");
    }

    @Test
    void testWithEmptyContent() throws IOException {
        File testFile = tempDir.resolve("empty.txt").toFile();
        Files.writeString(testFile.toPath(), "");

        Metadata metadata = new Metadata();

        TurTikaFileAttributes attributes = new TurTikaFileAttributes(testFile, "", metadata);

        assertThat(attributes.getContent()).isEmpty();
    }

    @Test
    void testWithLargeContent() throws IOException {
        File testFile = tempDir.resolve("large.txt").toFile();
        Files.writeString(testFile.toPath(), "large");

        String largeContent = "A".repeat(10000);
        Metadata metadata = new Metadata();

        TurTikaFileAttributes attributes = new TurTikaFileAttributes(testFile, largeContent, metadata);

        assertThat(attributes.getContent()).hasSize(10000);
    }

    @Test
    void testFileAttributesModification() throws IOException {
        File file1 = tempDir.resolve("file1.txt").toFile();
        Files.writeString(file1.toPath(), "content1");

        TurTikaFileAttributes attributes = new TurTikaFileAttributes(
                file1,
                "Content 1",
                new Metadata());

        assertThat(attributes.getFile()).hasName("file1.txt");

        File file2 = tempDir.resolve("file2.txt").toFile();
        Files.writeString(file2.toPath(), "content2");

        attributes.setFile(file2);
        assertThat(attributes.getFile()).hasName("file2.txt");
    }

    @Test
    void testMetadataModification() throws IOException {
        File testFile = tempDir.resolve("test.txt").toFile();
        Files.writeString(testFile.toPath(), "test");

        Metadata metadata = new Metadata();
        metadata.set("initial", "value");

        TurTikaFileAttributes attributes = new TurTikaFileAttributes(testFile, "content", metadata);

        assertThat(attributes.getMetadata().get("initial")).isEqualTo("value");

        attributes.getMetadata().set("updated", "new value");
        assertThat(attributes.getMetadata().get("updated")).isEqualTo("new value");
    }
}

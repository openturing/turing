/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */

package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Real-Tika tests for the shared file-attachment helper. Migrated from the
 * reflection-based block in {@code TurLLMChatAPITest} when the helper moved
 * out of the API class into a Spring service (T221a).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatAttachmentServiceTest {

    private final TurChatAttachmentService service = new TurChatAttachmentService();

    @Test
    void shouldReturnPlainUserMessageWhenFilesNull() {
        UserMessage result = service.buildUserMessageWithFiles("Hello", null);
        assertThat(result.getText()).isEqualTo("Hello");
        assertThat(result.getMedia()).isEmpty();
    }

    @Test
    void shouldReturnPlainUserMessageWhenFilesEmpty() {
        UserMessage result = service.buildUserMessageWithFiles("Hello", List.of());
        assertThat(result.getText()).isEqualTo("Hello");
        assertThat(result.getMedia()).isEmpty();
    }

    @Test
    void shouldExtractTextFromPlainTextFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "Hello World".getBytes());
        String result = service.extractTextFromFile(file);
        assertThat(result).contains("Hello World");
    }

    @Test
    void shouldReturnEmptyStringForEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);
        String result = service.extractTextFromFile(file);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldExtractTextFromHtmlFile() {
        String html = "<html><body><h1>Title</h1><p>Content paragraph</p></body></html>";
        MockMultipartFile file = new MockMultipartFile(
                "file", "page.html", "text/html", html.getBytes());
        String result = service.extractTextFromFile(file);
        assertThat(result)
                .contains("Title")
                .contains("Content paragraph");
    }

    @Test
    void shouldBuildUserMessageWithImageFile() {
        MockMultipartFile imageFile = new MockMultipartFile(
                "files", "photo.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        UserMessage result = service.buildUserMessageWithFiles("Describe this image", List.of(imageFile));
        assertThat(result.getText()).contains("Describe this image");
        // PNG bytes recognised + attached as Media
        assertThat(result.getMedia()).hasSize(1);
    }

    @Test
    void shouldBuildUserMessageWithTextFileOnly() {
        MockMultipartFile textFile = new MockMultipartFile(
                "files", "doc.txt", "text/plain", "Important document content".getBytes());
        UserMessage result = service.buildUserMessageWithFiles("Analyze this", List.of(textFile));
        assertThat(result.getText()).contains("Analyze this");
        assertThat(result.getText()).contains("Important document content");
        assertThat(result.getMedia()).isEmpty();
    }

    @Test
    void shouldBuildUserMessageWithNullContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "data.bin", null, "binary content".getBytes());
        UserMessage result = service.buildUserMessageWithFiles("Check this", List.of(file));
        assertThat(result.getText()).contains("Check this");
    }

    @Test
    void shouldBuildUserMessageWithMultipleFiles() {
        MockMultipartFile textFile = new MockMultipartFile(
                "files", "readme.txt", "text/plain", "README content".getBytes());
        MockMultipartFile imageFile = new MockMultipartFile(
                "files", "chart.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        UserMessage result = service.buildUserMessageWithFiles("Analyze both", List.of(textFile, imageFile));
        assertThat(result.getText()).contains("Analyze both");
        assertThat(result.getText()).contains("README content");
        assertThat(result.getMedia()).hasSize(1);
    }

    @Test
    void shouldKeepTextUnchangedWhenExtractedContentIsBlank() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "empty.bin", "application/octet-stream", new byte[]{0, 1, 2});
        UserMessage result = service.buildUserMessageWithFiles("What is this?", List.of(file));
        assertThat(result.getText()).isEqualTo("What is this?");
    }
}

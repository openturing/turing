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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import lombok.extern.slf4j.Slf4j;

/**
 * Shared helper that turns a {@code List<MultipartFile>} into a Spring AI
 * {@link UserMessage} carrying the visitor's text + extracted document text
 * + image {@link Media} blocks. Used by both the plain-LLM chat endpoint
 * ({@code TurLLMChatAPI}) and the AI Agent chat endpoint
 * ({@code TurAIAgentChatAPI}, via {@link TurAgentChatExecutor}) so the two
 * paths attach attachments through one canonical implementation.
 *
 * <p>Documents are routed through Apache Tika (text extracted, appended to
 * the user message). Images recognised in {@link #IMAGE_MIME_TYPES} are also
 * attached as {@link Media} blocks so vision-capable models can read them
 * directly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChatAttachmentService {

    /** Image MIME types recognised by Spring AI's {@link Media} blocks. */
    public static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp");

    /**
     * Builds a {@link UserMessage} for the visitor's latest turn, attaching
     * the text + media extracted from {@code files}. Returns a plain
     * {@link UserMessage} when {@code files} is null/empty.
     *
     * @param text the visitor's typed message (may be empty)
     * @param files attachments to extract + attach; null/empty leaves the
     *              return value as a plain {@link UserMessage}
     */
    public UserMessage buildUserMessageWithFiles(String text, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return new UserMessage(text == null ? "" : text);
        }

        List<Media> imageMedia = new ArrayList<>();
        StringBuilder extractedText = new StringBuilder();

        for (MultipartFile file : files) {
            extractFileInto(file, extractedText, imageMedia);
        }

        String baseText = text == null ? "" : text;
        String fullText = extractedText.isEmpty() ? baseText : baseText + extractedText;

        if (!imageMedia.isEmpty()) {
            return UserMessage.builder()
                    .text(fullText)
                    .media(imageMedia)
                    .build();
        }
        return new UserMessage(fullText);
    }

    /**
     * Extracts text + (optional) image media from a single {@code file},
     * appending textual content to {@code extractedText} and any image block
     * to {@code imageMedia}.
     */
    private void extractFileInto(MultipartFile file, StringBuilder extractedText,
            List<Media> imageMedia) {
        String contentType = file.getContentType() != null
                ? file.getContentType()
                : "application/octet-stream";

        // Tika handles text + PDF + DOCX + OCR fallback for images.
        String content = extractTextFromFile(file);
        if (!content.isBlank()) {
            extractedText.append("\n\n--- File: ")
                    .append(file.getOriginalFilename())
                    .append(" ---\n")
                    .append(content);
        }

        if (IMAGE_MIME_TYPES.contains(contentType)) {
            try {
                imageMedia.add(Media.builder()
                        .mimeType(MimeType.valueOf(contentType))
                        .data(new ByteArrayResource(file.getBytes()))
                        .name(file.getOriginalFilename())
                        .build());
            } catch (IOException e) {
                log.warn("Failed to read image file: {}", file.getOriginalFilename(), e);
            }
        }
    }

    /**
     * Extract textual content from {@code file} via Apache Tika. Returns an
     * empty string on read / parse failure — failures are logged but never
     * surface to the caller so a malformed attachment can't take a chat
     * turn down.
     */
    public String extractTextFromFile(MultipartFile file) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(file.getBytes())) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            metadata.set(HttpHeaders.CONTENT_TYPE, file.getContentType());
            new AutoDetectParser().parse(inputStream, handler, metadata, new ParseContext());
            return handler.toString().strip();
        } catch (IOException | TikaException | SAXException e) {
            log.warn("Failed to extract text from file: {}", file.getOriginalFilename(), e);
            return "";
        }
    }
}

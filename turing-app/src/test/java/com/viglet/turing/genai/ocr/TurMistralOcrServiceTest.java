/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.viglet.turing.genai.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurOcrProperty;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TurMistralOcrServiceTest {

    private TurConfigProperties config;
    private TurMistralOcrService service;

    @BeforeEach
    void setUp() {
        config = new TurConfigProperties();
        config.setOcr(new TurOcrProperty());
        service = new TurMistralOcrService(config);
    }

    @Test
    void isAvailable_falseByDefault_trueWhenEnabledWithKey() {
        assertFalse(service.isAvailable());
        config.getOcr().setEnabled(true);
        assertFalse(service.isAvailable(), "enabled but no key → still unavailable");
        config.getOcr().getMistral().setApiKey("sk-test");
        assertTrue(service.isAvailable());
    }

    @Test
    void isOcrEligible_imagesAndPdfsOnly() {
        assertTrue(service.isOcrEligible("application/pdf", "scan.pdf"));
        assertTrue(service.isOcrEligible("image/png", "chart.png"));
        assertTrue(service.isOcrEligible(null, "photo.JPEG"));
        assertFalse(service.isOcrEligible("text/plain", "notes.txt"));
        assertFalse(service.isOcrEligible(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "doc.docx"));
    }

    @Test
    void augment_returnsTikaTextWhenPresent() {
        // Even with OCR available, a non-blank Tika text short-circuits (no OCR call).
        config.getOcr().setEnabled(true);
        config.getOcr().getMistral().setApiKey("sk-test");
        String result = service.augment("real extracted text", new byte[] { 1, 2 }, "application/pdf", "a.pdf");
        assertEquals("real extracted text", result);
    }

    @Test
    void augment_returnsBlankTikaTextWhenOcrUnavailable() {
        // OCR disabled → blank stays blank (no network call attempted).
        String result = service.augment("", new byte[] { 1, 2 }, "application/pdf", "a.pdf");
        assertEquals("", result);
    }

    @Test
    void augment_returnsTikaTextForIneligibleFileEvenWhenEnabled() {
        config.getOcr().setEnabled(true);
        config.getOcr().getMistral().setApiKey("sk-test");
        String result = service.augment("", new byte[] { 1, 2 }, "text/plain", "notes.txt");
        assertEquals("", result);
    }

    @Test
    void buildRequest_usesImageUrlForImagesAndDocumentUrlForPdf() {
        JsonMapper mapper = JsonMapper.builder().build();

        JsonNode image = mapper.readTree(
                service.buildRequest("mistral-ocr-latest", new byte[] { 1 }, "image/png", "c.png"));
        assertEquals("image_url", image.path("document").path("type").asString(""));
        assertTrue(image.path("document").path("image_url").asString("").startsWith("data:image/png;base64,"));

        JsonNode pdf = mapper.readTree(
                service.buildRequest("mistral-ocr-latest", new byte[] { 1 }, "application/pdf", "s.pdf"));
        assertEquals("document_url", pdf.path("document").path("type").asString(""));
        assertTrue(pdf.path("document").path("document_url").asString("").startsWith("data:application/pdf;base64,"));
    }

    @Test
    void pagesToText_concatenatesPerPageMarkdown() {
        String json = """
                {
                  "pages": [
                    { "index": 0, "markdown": "# Page one\\nbody one" },
                    { "index": 1, "markdown": "Page two body" },
                    { "index": 2, "markdown": "" }
                  ]
                }
                """;
        JsonNode response = JsonMapper.builder().build().readTree(json);
        String text = TurMistralOcrService.pagesToText(response);
        assertTrue(text.contains("Page one"));
        assertTrue(text.contains("Page two body"));
        assertEquals("# Page one\nbody one\n\nPage two body", text);
    }

    @Test
    void pagesToText_emptyOnMissingPages() {
        assertEquals("", TurMistralOcrService.pagesToText(JsonMapper.builder().build().readTree("{}")));
    }
}

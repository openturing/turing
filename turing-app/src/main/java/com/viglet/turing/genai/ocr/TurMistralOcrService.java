/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.ocr;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurOcrProperty.TurMistralOcrProperty;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * T515 / §XXVIII.11 — Mistral OCR / Document-AI extraction bridge.
 *
 * <p>Apache Tika (the default extractor on the indexer/connector write path) is
 * weak on scanned PDFs and image-only documents — it returns little or no text.
 * {@code mistral-ocr} is best-in-class document understanding; this service wires
 * it as an <b>augmentation</b>, not a replacement: {@link #augment} only calls
 * OCR when the file is OCR-eligible (image / PDF) <em>and</em> Tika produced no
 * usable text, so the common text-PDF / DOCX path is untouched and spends no
 * OCR credits.
 *
 * <p>The OCR API returns per-page Markdown (with layout-aware structure); the
 * concatenated Markdown is what the indexer embeds. That same structured output
 * is what feeds T387 manifest derivation downstream — the bridge makes scanned
 * documents first-class inputs to both retrieval and field extraction.
 *
 * <p>Opt-in and <b>fail-soft</b>: disabled (or no API key) → {@link #isAvailable}
 * is false and {@link #augment} returns the Tika text unchanged; any OCR error
 * is logged and the Tika text is returned, so OCR never blocks indexing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurMistralOcrService {

    private final TurConfigProperties configProperties;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    public TurMistralOcrService(TurConfigProperties configProperties) {
        this.configProperties = configProperties;
    }

    /** OCR is available when the bridge is enabled and a Mistral API key is configured. */
    public boolean isAvailable() {
        var ocr = configProperties.getOcr();
        return ocr != null && ocr.isEnabled() && StringUtils.hasText(ocr.getMistral().getApiKey());
    }

    /**
     * Whether a file is a candidate for OCR — images and PDFs. Text-native
     * formats (DOCX, HTML, plain text…) are excluded; Tika already handles them.
     */
    public boolean isOcrEligible(String mimeType, String fileName) {
        if (StringUtils.hasText(mimeType)) {
            String mime = mimeType.toLowerCase(Locale.ROOT);
            if (mime.startsWith("image/") || mime.equals("application/pdf")) {
                return true;
            }
        }
        if (StringUtils.hasText(fileName)) {
            String name = fileName.toLowerCase(Locale.ROOT);
            return name.endsWith(".pdf") || name.endsWith(".png") || name.endsWith(".jpg")
                    || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".webp")
                    || name.endsWith(".tif") || name.endsWith(".tiff") || name.endsWith(".bmp");
        }
        return false;
    }

    /**
     * Returns {@code tikaText} when it is usable; otherwise, for an OCR-eligible
     * file with OCR enabled, runs {@code mistral-ocr} over the bytes and returns
     * the extracted Markdown. Fail-soft: any failure returns {@code tikaText}.
     *
     * @param tikaText the text Apache Tika already extracted (may be blank)
     * @param bytes    the raw file bytes
     * @param mimeType the file MIME type
     * @param fileName the file name (used for extension-based eligibility + URI)
     * @return the best available extracted text (never null; may be blank)
     */
    public String augment(String tikaText, byte[] bytes, String mimeType, String fileName) {
        if (StringUtils.hasText(tikaText)) {
            return tikaText;
        }
        if (!isAvailable() || bytes == null || bytes.length == 0
                || !isOcrEligible(mimeType, fileName)) {
            return tikaText == null ? "" : tikaText;
        }
        return extractText(bytes, mimeType, fileName).orElse(tikaText == null ? "" : tikaText);
    }

    /**
     * Runs OCR over the given bytes and returns the concatenated per-page
     * Markdown. Empty on any failure or when the bridge is unavailable.
     */
    public Optional<String> extractText(byte[] bytes, String mimeType, String fileName) {
        if (!isAvailable() || bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        TurMistralOcrProperty mistral = configProperties.getOcr().getMistral();
        try {
            String body = buildRequest(mistral.getModel(), bytes, mimeType, fileName);
            JsonNode response = post(mistral, body);
            String text = pagesToText(response);
            return StringUtils.hasText(text) ? Optional.of(text) : Optional.empty();
        } catch (RuntimeException e) {
            log.warn("[OCR] Mistral OCR extraction failed for '{}': {} — keeping Tika text",
                    fileName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Builds the OCR request body. PDFs go through {@code document_url}, images
     * through {@code image_url}; both carry a base64 data URI so no public URL or
     * upload step is needed.
     */
    String buildRequest(String model, byte[] bytes, String mimeType, String fileName) {
        boolean image = isImage(mimeType, fileName);
        String mime = StringUtils.hasText(mimeType) ? mimeType
                : (image ? "image/png" : "application/pdf");
        String dataUri = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        ObjectNode document = root.putObject("document");
        if (image) {
            document.put("type", "image_url");
            document.put("image_url", dataUri);
        } else {
            document.put("type", "document_url");
            document.put("document_url", dataUri);
        }
        root.put("include_image_base64", false);
        return objectMapper.writeValueAsString(root);
    }

    private boolean isImage(String mimeType, String fileName) {
        if (StringUtils.hasText(mimeType) && mimeType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        if (StringUtils.hasText(fileName)) {
            String name = fileName.toLowerCase(Locale.ROOT);
            return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                    || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".tif")
                    || name.endsWith(".tiff") || name.endsWith(".bmp");
        }
        return false;
    }

    private JsonNode post(TurMistralOcrProperty mistral, String body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mistral.getEndpoint()))
                .timeout(Duration.ofSeconds(Math.max(5, mistral.getTimeoutSeconds())))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + mistral.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Mistral OCR returned HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Mistral OCR request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mistral OCR request interrupted", e);
        }
    }

    /**
     * Concatenates the {@code pages[].markdown} of an OCR response into one text
     * blob, pages separated by a blank line. Pages with no markdown are skipped.
     */
    static String pagesToText(JsonNode response) {
        JsonNode pages = response == null ? null : response.path("pages");
        if (pages == null || !pages.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode page : pages) {
            String markdown = page.path("markdown").asString("");
            if (StringUtils.hasText(markdown)) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(markdown.trim());
            }
        }
        return sb.toString();
    }
}

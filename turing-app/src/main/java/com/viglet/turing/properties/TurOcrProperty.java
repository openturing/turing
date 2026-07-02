/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * T515 / §XXVIII.11 — configuration for the document-AI OCR bridge
 * ({@code turing.ocr.*}). Today the only adapter is Mistral OCR
 * ({@code mistral-ocr}), which is best-in-class on scanned PDFs and image-only
 * documents where Apache Tika extracts little or no text.
 *
 * <p>Off by default: when {@link #enabled} is false (or no API key is set), the
 * indexer/connector write path uses Tika exactly as before — OCR is a pure
 * <em>augmentation</em> that only kicks in for OCR-eligible files Tika couldn't
 * read, so existing behaviour is unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
public class TurOcrProperty {

    /** Master switch for the OCR augmentation. When false, extraction is Tika-only. */
    private boolean enabled = false;

    /** Mistral OCR settings ({@code turing.ocr.mistral.*}). */
    private TurMistralOcrProperty mistral = new TurMistralOcrProperty();

    @Getter
    @Setter
    public static class TurMistralOcrProperty {
        /** Mistral API key. When blank, the OCR bridge stays inert even if {@code enabled}. */
        private String apiKey;
        /** OCR model id. */
        private String model = "mistral-ocr-latest";
        /** REST endpoint for the OCR API. */
        private String endpoint = "https://api.mistral.ai/v1/ocr";
        /** Per-request timeout in seconds. */
        private int timeoutSeconds = 60;
    }
}

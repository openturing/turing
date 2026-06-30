/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.gemini;

import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.springframework.util.StringUtils;

import com.google.genai.types.Blob;
import com.google.genai.types.CodeExecutionResult;
import com.google.genai.types.ExecutableCode;
import com.google.genai.types.Part;

/**
 * T492 / §X.19 — renders the non-text parts a Gemini {@code code_execution} turn
 * returns (the generated Python, its output, and inline matplotlib images) into
 * markdown appended to the assistant answer.
 *
 * <p>Gemini surfaces code execution as separate response {@link Part}s
 * (rather than inside {@code text()}):
 * <ul>
 *   <li>{@link Part#executableCode()} — the Python the model ran;</li>
 *   <li>{@link Part#codeExecutionResult()} — its captured stdout/outcome;</li>
 *   <li>{@link Part#inlineData()} — image bytes (e.g. a matplotlib PNG).</li>
 * </ul>
 *
 * <p>This decoder turns those into a fenced ```python block, a fenced output
 * block, and an inline markdown image. Images are emitted as self-contained
 * {@code data:} URIs so they render in any markdown surface without a separate
 * artifact-serving round trip (the OpenAI/Anthropic code paths persist via the
 * {@code sandbox:} scheme T83; for Gemini's inline image bytes a data URI is the
 * zero-dependency equivalent).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurGeminiCodeExecutionDecoder {

    private TurGeminiCodeExecutionDecoder() {
    }

    /** True when the part is a code-execution artifact (not plain text). */
    static boolean isCodeExecutionPart(Part part) {
        return part != null && (part.executableCode().isPresent()
                || part.codeExecutionResult().isPresent()
                || part.inlineData().isPresent());
    }

    /**
     * Render the code-execution parts of one turn into trailing markdown, or
     * {@code ""} when there are none. Plain-text parts are ignored (they are
     * already streamed via {@code GenerateContentResponse.text()}).
     */
    public static String render(List<Part> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Part part : parts) {
            renderExecutableCode(out, part.executableCode().orElse(null));
            renderResult(out, part.codeExecutionResult().orElse(null));
            renderInlineImage(out, part.inlineData().orElse(null));
        }
        return out.toString();
    }

    private static void renderExecutableCode(StringBuilder out, ExecutableCode code) {
        if (code == null) {
            return;
        }
        String source = code.code().orElse("");
        if (!StringUtils.hasText(source)) {
            return;
        }
        String lang = code.language().map(Object::toString).orElse("python")
                .toLowerCase(Locale.ROOT);
        // The SDK enum is e.g. "PYTHON"; normalise to a markdown fence hint.
        if (!lang.matches("[a-z0-9+#-]+")) {
            lang = "python";
        }
        out.append("\n\n```").append(lang).append('\n').append(source).append("\n```\n");
    }

    private static void renderResult(StringBuilder out, CodeExecutionResult result) {
        if (result == null) {
            return;
        }
        String output = result.output().orElse("");
        if (!StringUtils.hasText(output)) {
            return;
        }
        out.append("\n```text\n").append(output).append("\n```\n");
    }

    private static void renderInlineImage(StringBuilder out, Blob blob) {
        if (blob == null) {
            return;
        }
        byte[] data = blob.data().orElse(null);
        if (data == null || data.length == 0) {
            return;
        }
        String mime = blob.mimeType().orElse("image/png");
        if (!mime.startsWith("image/")) {
            // Only inline images are rendered; other binary parts are skipped.
            return;
        }
        String base64 = Base64.getEncoder().encodeToString(data);
        out.append("\n![generated](data:").append(mime).append(";base64,")
                .append(base64).append(")\n");
    }
}

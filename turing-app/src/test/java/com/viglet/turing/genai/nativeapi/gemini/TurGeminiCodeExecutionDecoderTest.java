/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.genai.types.Blob;
import com.google.genai.types.CodeExecutionResult;
import com.google.genai.types.ExecutableCode;
import com.google.genai.types.Part;

/**
 * T492 / §X.19 — unit coverage for rendering Gemini code_execution response
 * parts (code / output / inline image) into answer markdown.
 */
class TurGeminiCodeExecutionDecoderTest {

    private static Part code(String src) {
        return Part.builder().executableCode(ExecutableCode.builder().code(src).build()).build();
    }

    private static Part result(String output) {
        return Part.builder()
                .codeExecutionResult(CodeExecutionResult.builder().output(output).build())
                .build();
    }

    private static Part image(byte[] bytes, String mime) {
        return Part.builder()
                .inlineData(Blob.builder().data(bytes).mimeType(mime).build())
                .build();
    }

    @Test
    void rendersExecutableCodeAsFencedPythonBlock() {
        String md = TurGeminiCodeExecutionDecoder.render(List.of(code("print(2 + 2)")));
        assertThat(md).contains("```python");
        assertThat(md).contains("print(2 + 2)");
    }

    @Test
    void rendersResultAsTextBlock() {
        String md = TurGeminiCodeExecutionDecoder.render(List.of(result("4")));
        assertThat(md).contains("```text");
        assertThat(md).contains("4");
    }

    @Test
    void rendersInlineImageAsDataUri() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        String md = TurGeminiCodeExecutionDecoder.render(List.of(image(png, "image/png")));
        assertThat(md).contains("![generated](data:image/png;base64,");
    }

    @Test
    void skipsNonImageInlineData() {
        String md = TurGeminiCodeExecutionDecoder.render(
                List.of(image("hello".getBytes(), "application/octet-stream")));
        assertThat(md).isEmpty();
    }

    @Test
    void rendersCodeOutputAndImageInOrder() {
        String md = TurGeminiCodeExecutionDecoder.render(List.of(
                code("plot()"),
                result("done"),
                image(new byte[] {1, 2, 3}, "image/png")));
        assertThat(md.indexOf("```python")).isLessThan(md.indexOf("```text"));
        assertThat(md.indexOf("```text")).isLessThan(md.indexOf("data:image/png"));
    }

    @Test
    void emptyForNoParts() {
        assertThat(TurGeminiCodeExecutionDecoder.render(List.of())).isEmpty();
        assertThat(TurGeminiCodeExecutionDecoder.render(null)).isEmpty();
    }

    @Test
    void isCodeExecutionPartDetectsArtifactParts() {
        assertThat(TurGeminiCodeExecutionDecoder.isCodeExecutionPart(code("x=1"))).isTrue();
        assertThat(TurGeminiCodeExecutionDecoder.isCodeExecutionPart(Part.fromText("hi"))).isFalse();
    }
}

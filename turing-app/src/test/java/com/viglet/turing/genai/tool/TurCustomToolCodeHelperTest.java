/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.viglet.turing.genai.tool.TurCodeInterpreterToolService.TurCodeInterpreterFile;
import com.viglet.turing.genai.tool.TurCodeInterpreterToolService.TurCodeInterpreterResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests for the T83 structured / JSON additions to {@link TurCustomToolCodeHelper}
 * — the Groovy binding that lets Custom Tools read execution output as fields
 * instead of regex-parsing the markdown.
 */
class TurCustomToolCodeHelperTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static TurCodeInterpreterResult sampleResult() {
        return new TurCodeInterpreterResult(
                "sess1", true, 0, false, 1234L,
                "hello\n", "",
                List.of(new TurCodeInterpreterFile(
                        "proposta.pdf", "/api/v2/code-interpreter/sess1/proposta.pdf?sig=abc",
                        false, 4096L)),
                "hello\n--- Generated Files ---\n[Download proposta.pdf](...)\n");
    }

    @Test
    void executePythonStructuredShouldDelegateToTenantAwareService() {
        TurCodeInterpreterToolService service = mock(TurCodeInterpreterToolService.class);
        TurCodeInterpreterResult expected = sampleResult();
        when(service.executePythonStructuredForTenant(
                "print('x')", "reportlab", "agent-1", "conv-9"))
                .thenReturn(expected);

        TurCustomToolCodeHelper helper = new TurCustomToolCodeHelper(
                service, "reportlab", "agent-1", "conv-9");

        TurCodeInterpreterResult result = helper.executePythonStructured("print('x')");
        assertThat(result).isSameAs(expected);
    }

    @Test
    void executePythonStructuredShouldFailFastOnBlankCode() {
        TurCodeInterpreterToolService service = mock(TurCodeInterpreterToolService.class);
        TurCustomToolCodeHelper helper = new TurCustomToolCodeHelper(service);

        TurCodeInterpreterResult result = helper.executePythonStructured("   ");
        assertThat(result.success()).isFalse();
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(result.markdown()).isEqualTo("Error: empty Python code");
        assertThat(result.files()).isEmpty();
    }

    @Test
    void executePythonJsonShouldEmitSnakeCaseContractWithoutMarkdown() {
        TurCodeInterpreterToolService service = mock(TurCodeInterpreterToolService.class);
        when(service.executePythonStructuredForTenant(any(), any(), any(), any()))
                .thenReturn(sampleResult());

        TurCustomToolCodeHelper helper = new TurCustomToolCodeHelper(service);
        String json = helper.executePythonJson("print('x')");

        JsonNode node = MAPPER.readTree(json);
        assertThat(node.get("session_id").asString()).isEqualTo("sess1");
        assertThat(node.get("success").asBoolean()).isTrue();
        assertThat(node.get("exit_code").asInt()).isZero();
        assertThat(node.get("timed_out").asBoolean()).isFalse();
        assertThat(node.get("duration_ms").asLong()).isEqualTo(1234L);
        assertThat(node.get("stdout").asString()).isEqualTo("hello\n");
        assertThat(node.has("stderr")).isTrue();
        // markdown is deliberately excluded from the JSON contract.
        assertThat(node.has("markdown")).isFalse();

        JsonNode files = node.get("files");
        assertThat(files.isArray()).isTrue();
        assertThat(files).hasSize(1);
        JsonNode file = files.get(0);
        assertThat(file.get("name").asString()).isEqualTo("proposta.pdf");
        assertThat(file.get("url").asString())
                .isEqualTo("/api/v2/code-interpreter/sess1/proposta.pdf?sig=abc");
        assertThat(file.get("image").asBoolean()).isFalse();
        assertThat(file.get("size_bytes").asLong()).isEqualTo(4096L);
    }

    @Test
    void executePythonStructuredMapShouldPrependInputsPrelude() {
        TurCodeInterpreterToolService service = mock(TurCodeInterpreterToolService.class);
        when(service.executePythonStructuredForTenant(any(), any(), any(), any()))
                .thenReturn(sampleResult());

        TurCustomToolCodeHelper helper = new TurCustomToolCodeHelper(service);
        helper.executePythonStructured(Map.of(
                "script", "print(INPUTS['a'])",
                "inputs", Map.of("a", 1)));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(service).executePythonStructuredForTenant(
                codeCaptor.capture(), any(), any(), any());
        String code = codeCaptor.getValue();
        assertThat(code)
                .contains("INPUTS = _json.loads(_base64.b64decode(")
                .endsWith("print(INPUTS['a'])");
    }

    @Test
    void executePythonStructuredMapShouldFailOnMissingScript() {
        TurCodeInterpreterToolService service = mock(TurCodeInterpreterToolService.class);
        TurCustomToolCodeHelper helper = new TurCustomToolCodeHelper(service);

        TurCodeInterpreterResult result = helper.executePythonStructured(Map.of("inputs", Map.of("a", 1)));
        assertThat(result.success()).isFalse();
        assertThat(result.markdown()).isEqualTo("Error: missing or empty 'script' argument");
    }

    @Test
    void executePythonMapStringOverloadShouldKeepLegacyErrorMessages() {
        TurCodeInterpreterToolService service = mock(TurCodeInterpreterToolService.class);
        TurCustomToolCodeHelper helper = new TurCustomToolCodeHelper(service);

        assertThat(helper.executePython((Map<String, Object>) null))
                .isEqualTo("Error: missing named args (script:, inputs:)");
        assertThat(helper.executePython(Map.of("inputs", Map.of("a", 1))))
                .isEqualTo("Error: missing or empty 'script' argument");
    }
}

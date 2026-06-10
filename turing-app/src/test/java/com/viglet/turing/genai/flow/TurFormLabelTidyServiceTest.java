/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.agent.TurFormLabelTidyField;
import com.viglet.turing.persistence.dto.agent.TurFormLabelTidyResponse;
import com.viglet.turing.system.TurLlmSummaryService;

/**
 * Pins the T236 form-label tidy contract: the LLM rewrites only the labels,
 * slot {@code name} + {@code type} round-trip verbatim, labels re-pair by name
 * (not by order), a field the LLM dropped keeps its original label, and the
 * service degrades gracefully on a missing LLM / bad JSON / no-op rewrite.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurFormLabelTidyServiceTest {

    @Mock
    private TurLlmSummaryService llmSummaryService;

    private TurFormLabelTidyService service;

    @BeforeEach
    void setUp() {
        service = new TurFormLabelTidyService(llmSummaryService);
        lenient().when(llmSummaryService.isAvailable()).thenReturn(true);
    }

    private static List<TurFormLabelTidyField> rawFields() {
        return List.of(
                new TurFormLabelTidyField("email", "Qual o seu e-mail corporativo?", "email"),
                new TurFormLabelTidyField("name", "Por favor, informe o seu nome completo", "text"));
    }

    @Test
    void tidiesLabelsAndPreservesNameAndType() {
        String llmResponse = "{\"fields\":["
                + "  {\"name\":\"email\",\"label\":\"E-mail\"},"
                + "  {\"name\":\"name\",\"label\":\"Nome completo\"}"
                + "]}";
        when(llmSummaryService.generate(startsWith("form-label-tidy:"), any(), any(), eq(true)))
                .thenReturn(new TurLlmSummaryService.SummaryResult(true, null, llmResponse, true));

        TurFormLabelTidyResponse result = service.tidy(rawFields());

        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.fields()).hasSize(2);
        assertThat(result.fields().get(0))
                .isEqualTo(new TurFormLabelTidyField("email", "E-mail", "email"));
        assertThat(result.fields().get(1))
                .isEqualTo(new TurFormLabelTidyField("name", "Nome completo", "text"));
    }

    @Test
    void repairsByNameNotByOrder() {
        // LLM returns the fields in reverse order — re-pairing must be by name.
        String llmResponse = "{\"fields\":["
                + "  {\"name\":\"name\",\"label\":\"Nome completo\"},"
                + "  {\"name\":\"email\",\"label\":\"E-mail\"}"
                + "]}";
        when(llmSummaryService.generate(startsWith("form-label-tidy:"), any(), any(), anyBoolean()))
                .thenReturn(new TurLlmSummaryService.SummaryResult(true, null, llmResponse, true));

        TurFormLabelTidyResponse result = service.tidy(rawFields());

        assertThat(result.success()).isTrue();
        assertThat(result.fields().get(0).name()).isEqualTo("email");
        assertThat(result.fields().get(0).label()).isEqualTo("E-mail");
        assertThat(result.fields().get(1).name()).isEqualTo("name");
        assertThat(result.fields().get(1).label()).isEqualTo("Nome completo");
    }

    @Test
    void keepsOriginalLabelForFieldsTheLlmDropped() {
        String llmResponse = "{\"fields\":["
                + "  {\"name\":\"email\",\"label\":\"E-mail\"}"
                + "]}";
        when(llmSummaryService.generate(startsWith("form-label-tidy:"), any(), any(), anyBoolean()))
                .thenReturn(new TurLlmSummaryService.SummaryResult(true, null, llmResponse, true));

        TurFormLabelTidyResponse result = service.tidy(rawFields());

        assertThat(result.success()).isTrue();
        assertThat(result.fields().get(0).label()).isEqualTo("E-mail");
        // The dropped field falls back to its raw label (lossless).
        assertThat(result.fields().get(1).label())
                .isEqualTo("Por favor, informe o seu nome completo");
    }

    @Test
    void stripsLlmCodeFences() {
        String fenced = "```json\n{\"fields\":[{\"name\":\"email\",\"label\":\"E-mail\"}]}\n```";
        when(llmSummaryService.generate(startsWith("form-label-tidy:"), any(), any(), anyBoolean()))
                .thenReturn(new TurLlmSummaryService.SummaryResult(true, null, fenced, true));

        TurFormLabelTidyResponse result = service.tidy(rawFields());

        assertThat(result.success()).isTrue();
        assertThat(result.fields().get(0).label()).isEqualTo("E-mail");
    }

    @Test
    void failsWhenNoLlmConfigured() {
        when(llmSummaryService.isAvailable()).thenReturn(false);

        TurFormLabelTidyResponse result = service.tidy(rawFields());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("default LLM");
        verify(llmSummaryService, never()).generate(any(), any(), any(), anyBoolean());
    }

    @Test
    void failsOnEmptyInput() {
        TurFormLabelTidyResponse result = service.tidy(List.of());

        assertThat(result.success()).isFalse();
        verify(llmSummaryService, never()).generate(any(), any(), any(), anyBoolean());
    }

    @Test
    void failsWhenLlmResponseIsNotJson() {
        when(llmSummaryService.generate(startsWith("form-label-tidy:"), any(), any(), anyBoolean()))
                .thenReturn(new TurLlmSummaryService.SummaryResult(true, null, "nope", true));

        TurFormLabelTidyResponse result = service.tidy(rawFields());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("not valid JSON");
    }

    @Test
    void failsWhenNothingChanged() {
        // LLM echoes the labels verbatim → no improvement to apply.
        String llmResponse = "{\"fields\":["
                + "  {\"name\":\"email\",\"label\":\"Qual o seu e-mail corporativo?\"},"
                + "  {\"name\":\"name\",\"label\":\"Por favor, informe o seu nome completo\"}"
                + "]}";
        when(llmSummaryService.generate(startsWith("form-label-tidy:"), any(), any(), anyBoolean()))
                .thenReturn(new TurLlmSummaryService.SummaryResult(true, null, llmResponse, true));

        TurFormLabelTidyResponse result = service.tidy(rawFields());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("already concise");
    }
}

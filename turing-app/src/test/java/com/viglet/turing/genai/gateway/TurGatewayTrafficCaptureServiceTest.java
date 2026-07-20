/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.eval.TurEvalDatasetImportService;
import com.viglet.turing.genai.gateway.TurOpenAiWire.WireMessage;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.properties.TurGatewayProperty;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T746 / §XLIX — unit coverage for gateway traffic capture into an eval dataset.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurGatewayTrafficCaptureServiceTest {

    @Mock
    private TurEvalDatasetImportService importService;
    @Captor
    private ArgumentCaptor<List<? extends JsonNode>> rowsCaptor;

    private TurGatewayProperty property() {
        TurGatewayProperty p = new TurGatewayProperty();
        p.setCaptureTraffic(true);
        return p;
    }

    private TurGatewayTrafficCaptureService service(TurGatewayProperty property) {
        return new TurGatewayTrafficCaptureService(property, importService, new ObjectMapper());
    }

    private List<WireMessage> userMsg(String text) {
        return List.of(new WireMessage("user", text));
    }

    private TurLLMInstance instance(String id) {
        TurLLMInstance i = new TurLLMInstance();
        i.setId(id);
        return i;
    }

    @Test
    void noOpWhenDisabled() {
        TurGatewayProperty p = new TurGatewayProperty(); // captureTraffic = false
        service(p).capture("gpt-4o", userMsg("hi"), "hello", instance("i-1"), "k-1");
        verify(importService, never()).importCanonicalRows(anyString(), any());
        verify(importService, never()).appendCanonicalRows(anyString(), any());
    }

    @Test
    void firstCaptureCreatesDatasetWithCanonicalRow() {
        TurEvalDataset created = new TurEvalDataset();
        created.setId("ds-1");
        when(importService.importCanonicalRows(eq("Gateway Traffic"), rowsCaptor.capture()))
                .thenReturn(created);

        service(property()).capture("gpt-4o", userMsg("What is X?"), "X is Y.", instance("i-1"), "k-1");

        List<? extends JsonNode> rows = rowsCaptor.getValue();
        assertThat(rows).hasSize(1);
        JsonNode row = rows.get(0);
        assertThat(row.path("turns").get(0).asString()).isEqualTo("What is X?");
        assertThat(row.path("referenceAnswer").asString()).isEqualTo("X is Y.");
        assertThat(row.path("expectedOutcome").asString()).isEqualTo("ANY");
        assertThat(row.path("metadata").path("source").asString()).isEqualTo("gateway");
        assertThat(row.path("metadata").path("keyId").asString()).isEqualTo("k-1");
    }

    @Test
    void secondCaptureAppendsToExistingDataset() {
        TurEvalDataset created = new TurEvalDataset();
        created.setId("ds-1");
        when(importService.importCanonicalRows(anyString(), any())).thenReturn(created);
        TurGatewayTrafficCaptureService svc = service(property());

        svc.capture("gpt-4o", userMsg("a"), "answer-a", instance("i-1"), "k-1");
        svc.capture("gpt-4o", userMsg("b"), "answer-b", instance("i-1"), "k-1");

        assertThat(svc.currentDatasetId()).isEqualTo("ds-1");
        verify(importService).appendCanonicalRows(eq("ds-1"), any());
    }

    @Test
    void skipsWhenNoUserTurnsOrBlankAnswer() {
        service(property()).capture("gpt-4o", List.of(new WireMessage("system", "s")), "answer",
                instance("i-1"), "k-1");
        service(property()).capture("gpt-4o", userMsg("hi"), "  ", instance("i-1"), "k-1");
        verify(importService, never()).importCanonicalRows(anyString(), any());
    }
}

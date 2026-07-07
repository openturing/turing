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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.genai.TurSystemPromptPreviewService.FlowPersonaDiagnostic;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.prompt.TurPromptAssemblyPipeline;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.service.llm.price.TurLLMPriceService;

/**
 * T607 — flow-aware persona resolution in the Live Preview. Exercises the
 * transparent-node walk + catalog reconciliation via
 * {@link TurSystemPromptPreviewService#diagnoseFlowPersonas(TurAIAgent)}, which
 * mirrors the runtime {@code TurAgentPersonaResolver} silent-fallback rule
 * without needing the assembly pipeline.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSystemPromptPreviewServiceFlowPersonaTest {

    @Mock
    private TurPromptAssemblyPipeline promptAssemblyPipeline;
    @Mock
    private TurChatFlowEngineService chatFlowEngineService;
    @Mock
    private TurChatFlowRepository chatFlowRepository;
    @Mock
    private TurLLMPriceService priceService;

    private TurSystemPromptPreviewService service;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new TurSystemPromptPreviewService(
                promptAssemblyPipeline, chatFlowEngineService, chatFlowRepository, priceService);
    }

    /** start → persona(personaId) → aiQuestion — the canonical override path. */
    private static ChatFlowGraph graphWithPersonaNode(String personaId) throws Exception {
        String json = """
                {
                  "nodes": [
                    {"id": "n1", "type": "start", "data": {}},
                    {"id": "n2", "type": "persona", "data": {"personaId": "%s"}},
                    {"id": "n3", "type": "aiQuestion", "data": {"aiInstruction": "Ask something"}}
                  ],
                  "edges": [
                    {"id": "e1", "source": "n1", "target": "n2"},
                    {"id": "e2", "source": "n2", "target": "n3"}
                  ]
                }
                """.formatted(personaId);
        return MAPPER.readValue(json, ChatFlowGraph.class);
    }

    private TurChatFlow stubSingleFlow(ChatFlowGraph graph, String flowName) {
        TurChatFlow flow = new TurChatFlow();
        flow.setId("flow-1");
        flow.setName(flowName);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(any()))
                .thenReturn(List.of(flow));
        when(chatFlowEngineService.parseGraph(flow)).thenReturn(Optional.of(graph));
        return flow;
    }

    private static TurAIAgent agentWith(TurPersona... catalog) {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        agent.setPersonas(new java.util.HashSet<>(List.of(catalog)));
        return agent;
    }

    private static TurPersona persona(String id, String name, TurPersonaKind kind) {
        TurPersona p = new TurPersona();
        p.setId(id);
        p.setName(name);
        p.setPersonaKind(kind);
        return p;
    }

    @Test
    void validSpeakerOverrideProducesNoDiagnostic() throws Exception {
        stubSingleFlow(graphWithPersonaNode("p-marina"), "Concierge");
        TurAIAgent agent = agentWith(persona("p-marina", "Marina", TurPersonaKind.SPEAKER));

        assertThat(service.diagnoseFlowPersonas(agent)).isEmpty();
    }

    @Test
    void personaNotInCatalogIsFlagged() throws Exception {
        stubSingleFlow(graphWithPersonaNode("p-ghost"), "Concierge");
        TurAIAgent agent = agentWith(persona("p-marina", "Marina", TurPersonaKind.SPEAKER));

        List<FlowPersonaDiagnostic> diags = service.diagnoseFlowPersonas(agent);
        assertThat(diags).hasSize(1);
        assertThat(diags.get(0).notInCatalog()).isTrue();
        assertThat(diags.get(0).audienceOnly()).isFalse();
        assertThat(diags.get(0).personaRef()).isEqualTo("p-ghost");
        assertThat(diags.get(0).flowName()).isEqualTo("Concierge");
    }

    @Test
    void audienceOnlyOverrideIsFlagged() throws Exception {
        stubSingleFlow(graphWithPersonaNode("p-reader"), "Concierge");
        TurAIAgent agent = agentWith(persona("p-reader", "Reader", TurPersonaKind.AUDIENCE));

        List<FlowPersonaDiagnostic> diags = service.diagnoseFlowPersonas(agent);
        assertThat(diags).hasSize(1);
        assertThat(diags.get(0).audienceOnly()).isTrue();
        assertThat(diags.get(0).notInCatalog()).isFalse();
        assertThat(diags.get(0).personaRef()).isEqualTo("Reader");
    }

    @Test
    void flowWithoutPersonaNodeProducesNoDiagnostic() throws Exception {
        String json = """
                {
                  "nodes": [
                    {"id": "n1", "type": "start", "data": {}},
                    {"id": "n2", "type": "aiQuestion", "data": {"aiInstruction": "Ask"}}
                  ],
                  "edges": [{"id": "e1", "source": "n1", "target": "n2"}]
                }
                """;
        stubSingleFlow(MAPPER.readValue(json, ChatFlowGraph.class), "Concierge");
        TurAIAgent agent = agentWith(persona("p-marina", "Marina", TurPersonaKind.SPEAKER));

        assertThat(service.diagnoseFlowPersonas(agent)).isEmpty();
    }
}

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.genai.TurSystemPromptPreviewService.FlowPersonaDiagnostic;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.flow.TurChatFlowEngineService.ConversationStateDto;
import com.viglet.turing.genai.persona.TurAgentPersonaResolver;
import com.viglet.turing.genai.prompt.TurMessageAssemblyPipeline;
import com.viglet.turing.genai.prompt.TurPromptAssembly;
import com.viglet.turing.genai.prompt.TurPromptAssemblyContext;
import com.viglet.turing.genai.prompt.TurPromptAssemblyPipeline;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptPreviewDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.service.chatanalytics.TurToolCallTraceService;
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
    @Mock
    private TurAgentPersonaResolver personaResolver;
    @Mock
    private TurToolCallTraceService toolCallTraceService;
    @Mock
    private com.viglet.turing.genai.capture.TurPromptCaptureService promptCaptureService;

    private TurSystemPromptPreviewService service;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // No message contributors → the pipeline emits nothing, so the preview
        // surfaces the two memory sources as informational placeholders (T617).
        TurMessageAssemblyPipeline messageAssemblyPipeline =
                new TurMessageAssemblyPipeline(List.of());
        service = new TurSystemPromptPreviewService(promptAssemblyPipeline,
                messageAssemblyPipeline, chatFlowEngineService,
                chatFlowRepository, priceService, new TurInertRegionDetector(),
                personaResolver, toolCallTraceService, promptCaptureService);
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

    /** start → aiQuestion(n2) → aiQuestion(n3) — for the T611 node picker. */
    private static ChatFlowGraph twoQuestionGraph() throws Exception {
        String json = """
                {
                  "nodes": [
                    {"id": "n1", "type": "start", "data": {}},
                    {"id": "n2", "type": "aiQuestion", "data": {"label": "Ask cargo", "aiInstruction": "Ask the role"}},
                    {"id": "n3", "type": "aiQuestion", "data": {"label": "Ask goal", "aiInstruction": "Ask the goal"}}
                  ],
                  "edges": [
                    {"id": "e1", "source": "n1", "target": "n2"},
                    {"id": "e2", "source": "n2", "target": "n3"}
                  ]
                }
                """;
        return MAPPER.readValue(json, ChatFlowGraph.class);
    }

    @Test
    void nodePickerAndVariablesSeedTheFlowState() throws Exception {
        TurChatFlow flow = stubSingleFlow(twoQuestionGraph(), "Concierge");
        TurAIAgent agent = agentWith();
        when(promptAssemblyPipeline.assemble(any()))
                .thenReturn(new TurPromptAssembly(List.of(), "system text", -1));

        TurSystemPromptPreviewDto dto = service.buildPreview(agent, flow.getId(), "n3",
                "{\"cargo\":\"developer\"}");

        ArgumentCaptor<TurPromptAssemblyContext> captor =
                ArgumentCaptor.forClass(TurPromptAssemblyContext.class);
        verify(promptAssemblyPipeline).assemble(captor.capture());
        var flowContext = captor.getValue().flowContext();
        assertThat(flowContext).isNotNull();
        assertThat(flowContext.state().getCurrentNodeId()).isEqualTo("n3");
        assertThat(flowContext.state().getVariablesJson()).contains("cargo").contains("developer");

        assertThat(dto.selectedNodeId()).isEqualTo("n3");
        assertThat(dto.flowNodes()).extracting(n -> n.id()).containsExactly("n2", "n3");
    }

    @Test
    void previewSurfacesMemorySourcesAsPlaceholderMessages() {
        // No live conversation → the message pipeline emits nothing, so the preview
        // renders the T115 summary + T30 relevance sources as runtime-only
        // placeholders in the whole-message-list section (T617).
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(any())).thenReturn(List.of());
        when(promptAssemblyPipeline.assemble(any()))
                .thenReturn(new TurPromptAssembly(List.of(), "system text", -1));
        TurAIAgent agent = agentWith();
        agent.setSystemPrompt("You are helpful.");

        TurSystemPromptPreviewDto dto = service.buildPreview(agent);

        assertThat(dto.messages()).hasSize(2);
        assertThat(dto.messages()).extracting(m -> m.origin())
                .containsExactly("MEMORY_SUMMARY", "MEMORY_RELEVANCE");
        assertThat(dto.messages()).allMatch(m -> m.runtimeOnly() && m.content().isEmpty());
    }

    @Test
    void invalidVariablesJsonFallsBackToEmptyState() throws Exception {
        TurChatFlow flow = stubSingleFlow(twoQuestionGraph(), "Concierge");
        TurAIAgent agent = agentWith();
        when(promptAssemblyPipeline.assemble(any()))
                .thenReturn(new TurPromptAssembly(List.of(), "system text", -1));

        service.buildPreview(agent, flow.getId(), "n2", "not-json");

        ArgumentCaptor<TurPromptAssemblyContext> captor =
                ArgumentCaptor.forClass(TurPromptAssemblyContext.class);
        verify(promptAssemblyPipeline).assemble(captor.capture());
        assertThat(captor.getValue().flowContext().state().getVariablesJson()).isEqualTo("{}");
    }

    // ---------------------------------------------------------------------
    // T612 — replay the assembled prompt from a real conversation.
    // ---------------------------------------------------------------------

    private static ConversationStateDto conversationState(String flowId, String nodeId) {
        return new ConversationStateDto("conv-1", flowId, "Concierge", nodeId,
                null, null, null, null, null);
    }

    @Test
    void replaySeedsAssemblyFromRealConversationState() throws Exception {
        TurChatFlow flow = stubSingleFlow(twoQuestionGraph(), "Concierge");
        TurAIAgent agent = agentWith();
        TurPersona marina = persona("p-marina", "Marina", TurPersonaKind.SPEAKER);
        when(promptAssemblyPipeline.assemble(any()))
                .thenReturn(new TurPromptAssembly(List.of(), "system text", -1));
        // The conversation is parked on node n3 with a collected slot; the runtime
        // resolved the Marina persona and fired one tool.
        when(chatFlowEngineService.getConversationState("conv-1"))
                .thenReturn(conversationState(flow.getId(), "n3"));
        when(chatFlowEngineService.listSlotsForConversation("conv-1"))
                .thenReturn(new com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto(
                        "conv-1", java.util.Map.of("cargo", "developer")));
        when(personaResolver.resolve(agent, "conv-1")).thenReturn(marina);
        when(toolCallTraceService.getTrace("conv-1")).thenReturn(List.of(
                com.viglet.turing.genai.tool.TurChatToolCall.completed(
                        "c1", "search_knowledge_base", "{q=...}", true, 42L)));

        TurSystemPromptPreviewDto dto = service.buildReplayPreview(agent, "conv-1");

        ArgumentCaptor<TurPromptAssemblyContext> captor =
                ArgumentCaptor.forClass(TurPromptAssemblyContext.class);
        verify(promptAssemblyPipeline).assemble(captor.capture());
        var ctx = captor.getValue();
        // The real cursor node + collected slots drove the assembly, and the
        // resolver's persona won over the static graph walk.
        assertThat(ctx.flowContext().state().getCurrentNodeId()).isEqualTo("n3");
        assertThat(ctx.flowContext().state().getVariablesJson()).contains("cargo").contains("developer");
        assertThat(ctx.persona()).isSameAs(marina);

        assertThat(dto.replay()).isNotNull();
        assertThat(dto.replay().resolved()).isTrue();
        assertThat(dto.replay().conversationId()).isEqualTo("conv-1");
        assertThat(dto.replay().nodeId()).isEqualTo("n3");
        assertThat(dto.replay().personaName()).isEqualTo("Marina");
        assertThat(dto.replay().toolCalls()).hasSize(1);
        assertThat(dto.replay().toolCalls().get(0).name()).isEqualTo("search_knowledge_base");
        assertThat(dto.replay().toolCalls().get(0).status()).isEqualTo("ok");
        assertThat(dto.replay().toolCalls().get(0).durationMs()).isEqualTo(42L);
    }

    @Test
    void replayOfUnknownConversationDegradesToPlainPreview() {
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(any())).thenReturn(List.of());
        when(promptAssemblyPipeline.assemble(any()))
                .thenReturn(new TurPromptAssembly(List.of(), "system text", -1));
        // No persisted state → getConversationState returns an all-null DTO.
        when(chatFlowEngineService.getConversationState("ghost"))
                .thenReturn(conversationState(null, null));
        when(personaResolver.resolve(any(), any())).thenReturn(null);
        when(toolCallTraceService.getTrace("ghost")).thenReturn(List.of());
        TurAIAgent agent = agentWith();

        TurSystemPromptPreviewDto dto = service.buildReplayPreview(agent, "ghost");

        assertThat(dto.replay()).isNotNull();
        assertThat(dto.replay().resolved()).isFalse();
        assertThat(dto.replay().nodeId()).isNull();
        assertThat(dto.replay().toolCalls()).isEmpty();
    }

    @Test
    void replayIgnoresFlowThatIsNotThisAgents() throws Exception {
        // The agent owns flow-1, but the conversation's leaf flow is another
        // agent's flow-X → replay must not silently render flow-1's addendum.
        TurChatFlow owned = new TurChatFlow();
        owned.setId("flow-1");
        owned.setName("Concierge");
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(any()))
                .thenReturn(List.of(owned));
        TurAIAgent agent = agentWith();
        when(promptAssemblyPipeline.assemble(any()))
                .thenReturn(new TurPromptAssembly(List.of(), "system text", -1));
        when(chatFlowEngineService.getConversationState("conv-2"))
                .thenReturn(conversationState("flow-X", "nX"));
        when(personaResolver.resolve(agent, "conv-2")).thenReturn(null);
        when(toolCallTraceService.getTrace("conv-2")).thenReturn(List.of());

        TurSystemPromptPreviewDto dto = service.buildReplayPreview(agent, "conv-2");

        ArgumentCaptor<TurPromptAssemblyContext> captor =
                ArgumentCaptor.forClass(TurPromptAssemblyContext.class);
        verify(promptAssemblyPipeline).assemble(captor.capture());
        // FLOW_NONE → no flow context fed to the assembler.
        assertThat(captor.getValue().flowContext()).isNull();
        // Still marked resolved (the conversation had state), but no flow/node.
        assertThat(dto.replay().resolved()).isTrue();
        assertThat(dto.replay().flowName()).isNull();
        assertThat(dto.replay().nodeId()).isNull();
    }
}

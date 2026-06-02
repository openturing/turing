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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.genai.flow.strategy.TurChatFlowGuardrailStrategy;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowSubmissionRepository;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsService;
import com.viglet.turing.service.chatslots.TurChatSlotEventBus;

/**
 * T92 / §VII.11.b — pins the forced-flow contract of
 * {@link TurChatFlowEngineService#pinFlowForConversation}.
 *
 * <p>Two-axis matrix:
 * <ul>
 *   <li>Resolution: by id, by name (case-insensitive), unknown selector, wrong-agent flow,
 *       disabled flow.</li>
 *   <li>Side effects: reset prior in-progress states for the conversation; create exactly
 *       one new state on the chosen flow; seed slots collected before the pin.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurChatFlowPinFlowTest {

    private static final String AGENT_ID = "agent-1";
    private static final String CONV_ID = "conv-1";

    /**
     * Minimal flow definition with an aiQuestion that's NOT auto-satisfied
     * by the seed slot — so the transparent walker has no work to do and
     * the cursor stays on the question. Keeps the test focused on the
     * pin contract instead of the walker's side effects.
     */
    private static final String DEFINITION_ASK_AREA = """
            {
              "nodes": [
                {"id":"start","type":"start","data":{"label":"start"}},
                {"id":"ask-area","type":"aiQuestion","data":{
                    "label":"What area?","outputVariable":"area"
                }},
                {"id":"end","type":"end","data":{"label":"end"}}
              ],
              "edges": [
                {"id":"e1","source":"start","target":"ask-area"},
                {"id":"e2","source":"ask-area","target":"end"}
              ]
            }
            """;

    @Mock
    private TurChatFlowStateRepository stateRepository;
    @Mock
    private TurChatFlowRepository chatFlowRepository;
    @Mock
    private TurChatFlowSubmissionRepository submissionRepository;
    @Mock
    private TurChatAnalyticsService chatAnalyticsService;
    @Mock
    private TurChatSlotEventBus slotEventBus;
    @Mock
    private CacheManager cacheManager;

    private TurChatFlowEngineService engine;

    @BeforeEach
    void setUp() {
        engine = new TurChatFlowEngineService(stateRepository, chatFlowRepository,
                submissionRepository, chatAnalyticsService, slotEventBus,
                org.mockito.Mockito.mock(
                        com.viglet.turing.service.chatslots.TurChatSlotAuditService.class),
                org.mockito.Mockito.mock(
                        com.viglet.turing.service.chatanalytics.TurAbBanditService.class),
                cacheManager,
                org.mockito.Mockito.mock(TurFunctionCallNodeExecutor.class),
                org.mockito.Mockito.mock(
                        com.viglet.turing.genai.flow.routine.TurScheduleAgentNodeExecutor.class),
                org.mockito.Mockito.mock(TurChatWebhookNodeExecutor.class),
                List.<TurChatFlowGuardrailStrategy>of());
        // stateRepository.save returns the argument — production JPA would
        // populate the id, but the pin path doesn't rely on the id being
        // round-tripped (it persists the state for later reads, not for the
        // current call).
        lenient().when(stateRepository.save(any(TurChatFlowState.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // listSlotsForConversation walks both repos — default to empty so
        // tests that don't care about slots don't have to stub.
        lenient().when(submissionRepository.findByConversationIdOrderByCompletedAtDesc(CONV_ID))
                .thenReturn(List.of());
        lenient().when(stateRepository.findByConversationId(CONV_ID))
                .thenReturn(List.of());
    }

    @Test
    void pinsByFlowId_resetsPriorStates_andCreatesNewLeaf() {
        TurAIAgent agent = agent();
        TurChatFlow target = flow("flow-b", "B2B Quote", DEFINITION_ASK_AREA);
        TurChatFlow other = flow("flow-a", "Lead Capture", DEFINITION_ASK_AREA);

        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(other, target));
        TurChatFlowState priorState = stateOn(other, "ask-area");
        when(stateRepository.findByConversationIdAndFlow_TurAIAgent_Id(CONV_ID, AGENT_ID))
                .thenReturn(new ArrayList<>(List.of(priorState)));

        TurChatFlowEngineService.PinResult result =
                engine.pinFlowForConversation(agent, CONV_ID, "flow-b");

        assertThat(result.success()).isTrue();
        assertThat(result.pinnedFlowId()).isEqualTo("flow-b");
        assertThat(result.pinnedFlowName()).isEqualTo("B2B Quote");
        assertThat(result.reason()).isNull();
        verify(stateRepository).deleteAll(List.of(priorState));
        ArgumentCaptor<TurChatFlowState> saved = ArgumentCaptor.forClass(TurChatFlowState.class);
        verify(stateRepository, atLeastOnce()).save(saved.capture());
        TurChatFlowState created = saved.getAllValues().get(0);
        assertThat(created.getFlow().getId()).isEqualTo("flow-b");
        assertThat(created.getConversationId()).isEqualTo(CONV_ID);
        // Walker found nothing transparent → cursor sits on the question.
        assertThat(created.getCurrentNodeId()).isEqualTo("ask-area");
        verify(slotEventBus).publish(eq(CONV_ID), any());
    }

    @Test
    void pinsByFlowName_caseInsensitive() {
        TurAIAgent agent = agent();
        TurChatFlow target = flow("uuid-deadbeef", "in-company", DEFINITION_ASK_AREA);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(target));
        when(stateRepository.findByConversationIdAndFlow_TurAIAgent_Id(CONV_ID, AGENT_ID))
                .thenReturn(new ArrayList<>());

        TurChatFlowEngineService.PinResult result =
                engine.pinFlowForConversation(agent, CONV_ID, "IN-COMPANY");

        assertThat(result.success()).isTrue();
        assertThat(result.pinnedFlowId()).isEqualTo("uuid-deadbeef");
    }

    @Test
    void slotInheritanceMapping_seedsOnlyMappedSlots_withRename() {
        // T93 / §VII.11.c — receiving flow declares a mapping that picks
        // ONLY two slots and renames cargo_atual → position. Other slots
        // captured on prior flows must be left out of the new state.
        TurAIAgent agent = agent();
        TurChatFlow target = flow("flow-b", "B2B", DEFINITION_ASK_AREA);
        target.setSlotInheritanceJson("{\"name\":\"name\",\"position\":\"cargo_atual\"}");
        TurChatFlow other = flow("flow-a", "Lead Capture", DEFINITION_ASK_AREA);

        TurChatFlowState prior = stateOn(other, "ask-name");
        ChatFlowOps.writeVariables(prior, java.util.Map.of(
                "name", "Maria",
                "cargo_atual", "Diretora",
                "last_topic_searched", "preço"));

        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(other, target));
        when(stateRepository.findByConversationId(CONV_ID))
                .thenReturn(List.of(prior));
        when(stateRepository.findByConversationIdAndFlow_TurAIAgent_Id(CONV_ID, AGENT_ID))
                .thenReturn(new ArrayList<>(List.of(prior)));

        TurChatFlowEngineService.PinResult result =
                engine.pinFlowForConversation(agent, CONV_ID, "flow-b");

        assertThat(result.success()).isTrue();
        ArgumentCaptor<TurChatFlowState> saved = ArgumentCaptor.forClass(TurChatFlowState.class);
        verify(stateRepository, atLeastOnce()).save(saved.capture());
        TurChatFlowState created = saved.getAllValues().get(0);
        assertThat(ChatFlowOps.readVariables(created))
                .as("Mapping selects name (pass-through) and renames cargo_atual → position;"
                        + " everything else stays out")
                .containsEntry("name", "Maria")
                .containsEntry("position", "Diretora")
                .doesNotContainKey("last_topic_searched")
                .doesNotContainKey("cargo_atual");
    }

    @Test
    void slotInheritanceMapping_wildcardCopiesAllSlots() {
        // {"*":"*"} = "give me everything" — matches the legacy pin behaviour
        // but is the contractual way to opt in once T93 is generally adopted.
        TurAIAgent agent = agent();
        TurChatFlow target = flow("flow-b", "B2B", DEFINITION_ASK_AREA);
        target.setSlotInheritanceJson("{\"*\":\"*\"}");
        TurChatFlow other = flow("flow-a", "Lead Capture", DEFINITION_ASK_AREA);

        TurChatFlowState prior = stateOn(other, "ask-name");
        ChatFlowOps.writeVariables(prior, java.util.Map.of(
                "name", "Maria",
                "cargo_atual", "Diretora"));

        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(other, target));
        when(stateRepository.findByConversationId(CONV_ID))
                .thenReturn(List.of(prior));
        when(stateRepository.findByConversationIdAndFlow_TurAIAgent_Id(CONV_ID, AGENT_ID))
                .thenReturn(new ArrayList<>(List.of(prior)));

        engine.pinFlowForConversation(agent, CONV_ID, "flow-b");

        ArgumentCaptor<TurChatFlowState> saved = ArgumentCaptor.forClass(TurChatFlowState.class);
        verify(stateRepository, atLeastOnce()).save(saved.capture());
        TurChatFlowState created = saved.getAllValues().get(0);
        assertThat(ChatFlowOps.readVariables(created))
                .containsEntry("name", "Maria")
                .containsEntry("cargo_atual", "Diretora");
    }

    @Test
    void slotInheritanceMapping_silentlyDropsAbsentSources() {
        // Mapping references `email`, but the visitor never typed an email.
        // Result: name is copied, email is left absent — no null placeholder
        // leaks in (would otherwise mark the slot as "filled" for the
        // satisfied-questions walker and skip a legitimate question).
        TurAIAgent agent = agent();
        TurChatFlow target = flow("flow-b", "B2B", DEFINITION_ASK_AREA);
        target.setSlotInheritanceJson("{\"name\":\"name\",\"email\":\"email\"}");
        TurChatFlow other = flow("flow-a", "Lead Capture", DEFINITION_ASK_AREA);

        TurChatFlowState prior = stateOn(other, "ask-name");
        ChatFlowOps.writeVariables(prior, java.util.Map.of("name", "Maria"));

        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(other, target));
        when(stateRepository.findByConversationId(CONV_ID))
                .thenReturn(List.of(prior));
        when(stateRepository.findByConversationIdAndFlow_TurAIAgent_Id(CONV_ID, AGENT_ID))
                .thenReturn(new ArrayList<>(List.of(prior)));

        engine.pinFlowForConversation(agent, CONV_ID, "flow-b");

        ArgumentCaptor<TurChatFlowState> saved = ArgumentCaptor.forClass(TurChatFlowState.class);
        verify(stateRepository, atLeastOnce()).save(saved.capture());
        TurChatFlowState created = saved.getAllValues().get(0);
        assertThat(ChatFlowOps.readVariables(created))
                .containsEntry("name", "Maria")
                .doesNotContainKey("email");
    }

    @Test
    void slotInheritanceMapping_malformedJsonDoesNotCrashThePin() {
        // Authoring mistake — the pin must not stall the whole conversation.
        // Falls back to "no inheritance" (empty seed), pin still succeeds.
        TurAIAgent agent = agent();
        TurChatFlow target = flow("flow-b", "B2B", DEFINITION_ASK_AREA);
        target.setSlotInheritanceJson("{not valid json");

        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(target));
        when(stateRepository.findByConversationIdAndFlow_TurAIAgent_Id(CONV_ID, AGENT_ID))
                .thenReturn(new ArrayList<>());

        TurChatFlowEngineService.PinResult result =
                engine.pinFlowForConversation(agent, CONV_ID, "flow-b");

        assertThat(result.success()).isTrue();
    }

    @Test
    void seedsSlotsCapturedBeforeThePin() {
        TurAIAgent agent = agent();
        TurChatFlow target = flow("flow-b", "B2B", DEFINITION_ASK_AREA);
        TurChatFlow other = flow("flow-a", "Lead Capture", DEFINITION_ASK_AREA);

        // Existing state on a different flow carries a slot the visitor
        // already filled — must survive the pin so the receiving flow
        // does not re-ask for `name`.
        TurChatFlowState prior = stateOn(other, "ask-name");
        ChatFlowOps.writeVariables(prior, java.util.Map.of("name", "Maria"));

        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(other, target));
        when(stateRepository.findByConversationId(CONV_ID))
                .thenReturn(List.of(prior));
        when(stateRepository.findByConversationIdAndFlow_TurAIAgent_Id(CONV_ID, AGENT_ID))
                .thenReturn(new ArrayList<>(List.of(prior)));

        TurChatFlowEngineService.PinResult result =
                engine.pinFlowForConversation(agent, CONV_ID, "flow-b");

        assertThat(result.success()).isTrue();
        ArgumentCaptor<TurChatFlowState> saved = ArgumentCaptor.forClass(TurChatFlowState.class);
        verify(stateRepository, atLeastOnce()).save(saved.capture());
        TurChatFlowState created = saved.getAllValues().get(0);
        assertThat(ChatFlowOps.readVariables(created))
                .as("Seeded slot from the previous flow must carry over to the pinned flow's state")
                .containsEntry("name", "Maria");
    }

    @Test
    void returnsErrorForUnknownSelector() {
        TurAIAgent agent = agent();
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flow("flow-a", "Lead Capture", DEFINITION_ASK_AREA)));

        TurChatFlowEngineService.PinResult result =
                engine.pinFlowForConversation(agent, CONV_ID, "ghost");

        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("not found");
    }

    @Test
    void returnsErrorForDisabledFlow() {
        TurAIAgent agent = agent();
        TurChatFlow disabled = flow("flow-b", "B2B", DEFINITION_ASK_AREA);
        disabled.setEnabled(0);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(disabled));

        TurChatFlowEngineService.PinResult result =
                engine.pinFlowForConversation(agent, CONV_ID, "flow-b");

        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("disabled");
    }

    @Test
    void returnsErrorForUnparseableGraph() {
        TurAIAgent agent = agent();
        TurChatFlow broken = flow("flow-c", "Broken", "{not valid json");
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(broken));

        TurChatFlowEngineService.PinResult result =
                engine.pinFlowForConversation(agent, CONV_ID, "flow-c");

        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("parseable");
    }

    @Test
    void blankInputsShortCircuit() {
        TurAIAgent agent = agent();
        assertThat(engine.pinFlowForConversation(null, CONV_ID, "flow-b").success()).isFalse();
        assertThat(engine.pinFlowForConversation(agent, "", "flow-b").success()).isFalse();
        assertThat(engine.pinFlowForConversation(agent, CONV_ID, "  ").success()).isFalse();
    }

    private static TurAIAgent agent() {
        TurAIAgent a = new TurAIAgent();
        a.setId(AGENT_ID);
        return a;
    }

    private static TurChatFlow flow(String id, String name, String definitionJson) {
        TurChatFlow flow = new TurChatFlow();
        flow.setId(id);
        flow.setName(name);
        flow.setEnabled(1);
        flow.setDefinitionJson(definitionJson);
        TurAIAgent owning = new TurAIAgent();
        owning.setId(AGENT_ID);
        flow.setTurAIAgent(owning);
        return flow;
    }

    private static TurChatFlowState stateOn(TurChatFlow flow, String nodeId) {
        TurChatFlowState state = new TurChatFlowState();
        state.setId("state-" + flow.getId());
        state.setConversationId(CONV_ID);
        state.setFlow(flow);
        state.setCurrentNodeId(nodeId);
        state.setVariablesJson("{}");
        return state;
    }
}

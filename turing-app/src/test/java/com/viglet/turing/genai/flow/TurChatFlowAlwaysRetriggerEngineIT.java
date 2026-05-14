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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.genai.flow.TurChatFlowEngineService.FlowSelection;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.agent.TurChatFlowTriggerMode;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

import tools.jackson.databind.ObjectMapper;

/**
 * Reproduces the bug observed on the pizza-order flow:
 * <blockquote>
 *   First run completes successfully ({@code customerName}, {@code pizzaFlavor},
 *   … are captured). User says "quero fazer um pedido de pizza" again → flow
 *   re-triggers (mode = {@code ALWAYS}) → bot asks the visitor's name again
 *   even though the slot was filled on the previous run.
 * </blockquote>
 *
 * <p>The trace points at {@code selectActiveFlow}: on {@code ALWAYS}
 * re-trigger the engine <em>deletes</em> the prior state row and
 * {@link TurChatFlowEngineService#loadOrInitState} mints a fresh one with
 * {@code variablesJson="{}"}, which makes
 * {@link ChatFlowOps#walkThroughSatisfiedQuestions} have nothing to skip.
 * The test pins this down with a deterministic router (mocked {@link ChatModel})
 * so we don't need OpenAI to repro.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurChatFlowAlwaysRetriggerEngineIT extends AbstractTuringSpringIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TurChatFlowEngineService engine;
    @Autowired
    private TurChatFlowRepository chatFlowRepository;
    @Autowired
    private TurChatFlowStateRepository chatFlowStateRepository;
    @Autowired
    private TurAIAgentRepository agentRepository;

    private TurAIAgent agent;

    @BeforeEach
    void newAgent() {
        TurAIAgent a = new TurAIAgent();
        a.setTitle("retrigger-it-" + UUID.randomUUID().toString().substring(0, 8));
        a.setEnabled(1);
        agent = agentRepository.save(a);
    }

    // ─────────────────────────── Regression ───────────────────────────

    /**
     * Reproduces the pizza-order bug. Setup mirrors a session that already
     * completed the flow once: state row sits at the {@code end} node with
     * every slot filled in {@code variablesJson}. The user fires a second
     * "I want a pizza" message — {@code selectActiveFlow} sees the flow as
     * completed-but-ALWAYS and walks the re-trigger branch.
     *
     * <p>Expected outcome (the contract the user reported as broken): the
     * re-triggered state must preserve the previously-captured slot values
     * so {@link ChatFlowOps#walkThroughSatisfiedQuestions} fast-forwards
     * past every aiQuestion whose slot is already filled.
     */
    @Test
    void alwaysRetrigger_preservesPreviouslyCapturedSlots() {
        TurChatFlow flow = savePizzaLikeFlow();
        String conversationId = "conv-" + UUID.randomUUID();

        // 1) Simulate the prior run: state at end-node, all slots filled.
        Map<String, String> prior = Map.of(
                "customerName", "Alexandre Oliveira",
                "pizzaFlavor", "Portuguesa");
        saveEndState(flow, conversationId, prior);

        // 2) Re-trigger.
        ChatModel router = mockRouterPickingFlow(flow.getId());
        Optional<FlowSelection> selection = engine.selectActiveFlow(agent,
                conversationId, "quero fazer um pedido de pizza", router);

        assertThat(selection)
                .as("Router picked the flow — engine must return a selection")
                .isPresent();

        TurChatFlowState restarted = selection.get().state();
        assertThat(ChatFlowOps.readVariables(restarted))
                .as("Re-triggered state must carry prior slot values forward — that is "
                        + "what walkThroughSatisfiedQuestions depends on to skip already-filled "
                        + "aiQuestion nodes. Wiping them forces the user to re-answer the same "
                        + "questions, which is the bug observed on the pizza flow.")
                .containsEntry("customerName", "Alexandre Oliveira")
                .containsEntry("pizzaFlavor", "Portuguesa");
    }

    /**
     * Companion assertion: when the carried slots cover every aiQuestion on
     * the flow, the re-triggered state should NOT sit on the first
     * aiQuestion — the transparent walker is supposed to fast-forward.
     * Under the bug the engine plants the cursor on {@code askName} because
     * variables are empty. Under the fix it lands on the {@code end} node
     * (every slot satisfied) or on the next interactive node.
     */
    @Test
    void alwaysRetrigger_skipsAlreadyFilledQuestions() {
        TurChatFlow flow = savePizzaLikeFlow();
        String conversationId = "conv-" + UUID.randomUUID();
        Map<String, String> prior = Map.of(
                "customerName", "Alexandre Oliveira",
                "pizzaFlavor", "Portuguesa");
        saveEndState(flow, conversationId, prior);

        ChatModel router = mockRouterPickingFlow(flow.getId());
        Optional<FlowSelection> selection = engine.selectActiveFlow(agent,
                conversationId, "quero fazer um pedido de pizza", router);

        assertThat(selection).isPresent();
        assertThat(selection.get().state().getCurrentNodeId())
                .as("Both aiQuestion slots are pre-filled — walker must skip past askName")
                .isNotEqualTo("askName");
    }

    // ─────────────────────────── Helpers ───────────────────────────

    /**
     * Mocks the router LLM so it deterministically picks {@code flowId} — same
     * contract {@code TurChatFlowEngineService#askRouter} expects (reply is
     * just the flow id as plain text).
     */
    private static ChatModel mockRouterPickingFlow(String flowId) {
        ChatModel router = mock(ChatModel.class);
        ChatResponse response = new ChatResponse(List.of(
                new Generation(new AssistantMessage(flowId))));
        when(router.call(any(Prompt.class))).thenReturn(response);
        return router;
    }

    /**
     * Builds a minimal pizza-like flow: {@code start → askName → askFlavor → end}.
     * Trigger mode is {@code ALWAYS} and the description is non-blank — both
     * requirements for {@link TurChatFlowEngineService#selectActiveFlow} to
     * consider the flow as a router candidate.
     */
    private TurChatFlow savePizzaLikeFlow() {
        TurChatFlow f = new TurChatFlow();
        f.setName("pizza-like-" + UUID.randomUUID().toString().substring(0, 6));
        f.setEnabled(1);
        f.setTurAIAgent(agent);
        f.setTriggerMode(TurChatFlowTriggerMode.ALWAYS);
        f.setTriggerDescription("Activate when the user wants to order a pizza.");
        f.setDefinitionJson(pizzaLikeGraph());
        return chatFlowRepository.save(f);
    }

    /**
     * Saves a state row anchored on the {@code end} node with a fully
     * populated {@code variablesJson}. Mimics the end of a successful first
     * run that has already been recorded as a submission.
     */
    private TurChatFlowState saveEndState(TurChatFlow flow, String conversationId,
            Map<String, String> variables) {
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId(conversationId);
        state.setFlow(flow);
        state.setCurrentNodeId("end");
        state.setVariablesJson(JSON.writeValueAsString(variables));
        return chatFlowStateRepository.save(state);
    }

    private static String pizzaLikeGraph() {
        List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        List<Map<String, Object>> edges = new java.util.ArrayList<>();

        nodes.add(node("start", "start", nodeData("START", "start", null)));
        nodes.add(node("askName", "aiQuestion", nodeData("ASK NAME", "aiQuestion", Map.of(
                "aiInstruction", "Cumprimente o cliente e pergunte o nome dele.",
                "outputVariable", "customerName"))));
        nodes.add(node("askFlavor", "aiQuestion", nodeData("ASK FLAVOR", "aiQuestion", Map.of(
                "aiInstruction", "Pergunte qual o sabor da pizza.",
                "outputVariable", "pizzaFlavor"))));
        nodes.add(node("end", "end", nodeData("END", "end", null)));

        edges.add(edge("e0", "start", "askName"));
        edges.add(edge("e1", "askName", "askFlavor"));
        edges.add(edge("e2", "askFlavor", "end"));

        return serialize(nodes, edges);
    }

    private static Map<String, Object> nodeData(String label, String type,
            Map<String, Object> extra) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("label", label);
        data.put("type", type);
        if (extra != null) {
            data.putAll(extra);
        }
        return data;
    }

    private static Map<String, Object> node(String id, String type, Map<String, Object> data) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", type);
        node.put("data", data);
        return node;
    }

    private static Map<String, Object> edge(String id, String source, String target) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("id", id);
        edge.put("source", source);
        edge.put("target", target);
        edge.put("sourceHandle", null);
        edge.put("targetHandle", null);
        edge.put("label", null);
        return edge;
    }

    private static String serialize(List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges) {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        return JSON.writeValueAsString(graph);
    }
}

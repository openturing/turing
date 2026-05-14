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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

import tools.jackson.databind.ObjectMapper;

/**
 * Integration test for the {@code aiQuestion} skip-if-filled walker —
 * {@link ChatFlowOps#walkThroughSatisfiedQuestions}. The walker is wired
 * into the engine's {@code walkTransparentNodes} loop next to the existing
 * condition / switch walkers; tests exercise it directly so assertions
 * stay deterministic (no LLM round-trip required).
 *
 * <p>Mirrors the conventions of {@link TurChatFlowSwitchEngineIT}: extends
 * {@link AbstractTuringSpringIT} for the ephemeral H2 + full Liquibase
 * pipeline, builds flow graphs in code, and persists state through the
 * real repository so the migration's column types are exercised on the
 * way in and out.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurChatFlowSkipIfFilledEngineIT extends AbstractTuringSpringIT {

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
        a.setTitle("skip-it-" + UUID.randomUUID().toString().substring(0, 8));
        a.setEnabled(1);
        agent = agentRepository.save(a);
    }

    @Test
    void filledSlot_isSkippedToNextNode() {
        TurChatFlow flow = saveFlow("skip-name",
                twoQuestionFlow("visitor_name", "visitor_email", false, false));
        TurChatFlowState state = saveState(flow, "askName",
                Map.of("visitor_name", "Maria"));

        ChatFlowOps.walkThroughSatisfiedQuestions(state, parseGraph(flow));

        assertThat(state.getCurrentNodeId())
                .as("visitor_name already filled — should skip past askName")
                .isEqualTo("askEmail");
    }

    @Test
    void allSlotsFilled_walksAllTheWayToEnd() {
        TurChatFlow flow = saveFlow("skip-both",
                twoQuestionFlow("visitor_name", "visitor_email", false, false));
        TurChatFlowState state = saveState(flow, "askName", Map.of(
                "visitor_name", "Maria",
                "visitor_email", "maria@example.com"));

        ChatFlowOps.walkThroughSatisfiedQuestions(state, parseGraph(flow));

        assertThat(state.getCurrentNodeId())
                .as("Both slots filled — walker should land on the end node")
                .isEqualTo("end");
    }

    @Test
    void emptySlot_doesNotSkip() {
        TurChatFlow flow = saveFlow("no-skip-empty",
                twoQuestionFlow("visitor_name", "visitor_email", false, false));
        TurChatFlowState state = saveState(flow, "askName", Map.of());

        ChatFlowOps.walkThroughSatisfiedQuestions(state, parseGraph(flow));

        assertThat(state.getCurrentNodeId())
                .as("No value collected yet — must stay on askName so the user is prompted")
                .isEqualTo("askName");
    }

    @Test
    void blankSlotValue_doesNotSkip() {
        TurChatFlow flow = saveFlow("no-skip-blank",
                twoQuestionFlow("visitor_name", "visitor_email", false, false));
        TurChatFlowState state = saveState(flow, "askName",
                Map.of("visitor_name", "   "));

        ChatFlowOps.walkThroughSatisfiedQuestions(state, parseGraph(flow));

        assertThat(state.getCurrentNodeId())
                .as("Whitespace-only value should not satisfy the slot — keep asking")
                .isEqualTo("askName");
    }

    @Test
    void overrideExistingValue_disablesSkip() {
        // First node opts in to always-ask via overrideExistingValue=true.
        // Even though the slot is filled, the walker must NOT skip it.
        TurChatFlow flow = saveFlow("override-on",
                twoQuestionFlow("visitor_name", "visitor_email",
                        /* nameOverride */ true, /* emailOverride */ false));
        TurChatFlowState state = saveState(flow, "askName",
                Map.of("visitor_name", "Maria",
                        "visitor_email", "maria@example.com"));

        ChatFlowOps.walkThroughSatisfiedQuestions(state, parseGraph(flow));

        assertThat(state.getCurrentNodeId())
                .as("Override flag forces the engine to always ask — no skip")
                .isEqualTo("askName");
    }

    @Test
    void variablesAreNotMutatedBySkip() {
        // The walker advances the cursor but must NOT touch the variables map —
        // adjacent strategies still need to read previously-collected values.
        TurChatFlow flow = saveFlow("preserve-vars",
                twoQuestionFlow("visitor_name", "visitor_email", false, false));
        String varsJsonBefore = JSON.writeValueAsString(Map.of(
                "visitor_name", "Maria",
                "leftover", "Untouched"));
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId("conv-" + UUID.randomUUID());
        state.setFlow(flow);
        state.setCurrentNodeId("askName");
        state.setVariablesJson(varsJsonBefore);
        state = chatFlowStateRepository.save(state);

        ChatFlowOps.walkThroughSatisfiedQuestions(state, parseGraph(flow));

        assertThat(state.getCurrentNodeId()).isEqualTo("askEmail");
        assertThat(state.getVariablesJson()).isEqualTo(varsJsonBefore);
    }

    @Test
    void nonAiQuestionNode_isNeverSkipped() {
        // Walker is a no-op when the current node isn't an aiQuestion — sitting
        // on the end node must stay stable even with every slot filled.
        TurChatFlow flow = saveFlow("noop-on-end",
                twoQuestionFlow("visitor_name", "visitor_email", false, false));
        TurChatFlowState state = saveState(flow, "end", Map.of(
                "visitor_name", "Maria",
                "visitor_email", "maria@example.com"));

        ChatFlowOps.walkThroughSatisfiedQuestions(state, parseGraph(flow));

        assertThat(state.getCurrentNodeId()).isEqualTo("end");
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private TurChatFlow saveFlow(String name, String definitionJson) {
        TurChatFlow f = new TurChatFlow();
        f.setName(name + "-" + UUID.randomUUID().toString().substring(0, 6));
        f.setDefinitionJson(definitionJson);
        f.setEnabled(1);
        f.setTurAIAgent(agent);
        return chatFlowRepository.save(f);
    }

    private TurChatFlowState saveState(TurChatFlow flow, String currentNodeId,
            Map<String, String> variables) {
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId("conv-" + UUID.randomUUID());
        state.setFlow(flow);
        state.setCurrentNodeId(currentNodeId);
        state.setVariablesJson(JSON.writeValueAsString(variables));
        return chatFlowStateRepository.save(state);
    }

    private ChatFlowGraph parseGraph(TurChatFlow flow) {
        return engine.parseGraph(flow).orElseThrow();
    }

    /**
     * Builds {@code start → askName(outputVariable=firstSlot) → askEmail(outputVariable=secondSlot) → end}.
     * Each {@code aiQuestion} carries its own override flag so the tests
     * can toggle skip-if-filled behaviour per node.
     */
    private static String twoQuestionFlow(String firstSlot, String secondSlot,
            boolean nameOverride, boolean emailOverride) {
        List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        List<Map<String, Object>> edges = new java.util.ArrayList<>();

        nodes.add(node("start", "start", nodeData("START", "start", null)));
        nodes.add(node("askName", "aiQuestion", nodeData("ASK NAME", "aiQuestion", Map.of(
                "aiInstruction", "Pergunte o nome do visitante.",
                "outputVariable", firstSlot,
                "overrideExistingValue", nameOverride))));
        nodes.add(node("askEmail", "aiQuestion", nodeData("ASK EMAIL", "aiQuestion", Map.of(
                "aiInstruction", "Pergunte o e-mail.",
                "outputVariable", secondSlot,
                "overrideExistingValue", emailOverride))));
        nodes.add(node("end", "end", nodeData("END", "end", null)));

        edges.add(edge("e0", "start", "askName", null));
        edges.add(edge("e1", "askName", "askEmail", null));
        edges.add(edge("e2", "askEmail", "end", null));

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

    private static Map<String, Object> edge(String id, String source, String target,
            String sourceHandle) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("id", id);
        edge.put("source", source);
        edge.put("target", target);
        edge.put("sourceHandle", sourceHandle);
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

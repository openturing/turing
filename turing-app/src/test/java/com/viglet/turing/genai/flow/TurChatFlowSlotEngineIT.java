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
 * Integration test for the {@code slot} node runtime — the transparent step
 * that writes (SET) or removes (DELETE) a value in the conversation's
 * variable map. Exercises {@link ChatFlowOps#applySlotNode} directly so the
 * assertions stay deterministic (no LLM round-trip required) and also
 * verifies the engine's transparent walker descends past a {@code slot} node
 * at flow initialisation, leaving the variables persisted on the resulting
 * state.
 *
 * <p>Mirrors the conventions of {@link TurChatFlowSkipIfFilledEngineIT}:
 * extends {@link AbstractTuringSpringIT} for the ephemeral H2 + full
 * Liquibase pipeline, builds flow graphs in code, persists state through
 * the real repository.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurChatFlowSlotEngineIT extends AbstractTuringSpringIT {

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
        a.setTitle("slot-it-" + UUID.randomUUID().toString().substring(0, 8));
        a.setEnabled(1);
        agent = agentRepository.save(a);
    }

    // ─────────────────────────── SET ───────────────────────────

    @Test
    void setOnEmptySlot_writesValue() {
        TurChatFlow flow = saveFlow("slot-set-empty",
                slotFlow("SET", "visitor_name", "Maria", false));
        TurChatFlowState state = saveState(flow, "slot-1", Map.of());

        ChatFlowOps.applySlotNode(state, currentNode(state, flow));

        assertThat(ChatFlowOps.readVariables(state))
                .as("SET on empty slot writes the configured value")
                .containsEntry("visitor_name", "Maria");
    }

    @Test
    void setWithoutOverride_preservesExistingValue() {
        TurChatFlow flow = saveFlow("slot-set-no-override",
                slotFlow("SET", "visitor_name", "FromNode", /* override */ false));
        TurChatFlowState state = saveState(flow, "slot-1",
                Map.of("visitor_name", "FromUser"));

        ChatFlowOps.applySlotNode(state, currentNode(state, flow));

        assertThat(ChatFlowOps.readVariables(state))
                .as("override=false must NOT overwrite a slot that already has a value")
                .containsEntry("visitor_name", "FromUser");
    }

    @Test
    void setWithOverride_overwritesExistingValue() {
        TurChatFlow flow = saveFlow("slot-set-override",
                slotFlow("SET", "visitor_name", "FromNode", /* override */ true));
        TurChatFlowState state = saveState(flow, "slot-1",
                Map.of("visitor_name", "FromUser"));

        ChatFlowOps.applySlotNode(state, currentNode(state, flow));

        assertThat(ChatFlowOps.readVariables(state))
                .as("override=true overwrites the slot even when it already had a value")
                .containsEntry("visitor_name", "FromNode");
    }

    @Test
    void setOnBlankSlot_writesValueWithoutOverride() {
        // A whitespace-only value should NOT count as a real fill: the node
        // is free to write its own value even without override.
        TurChatFlow flow = saveFlow("slot-set-blank",
                slotFlow("SET", "visitor_name", "Maria", /* override */ false));
        TurChatFlowState state = saveState(flow, "slot-1",
                Map.of("visitor_name", "   "));

        ChatFlowOps.applySlotNode(state, currentNode(state, flow));

        assertThat(ChatFlowOps.readVariables(state))
                .as("Blank value does not satisfy the slot — SET should still write")
                .containsEntry("visitor_name", "Maria");
    }

    @Test
    void setWithNullValue_writesEmptyString() {
        // SET with a null slotValue is a legitimate way to seed an empty
        // slot (e.g. before a downstream node fills it). Coerced to "".
        TurChatFlow flow = saveFlow("slot-set-null",
                slotFlow("SET", "visitor_name", /* value */ null, /* override */ true));
        TurChatFlowState state = saveState(flow, "slot-1",
                Map.of("visitor_name", "FromUser"));

        ChatFlowOps.applySlotNode(state, currentNode(state, flow));

        assertThat(ChatFlowOps.readVariables(state))
                .as("Null slotValue is coerced to the empty string")
                .containsEntry("visitor_name", "");
    }

    @Test
    void setPreservesUnrelatedVariables() {
        TurChatFlow flow = saveFlow("slot-set-preserves",
                slotFlow("SET", "visitor_name", "Maria", true));
        TurChatFlowState state = saveState(flow, "slot-1", Map.of(
                "visitor_name", "FromUser",
                "leftover", "Untouched"));

        ChatFlowOps.applySlotNode(state, currentNode(state, flow));

        assertThat(ChatFlowOps.readVariables(state))
                .as("Variables outside the slot must be left alone")
                .containsEntry("visitor_name", "Maria")
                .containsEntry("leftover", "Untouched");
    }

    // ─────────────────────────── DELETE ───────────────────────────

    @Test
    void deleteRemovesExistingSlot() {
        TurChatFlow flow = saveFlow("slot-delete-existing",
                slotFlow("DELETE", "visitor_name", null, false));
        TurChatFlowState state = saveState(flow, "slot-1", Map.of(
                "visitor_name", "FromUser",
                "leftover", "Untouched"));

        ChatFlowOps.applySlotNode(state, currentNode(state, flow));

        Map<String, String> vars = ChatFlowOps.readVariables(state);
        assertThat(vars)
                .as("DELETE removes the named slot and leaves siblings alone")
                .doesNotContainKey("visitor_name")
                .containsEntry("leftover", "Untouched");
    }

    @Test
    void deleteOnMissingSlot_isNoOp() {
        TurChatFlow flow = saveFlow("slot-delete-missing",
                slotFlow("DELETE", "visitor_name", null, false));
        TurChatFlowState state = saveState(flow, "slot-1",
                Map.of("leftover", "Untouched"));

        ChatFlowOps.applySlotNode(state, currentNode(state, flow));

        assertThat(ChatFlowOps.readVariables(state))
                .as("DELETE on a slot that was never set leaves the variables untouched")
                .containsExactlyEntriesOf(Map.of("leftover", "Untouched"));
    }

    // ─────────────────────────── Defensive ───────────────────────────

    @Test
    void blankSlotName_isNoOp() {
        TurChatFlow flow = saveFlow("slot-blank-name",
                slotFlow("SET", /* name */ "", "Maria", true));
        Map<String, String> initial = Map.of("visitor_name", "FromUser");
        TurChatFlowState state = saveState(flow, "slot-1", initial);

        ChatFlowOps.applySlotNode(state, currentNode(state, flow));

        assertThat(ChatFlowOps.readVariables(state))
                .as("Misconfigured node (blank slotName) must not touch state")
                .containsExactlyEntriesOf(initial);
    }

    @Test
    void unknownOperation_isNoOp() {
        TurChatFlow flow = saveFlow("slot-bogus-op",
                slotFlow("UPSERT", "visitor_name", "Maria", true));
        Map<String, String> initial = Map.of("visitor_name", "FromUser");
        TurChatFlowState state = saveState(flow, "slot-1", initial);

        ChatFlowOps.applySlotNode(state, currentNode(state, flow));

        assertThat(ChatFlowOps.readVariables(state))
                .as("Unknown slotOperation must be ignored — state stays untouched")
                .containsExactlyEntriesOf(initial);
    }

    @Test
    void nullOperation_defaultsToSet() {
        // When the export omits slotOperation (older builds), we treat the
        // node as SET so curated graphs keep working without migration.
        TurChatFlow flow = saveFlow("slot-null-op",
                slotFlow(/* operation */ null, "visitor_name", "Maria", true));
        TurChatFlowState state = saveState(flow, "slot-1", Map.of());

        ChatFlowOps.applySlotNode(state, currentNode(state, flow));

        assertThat(ChatFlowOps.readVariables(state))
                .as("Missing slotOperation defaults to SET")
                .containsEntry("visitor_name", "Maria");
    }

    // ─────────────────────────── Engine walker ───────────────────────────

    @Test
    void engineWalksPastSlotNodeOnInit_andPersistsTheWrite() {
        // start → slot(SET visitor_name=Maria) → askEmail(aiQuestion) → end
        // loadOrInitState anchors on the first node after start (the slot),
        // then walkTransparentNodes descends past it onto askEmail and persists
        // the resulting variables to the DB.
        TurChatFlow flow = saveFlow("slot-walk-init",
                slotThenQuestionFlow("SET", "visitor_name", "Maria", false));
        String conversationId = "conv-" + UUID.randomUUID();

        ChatFlowGraph graph = engine.parseGraph(flow).orElseThrow();
        TurChatFlowState state = engine.loadOrInitState(conversationId, flow, graph).orElseThrow();

        assertThat(state.getCurrentNodeId())
                .as("Engine must walk past the transparent slot node and land on askEmail")
                .isEqualTo("askEmail");
        assertThat(ChatFlowOps.readVariables(state))
                .as("The slot SET performed during the walk must be persisted on the state")
                .containsEntry("visitor_name", "Maria");

        // Re-load from the DB to confirm the variable survived the save.
        TurChatFlowState reloaded = chatFlowStateRepository.findById(state.getId()).orElseThrow();
        assertThat(ChatFlowOps.readVariables(reloaded))
                .as("Persisted state must carry the SET — not just the in-memory copy")
                .containsEntry("visitor_name", "Maria");
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

    private ChatFlowNode currentNode(TurChatFlowState state, TurChatFlow flow) {
        ChatFlowGraph graph = engine.parseGraph(flow).orElseThrow();
        return graph.nodeById(state.getCurrentNodeId()).orElseThrow();
    }

    /**
     * Builds {@code start → slot-1(slotOperation/slotName/slotValue, override) → end}.
     * Single-step graph used by the {@code applySlotNode} unit-style tests.
     */
    private static String slotFlow(String operation, String slotName, String slotValue,
            boolean override) {
        Map<String, Object> slotData = new LinkedHashMap<>();
        slotData.put("label", "SLOT");
        slotData.put("type", "slot");
        slotData.put("slotName", slotName);
        if (operation != null) {
            slotData.put("slotOperation", operation);
        }
        if (slotValue != null) {
            slotData.put("slotValue", slotValue);
        }
        if (override) {
            slotData.put("overrideExistingValue", true);
        }

        List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        List<Map<String, Object>> edges = new java.util.ArrayList<>();
        nodes.add(node("start", "start", nodeData("START", "start", null)));
        nodes.add(node("slot-1", "slot", slotData));
        nodes.add(node("end", "end", nodeData("END", "end", null)));
        edges.add(edge("e0", "start", "slot-1"));
        edges.add(edge("e1", "slot-1", "end"));

        return serialize(nodes, edges);
    }

    /**
     * Builds {@code start → slot-1 → askEmail(aiQuestion) → end}. Used to
     * exercise the engine's transparent walker on init.
     */
    private static String slotThenQuestionFlow(String operation, String slotName,
            String slotValue, boolean override) {
        Map<String, Object> slotData = new LinkedHashMap<>();
        slotData.put("label", "SLOT");
        slotData.put("type", "slot");
        slotData.put("slotName", slotName);
        slotData.put("slotOperation", operation);
        if (slotValue != null) {
            slotData.put("slotValue", slotValue);
        }
        if (override) {
            slotData.put("overrideExistingValue", true);
        }

        Map<String, Object> askData = new LinkedHashMap<>();
        askData.put("label", "ASK EMAIL");
        askData.put("type", "aiQuestion");
        askData.put("aiInstruction", "Pergunte o e-mail do visitante.");
        askData.put("outputVariable", "visitor_email");

        List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        List<Map<String, Object>> edges = new java.util.ArrayList<>();
        nodes.add(node("start", "start", nodeData("START", "start", null)));
        nodes.add(node("slot-1", "slot", slotData));
        nodes.add(node("askEmail", "aiQuestion", askData));
        nodes.add(node("end", "end", nodeData("END", "end", null)));
        edges.add(edge("e0", "start", "slot-1"));
        edges.add(edge("e1", "slot-1", "askEmail"));
        edges.add(edge("e2", "askEmail", "end"));

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

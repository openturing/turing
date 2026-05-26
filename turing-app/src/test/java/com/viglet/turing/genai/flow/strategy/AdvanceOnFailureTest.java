/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.flow.ChatFlowEdge;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.ChatFlowNode.NodeData;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

/**
 * Pin tests for the T49 failure-routing helper
 * {@link ChatFlowOps#advanceOnFailure(TurChatFlowState, ChatFlowGraph, ChatFlowNode)}
 * and the {@link ChatFlowNode#continueOnFailure()} accessor. Pure-static — no
 * Spring context, no executor mocks; exercises only the graph-navigation logic
 * the engine uses when a {@code functionCall} / {@code scheduleAgent} node opts
 * in to try/catch routing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class AdvanceOnFailureTest {

    private static final String NODE_ID = "fc-1";
    private static final String RECOVERY_ID = "recovery";
    private static final String NORMAL_ID = "normal";

    @Test
    @DisplayName("failure handle wired → routes to that target")
    void failureHandleWired_routesToFailureTarget() {
        ChatFlowNode source = functionCallNode(true);
        ChatFlowNode normal = endNode(NORMAL_ID);
        ChatFlowNode recovery = endNode(RECOVERY_ID);
        ChatFlowGraph graph = new ChatFlowGraph(
                List.of(source, normal, recovery),
                List.of(
                        edge("e-normal", NODE_ID, NORMAL_ID, null),
                        edge("e-failure", NODE_ID, RECOVERY_ID, ChatFlowOps.FAILURE_HANDLE)));
        TurChatFlowState state = stateAt(NODE_ID);

        String advancedTo = ChatFlowOps.advanceOnFailure(state, graph, source);

        assertThat(advancedTo).isEqualTo(RECOVERY_ID);
        assertThat(state.getCurrentNodeId()).isEqualTo(RECOVERY_ID);
    }

    @Test
    @DisplayName("failure handle case-insensitive (\"FAILURE\" still matches)")
    void failureHandle_caseInsensitive() {
        ChatFlowNode source = functionCallNode(true);
        ChatFlowGraph graph = new ChatFlowGraph(
                List.of(source, endNode(NORMAL_ID), endNode(RECOVERY_ID)),
                List.of(
                        edge("e-normal", NODE_ID, NORMAL_ID, null),
                        edge("e-failure", NODE_ID, RECOVERY_ID, "FAILURE")));
        TurChatFlowState state = stateAt(NODE_ID);

        ChatFlowOps.advanceOnFailure(state, graph, source);

        assertThat(state.getCurrentNodeId()).isEqualTo(RECOVERY_ID);
    }

    @Test
    @DisplayName("no failure handle wired → falls back to first outgoing edge")
    void noFailureHandle_fallsBackToFirstEdge() {
        ChatFlowNode source = functionCallNode(true);
        ChatFlowGraph graph = new ChatFlowGraph(
                List.of(source, endNode(NORMAL_ID), endNode("extra")),
                List.of(
                        edge("e-normal", NODE_ID, NORMAL_ID, null),
                        edge("e-extra", NODE_ID, "extra", "timeout")));
        TurChatFlowState state = stateAt(NODE_ID);

        ChatFlowOps.advanceOnFailure(state, graph, source);

        // First outgoing edge wins as the lenient fallback — same default
        // the rest of the flow ops use.
        assertThat(state.getCurrentNodeId()).isEqualTo(NORMAL_ID);
    }

    @Test
    @DisplayName("failure edge points at missing node → falls back to first edge")
    void failureTargetMissing_fallsBackToFirstEdge() {
        ChatFlowNode source = functionCallNode(true);
        ChatFlowGraph graph = new ChatFlowGraph(
                List.of(source, endNode(NORMAL_ID)),
                List.of(
                        edge("e-normal", NODE_ID, NORMAL_ID, null),
                        edge("e-failure", NODE_ID, "ghost-target", ChatFlowOps.FAILURE_HANDLE)));
        TurChatFlowState state = stateAt(NODE_ID);

        ChatFlowOps.advanceOnFailure(state, graph, source);

        assertThat(state.getCurrentNodeId()).isEqualTo(NORMAL_ID);
    }

    @Test
    @DisplayName("no outgoing edges at all → cursor stays put")
    void noOutgoingEdges_staysPut() {
        ChatFlowNode source = functionCallNode(true);
        ChatFlowGraph graph = new ChatFlowGraph(List.of(source), List.of());
        TurChatFlowState state = stateAt(NODE_ID);

        ChatFlowOps.advanceOnFailure(state, graph, source);

        assertThat(state.getCurrentNodeId()).isEqualTo(NODE_ID);
    }

    @Test
    @DisplayName("continueOnFailure() returns null by default (legacy nodes)")
    void continueOnFailure_defaultsToNull() {
        ChatFlowNode legacy = functionCallNode(null);
        assertThat(legacy.continueOnFailure()).isNull();
    }

    @Test
    @DisplayName("continueOnFailure() round-trips Boolean.TRUE and Boolean.FALSE")
    void continueOnFailure_roundTripsBoolean() {
        assertThat(functionCallNode(true).continueOnFailure()).isTrue();
        assertThat(functionCallNode(false).continueOnFailure()).isFalse();
    }

    @Test
    @DisplayName("ChatFlowNode back-compat constructor (24 args) leaves continueOnFailure null")
    void backCompatConstructor_leavesContinueOnFailureNull() {
        // Exercises the 24-arg NodeData constructor still in use by tests
        // and JSON deserialization paths predating T49.
        ChatFlowNode legacy = new ChatFlowNode("n1", "functionCall", new NodeData(
                "label", "functionCall", null, null, null, "tool_x", null, "NATIVE", null, null,
                null, null, null, List.of(), List.of(), null, null, null, null, null, null, List.of(),
                null, null));
        assertThat(legacy.continueOnFailure()).isNull();
    }

    @Test
    @DisplayName("FAILURE_HANDLE constant is the literal 'failure'")
    void failureHandleConstant_isFailureLiteral() {
        // Pins the wire contract — JSON edges export sourceHandle="failure".
        // T50 (red edge) reads the same constant when picking edge style.
        assertThat(ChatFlowOps.FAILURE_HANDLE).isEqualTo("failure");
    }

    // ─────────────────────── helpers ───────────────────────

    private static ChatFlowNode functionCallNode(Boolean continueOnFailure) {
        return new ChatFlowNode(NODE_ID, "functionCall", new NodeData(
                "Function call", "functionCall",
                "{}", "out", null,
                "tool_x", null, "NATIVE", null, null, null, null, null,
                List.of(), List.of(),
                null, null, null, null, null, null, List.of(),
                null, null, continueOnFailure));
    }

    private static ChatFlowNode endNode(String id) {
        return new ChatFlowNode(id, "end", new NodeData(
                "End", "end", null, null, null, null, null, null, null, null, null,
                null, null, List.of(), List.of(), null, null, null, null, null, null, List.of(),
                null, null));
    }

    private static ChatFlowEdge edge(String id, String source, String target, String handle) {
        return new ChatFlowEdge(id, source, target, handle, null, null);
    }

    private static TurChatFlowState stateAt(String nodeId) {
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId("conv-test");
        state.setCurrentNodeId(nodeId);
        return state;
    }
}

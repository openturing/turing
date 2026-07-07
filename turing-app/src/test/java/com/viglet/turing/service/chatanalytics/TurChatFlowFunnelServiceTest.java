/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatanalytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.flow.ChatFlowEdge;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowSubmission;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowSubmissionRepository;

/**
 * Pins the T85 funnel service: per-node aggregation of in-flight cursors
 * plus submission terminal counts, with transparent nodes (start/condition/
 * switch/...) excluded so the funnel reads as user-facing steps only.
 */
@ExtendWith(MockitoExtension.class)
class TurChatFlowFunnelServiceTest {

    @Mock
    private TurChatFlowRepository chatFlowRepository;

    @Mock
    private TurChatFlowStateRepository stateRepository;

    @Mock
    private TurChatFlowSubmissionRepository submissionRepository;

    @Mock
    private TurChatFlowEngineService chatFlowEngineService;

    @InjectMocks
    private TurChatFlowFunnelService service;

    private static ChatFlowNode node(String id, String type, String label) {
        ChatFlowNode.NodeData data = new ChatFlowNode.NodeData(label, type, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        return new ChatFlowNode(id, type, data);
    }

    @Test
    void emptyFlowIdReturnsEmpty() {
        assertThat(service.computeFunnel(null)).isEmpty();
        assertThat(service.computeFunnel("")).isEmpty();
        assertThat(service.computeFunnel("  ")).isEmpty();
    }

    @Test
    void missingFlowReturnsEmpty() {
        when(chatFlowRepository.findById("nope")).thenReturn(Optional.empty());
        assertThat(service.computeFunnel("nope")).isEmpty();
    }

    @Test
    void unparseableGraphReturnsEmpty() {
        TurChatFlow flow = new TurChatFlow();
        flow.setId("f1");
        when(chatFlowRepository.findById("f1")).thenReturn(Optional.of(flow));
        when(chatFlowEngineService.parseGraph(flow)).thenReturn(Optional.empty());
        assertThat(service.computeFunnel("f1")).isEmpty();
    }

    @Test
    void funnelExcludesTransparentNodes() {
        TurChatFlow flow = new TurChatFlow();
        flow.setId("f1");
        flow.setName("Lead capture");
        ChatFlowGraph graph = new ChatFlowGraph(
                List.of(
                        node("start", "start", "Start"),
                        node("ai-name", "aiQuestion", "Name"),
                        node("cond", "condition", "Check"),
                        node("ai-cargo", "aiQuestion", "Role"),
                        node("end", "end", "End")),
                List.<ChatFlowEdge>of());
        when(chatFlowRepository.findById("f1")).thenReturn(Optional.of(flow));
        when(chatFlowEngineService.parseGraph(flow)).thenReturn(Optional.of(graph));
        when(stateRepository.countByCurrentNodeForFlow("f1")).thenReturn(List.of(
                new Object[] { "ai-name", 5L },
                new Object[] { "ai-cargo", 3L }));
        when(submissionRepository.countByEndNodeForFlow("f1")).thenReturn(List.of(
                new Object[] { "end", 12L },
                new Object[] { "__abandoned__", 7L }));

        TurChatFlowFunnelService.FunnelReport report = service.computeFunnel("f1").orElseThrow();
        assertThat(report.flowId()).isEqualTo("f1");
        assertThat(report.flowName()).isEqualTo("Lead capture");
        assertThat(report.abandonedAtFlow()).isEqualTo(7L);
        assertThat(report.totalStates()).isEqualTo(8);
        assertThat(report.totalSubmissions()).isEqualTo(19);
        // start/condition skipped; end kept as terminal anchor for the
        // funnel — only "transparent" intermediate nodes drop out.
        assertThat(report.nodes()).extracting("nodeId")
                .containsExactly("ai-name", "ai-cargo", "end");
        assertThat(report.nodes().get(0).cursorCount()).isEqualTo(5L);
        assertThat(report.nodes().get(0).completedCount()).isZero();
        assertThat(report.nodes().get(1).cursorCount()).isEqualTo(3L);
        assertThat(report.nodes().get(2).completedCount()).isEqualTo(12L);
    }

    @Test
    void funnelHandlesEmptyStatsTables() {
        TurChatFlow flow = new TurChatFlow();
        flow.setId("f1");
        flow.setName("New flow");
        ChatFlowGraph graph = new ChatFlowGraph(
                List.of(node("ai-name", "aiQuestion", "Name")),
                List.<ChatFlowEdge>of());
        when(chatFlowRepository.findById("f1")).thenReturn(Optional.of(flow));
        when(chatFlowEngineService.parseGraph(flow)).thenReturn(Optional.of(graph));
        when(stateRepository.countByCurrentNodeForFlow("f1")).thenReturn(List.of());
        when(submissionRepository.countByEndNodeForFlow("f1")).thenReturn(List.of());

        TurChatFlowFunnelService.FunnelReport report = service.computeFunnel("f1").orElseThrow();
        assertThat(report.totalStates()).isZero();
        assertThat(report.totalSubmissions()).isZero();
        assertThat(report.abandonedAtFlow()).isZero();
        assertThat(report.nodes()).hasSize(1);
        assertThat(report.nodes().get(0).cursorCount()).isZero();
        assertThat(report.nodes().get(0).completedCount()).isZero();
    }

    private static TurChatFlowSubmission submissionWithPath(String pathJson) {
        TurChatFlowSubmission s = new TurChatFlowSubmission();
        s.setNodeVisitPath(pathJson);
        return s;
    }

    @Test
    void pathAwareFunnelCountsReachedAndContinued() {
        // T237 — three conversations with recorded node-visit paths:
        //   A → ai-name, ai-cargo, end   (completed)
        //   B → ai-name, ai-cargo        (dropped at the role step)
        //   C → ai-name                  (dropped at the name step)
        TurChatFlow flow = new TurChatFlow();
        flow.setId("f1");
        flow.setName("Lead capture");
        ChatFlowGraph graph = new ChatFlowGraph(
                List.of(
                        node("start", "start", "Start"),
                        node("ai-name", "aiQuestion", "Name"),
                        node("ai-cargo", "aiQuestion", "Role"),
                        node("end", "end", "End")),
                List.<ChatFlowEdge>of());
        when(chatFlowRepository.findById("f1")).thenReturn(Optional.of(flow));
        when(chatFlowEngineService.parseGraph(flow)).thenReturn(Optional.of(graph));
        when(stateRepository.countByCurrentNodeForFlow("f1")).thenReturn(List.of());
        when(submissionRepository.countByEndNodeForFlow("f1")).thenReturn(List.of());
        when(submissionRepository.findByFlow_IdOrderByCompletedAtDesc("f1")).thenReturn(List.of(
                submissionWithPath("[\"ai-name\",\"ai-cargo\",\"end\"]"),
                submissionWithPath("[\"ai-name\",\"ai-cargo\"]"),
                submissionWithPath("[\"ai-name\"]")));

        TurChatFlowFunnelService.FunnelReport report = service.computeFunnel("f1").orElseThrow();
        assertThat(report.totalPaths()).isEqualTo(3);

        var byId = report.nodes().stream()
                .collect(java.util.stream.Collectors.toMap(
                        TurChatFlowFunnelService.FunnelNode::nodeId, n -> n));
        // ai-name: all 3 reached; A+B progressed past it; C dropped.
        assertThat(byId.get("ai-name").pathReached()).isEqualTo(3);
        assertThat(byId.get("ai-name").pathContinued()).isEqualTo(2);
        assertThat(byId.get("ai-name").pathDropOff()).isEqualTo(1);
        // ai-cargo: A+B reached; only A progressed; B dropped.
        assertThat(byId.get("ai-cargo").pathReached()).isEqualTo(2);
        assertThat(byId.get("ai-cargo").pathContinued()).isEqualTo(1);
        assertThat(byId.get("ai-cargo").pathDropOff()).isEqualTo(1);
        // end: only A reached; nothing later to continue to.
        assertThat(byId.get("end").pathReached()).isEqualTo(1);
        assertThat(byId.get("end").pathContinued()).isZero();
        assertThat(byId.get("end").pathDropOff()).isEqualTo(1);
    }

    @Test
    void pathStatsZeroWhenNoPathsRecorded() {
        // Node-visit log disabled → submissions carry no path → totalPaths 0
        // and the path-aware counters stay zero (falls back to V1 view).
        TurChatFlow flow = new TurChatFlow();
        flow.setId("f1");
        flow.setName("No paths");
        ChatFlowGraph graph = new ChatFlowGraph(
                List.of(node("ai-name", "aiQuestion", "Name")),
                List.<ChatFlowEdge>of());
        when(chatFlowRepository.findById("f1")).thenReturn(Optional.of(flow));
        when(chatFlowEngineService.parseGraph(flow)).thenReturn(Optional.of(graph));
        when(stateRepository.countByCurrentNodeForFlow("f1")).thenReturn(List.of());
        when(submissionRepository.countByEndNodeForFlow("f1")).thenReturn(List.of());
        when(submissionRepository.findByFlow_IdOrderByCompletedAtDesc("f1")).thenReturn(List.of(
                submissionWithPath(null),
                submissionWithPath("")));

        TurChatFlowFunnelService.FunnelReport report = service.computeFunnel("f1").orElseThrow();
        assertThat(report.totalPaths()).isZero();
        assertThat(report.nodes().get(0).pathReached()).isZero();
        assertThat(report.nodes().get(0).pathContinued()).isZero();
    }

    @Test
    void funnelTolerantToMalformedCountRows() {
        TurChatFlow flow = new TurChatFlow();
        flow.setId("f1");
        flow.setName("Robust");
        ChatFlowGraph graph = new ChatFlowGraph(
                List.of(node("ai-name", "aiQuestion", "Name")),
                List.<ChatFlowEdge>of());
        when(chatFlowRepository.findById("f1")).thenReturn(Optional.of(flow));
        when(chatFlowEngineService.parseGraph(flow)).thenReturn(Optional.of(graph));
        when(stateRepository.countByCurrentNodeForFlow("f1")).thenReturn(List.of(
                new Object[] { null, 5L },           // null id → skipped
                new Object[] { "ai-name" },          // short row → skipped
                new Object[] { "ai-name", "x" },     // non-numeric → skipped
                new Object[] { "ai-name", 2L }));    // valid → counted
        when(submissionRepository.countByEndNodeForFlow("f1")).thenReturn(List.of());

        TurChatFlowFunnelService.FunnelReport report = service.computeFunnel("f1").orElseThrow();
        assertThat(report.nodes().get(0).cursorCount()).isEqualTo(2L);
    }
}

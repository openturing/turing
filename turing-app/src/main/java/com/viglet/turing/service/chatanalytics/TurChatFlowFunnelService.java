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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowSubmissionRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T85 / §VII.10.b — funnel statistics per chat-flow node. Aggregates two
 * sources to give operators a turn-by-turn drop-off view of a flow:
 *
 * <ul>
 *   <li><b>In-flight cursor counts</b> from {@code chat_flow_state} —
 *       how many active conversations are currently parked at each
 *       node (proxy for "the audience that hasn't progressed yet").</li>
 *   <li><b>Submission terminal counts</b> from {@code chat_flow_submission}
 *       — how many conversations ENDED at each node id. End nodes plus
 *       the synthetic {@code __abandoned__} marker for incomplete runs.
 *       </li>
 * </ul>
 *
 * <p>Note on fidelity: without a per-turn node visit log (would require
 * a new {@code chat_node_visit} table) we cannot report "how many
 * conversations passed through node X". We expose what we can derive
 * from existing state — the operator gets "parked + ended at each node"
 * which is enough to spot the obvious "60% drop between cargo and
 * objetivo" pattern by reading the bars in graph order. A per-turn log
 * is tracked separately (T85 follow-up) for a full path-aware funnel.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChatFlowFunnelService {

    /** Synthetic end-node marker the engine emits for abandoned runs. */
    private static final String ABANDONED_MARKER = "__abandoned__";

    private final TurChatFlowRepository chatFlowRepository;
    private final TurChatFlowStateRepository stateRepository;
    private final TurChatFlowSubmissionRepository submissionRepository;
    private final TurChatFlowEngineService chatFlowEngineService;

    public TurChatFlowFunnelService(TurChatFlowRepository chatFlowRepository,
            TurChatFlowStateRepository stateRepository,
            TurChatFlowSubmissionRepository submissionRepository,
            TurChatFlowEngineService chatFlowEngineService) {
        this.chatFlowRepository = chatFlowRepository;
        this.stateRepository = stateRepository;
        this.submissionRepository = submissionRepository;
        this.chatFlowEngineService = chatFlowEngineService;
    }

    /**
     * Per-node funnel row. {@code cursorCount} is the number of in-flight
     * states currently parked here; {@code completedCount} is the number
     * of submissions that ended at this node. Both can be zero.
     */
    public record FunnelNode(
            String nodeId,
            String label,
            String type,
            long cursorCount,
            long completedCount,
            long abandonedCount) {
    }

    /**
     * Funnel response: ordered list of interactive nodes for the flow,
     * each with its drop-off counters. The list is in graph order
     * (start → end) so the UI can render the funnel as a vertical
     * stack and the operator's eyes spot the cliff visually.
     */
    public record FunnelReport(
            String flowId,
            String flowName,
            int totalStates,
            int totalSubmissions,
            long abandonedAtFlow,
            List<FunnelNode> nodes) {
    }

    /**
     * Computes the funnel for {@code flowId}. Returns {@link Optional#empty}
     * when the flow id is unknown or its graph is unparseable — the API
     * surface translates that to a 404.
     */
    public Optional<FunnelReport> computeFunnel(String flowId) {
        if (flowId == null || flowId.isBlank()) return Optional.empty();
        TurChatFlow flow = chatFlowRepository.findById(flowId).orElse(null);
        if (flow == null) return Optional.empty();
        ChatFlowGraph graph = chatFlowEngineService.parseGraph(flow).orElse(null);
        if (graph == null) return Optional.empty();

        Map<String, Long> cursorByNode = toCountMap(
                stateRepository.countByCurrentNodeForFlow(flowId));
        Map<String, Long> completedByNode = toCountMap(
                submissionRepository.countByEndNodeForFlow(flowId));

        long abandonedAtFlow = completedByNode.getOrDefault(ABANDONED_MARKER, 0L);
        int totalStates = (int) cursorByNode.values().stream().mapToLong(Long::longValue).sum();
        int totalSubmissions = (int) completedByNode.values().stream().mapToLong(Long::longValue).sum();

        List<FunnelNode> nodes = new ArrayList<>();
        // Walk the graph nodes in their declared order so the funnel reads
        // top-to-bottom in author intent. Skip pure-transparent nodes
        // (start / condition / switch / persona) — they're invisible to
        // the visitor; the funnel is about the user-facing steps.
        for (ChatFlowNode node : graph.nodes()) {
            String type = node.type();
            if (type == null) continue;
            if (isTransparent(type)) continue;
            long cursor = cursorByNode.getOrDefault(node.id(), 0L);
            long completed = completedByNode.getOrDefault(node.id(), 0L);
            // Heuristic: states whose cursor has been parked at this node
            // for longer than the cleanup window count as abandoned. We
            // don't have that lookup here without scanning rows, so for
            // V1 we report "cursorCount" as the upper bound on abandoned
            // at THIS node — the UI labels it as "currently here" and
            // separately reports the synthetic __abandoned__ total under
            // the flow header.
            nodes.add(new FunnelNode(node.id(), node.label(), type,
                    cursor, completed, 0L));
        }

        return Optional.of(new FunnelReport(flow.getId(), flow.getName(),
                totalStates, totalSubmissions, abandonedAtFlow, nodes));
    }

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> out = new HashMap<>();
        if (rows == null) return out;
        for (Object[] row : rows) {
            if (row == null || row.length < 2) continue;
            if (row[0] == null) continue;
            Object countObj = row[1];
            if (!(countObj instanceof Number n)) continue;
            out.put(row[0].toString(), n.longValue());
        }
        return out;
    }

    private static boolean isTransparent(String type) {
        return switch (type) {
            case "start", "condition", "switch", "persona", "subFlow",
                    "subFlowSwitch", "writeSlot", "functionCall", "scheduleAgent" -> true;
            default -> false;
        };
    }
}

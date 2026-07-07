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
import com.viglet.turing.genai.flow.TurChatFlowNodeVisitPath;
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
 * <p>T237 — when the opt-in per-turn node-visit log is enabled
 * ({@code turing.chat.analytics.node-visit-log.enabled}), terminal
 * submissions carry the ordered node-visit path, so the funnel adds true
 * path-aware {@code pathReached} / {@code pathContinued} (→ drop-off) counts
 * per node — "how many conversations passed through node X and how many
 * progressed past it". When the log is off (the default), those counters are
 * zero and the funnel falls back to the V1 view derived from existing state:
 * in-flight cursor counts ({@code chat_flow_state}) plus terminal counts
 * ({@code chat_flow_submission}), enough to spot the obvious "60% drop between
 * cargo and objetivo" by reading the bars in graph order.
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
            long abandonedCount,
            // T237 — path-aware counters from the per-turn node-visit log.
            // pathReached: recorded conversation paths that visited this node.
            // pathContinued: of those, how many also reached a LATER funnel
            // node (i.e. progressed past this step). Both 0 when the
            // node-visit log is disabled / no paths recorded yet.
            long pathReached,
            long pathContinued) {

        /** Conversations that reached this node but never a later one (T237). */
        public long pathDropOff() {
            return Math.max(0, pathReached - pathContinued);
        }
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
            // T237 — number of terminal submissions that carried a recorded
            // node-visit path (0 when the opt-in log is disabled). When > 0 the
            // UI shows the path-aware reached / drop-off columns.
            int totalPaths,
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

        // The interactive funnel nodes in graph (author) order.
        List<ChatFlowNode> funnelNodes = new ArrayList<>();
        for (ChatFlowNode node : graph.nodes()) {
            String type = node.type();
            if (type != null && !isTransparent(type)) {
                funnelNodes.add(node);
            }
        }
        List<String> orderedNodeIds = funnelNodes.stream().map(ChatFlowNode::id).toList();

        // T237 — path-aware stats from the per-turn node-visit log carried on
        // terminal submissions. Empty (all-zero) when the opt-in log is off.
        PathStats pathStats = computePathStats(flowId, orderedNodeIds);

        List<FunnelNode> nodes = new ArrayList<>();
        for (ChatFlowNode node : funnelNodes) {
            long cursor = cursorByNode.getOrDefault(node.id(), 0L);
            long completed = completedByNode.getOrDefault(node.id(), 0L);
            // Heuristic: states whose cursor has been parked at this node
            // for longer than the cleanup window count as abandoned. We
            // don't have that lookup here without scanning rows, so for
            // V1 we report "cursorCount" as the upper bound on abandoned
            // at THIS node — the UI labels it as "currently here" and
            // separately reports the synthetic __abandoned__ total under
            // the flow header.
            nodes.add(new FunnelNode(node.id(), node.label(), type(node),
                    cursor, completed, 0L,
                    pathStats.reached().getOrDefault(node.id(), 0L),
                    pathStats.continued().getOrDefault(node.id(), 0L)));
        }

        return Optional.of(new FunnelReport(flow.getId(), flow.getName(),
                totalStates, totalSubmissions, abandonedAtFlow, pathStats.totalPaths(), nodes));
    }

    private static String type(ChatFlowNode node) {
        return node.type();
    }

    /** Per-node path-aware counters plus the number of paths considered (T237). */
    private record PathStats(Map<String, Long> reached, Map<String, Long> continued, int totalPaths) {
    }

    /**
     * Aggregates the per-turn node-visit paths snapshotted onto terminal
     * submissions into per-node {@code reached} / {@code continued} counts.
     * {@code reached(n)} = paths that visited node {@code n}; {@code continued(n)}
     * = of those, how many also reached a funnel node positioned LATER in graph
     * order (i.e. progressed past {@code n}). Branch-tolerant: it asks "did the
     * path reach any later step?" rather than assuming strict linear order.
     *
     * <p>Returns all-zero stats (and {@code totalPaths == 0}) when no submission
     * carries a path — the common case while the opt-in log is disabled — so
     * the funnel quietly falls back to the V1 cursor/completed view.
     */
    private PathStats computePathStats(String flowId, List<String> orderedNodeIds) {
        Map<String, Long> reached = new HashMap<>();
        Map<String, Long> continued = new HashMap<>();
        if (orderedNodeIds.isEmpty()) {
            return new PathStats(reached, continued, 0);
        }
        Map<String, Integer> orderIndex = new HashMap<>();
        for (int i = 0; i < orderedNodeIds.size(); i++) {
            orderIndex.put(orderedNodeIds.get(i), i);
        }

        int totalPaths = 0;
        var submissions = submissionRepository.findByFlow_IdOrderByCompletedAtDesc(flowId);
        for (var submission : submissions) {
            List<String> path = TurChatFlowNodeVisitPath.parse(submission.getNodeVisitPath());
            if (path.isEmpty()) {
                continue;
            }
            totalPaths++;
            accumulatePath(path, orderedNodeIds, orderIndex, reached, continued);
        }
        return new PathStats(reached, continued, totalPaths);
    }

    /**
     * Folds one conversation path into the {@code reached}/{@code continued}
     * accumulators (T237). Counts each funnel node the path visited as reached,
     * and as continued when the path also reached a later funnel node.
     */
    private static void accumulatePath(List<String> path, List<String> orderedNodeIds,
            Map<String, Integer> orderIndex, Map<String, Long> reached, Map<String, Long> continued) {
        // The furthest funnel-order index this path reached.
        int maxIndex = -1;
        for (String visited : path) {
            Integer idx = orderIndex.get(visited);
            if (idx != null && idx > maxIndex) {
                maxIndex = idx;
            }
        }
        // Count only funnel nodes the path actually visited, so a branch that
        // skipped a node doesn't inflate it.
        java.util.Set<String> visitedFunnel = new java.util.HashSet<>(path);
        for (String nodeId : orderedNodeIds) {
            if (!visitedFunnel.contains(nodeId)) {
                continue;
            }
            reached.merge(nodeId, 1L, Long::sum);
            if (orderIndex.get(nodeId) < maxIndex) {
                continued.merge(nodeId, 1L, Long::sum);
            }
        }
    }

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> out = new HashMap<>();
        if (rows == null) return out;
        for (Object[] row : rows) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] instanceof Number n) {
                out.put(row[0].toString(), n.longValue());
            }
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

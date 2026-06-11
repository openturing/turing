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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.dto.agent.TurParkedConversationDto;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T122 / §IX.6.b — read model + unblock action behind the parked-conversations
 * dashboard. Lists every conversation currently suspended on a {@code suspend}
 * node (T121) with its reason and waiting time, and exposes an admin "unblock"
 * that delegates to {@link TurChatFlowEngineService#resumeSuspendedFlow} — the
 * same primitive a webhook callback would call, minus any slot payload.
 *
 * <p>The scan resolves each flow's {@code suspend} node ids by parsing the
 * flow graph <em>once</em> (the flow catalog is small and {@code @Cacheable}),
 * then loads only the matching runtime states via
 * {@link TurChatFlowStateRepository#findByFlow_IdAndCurrentNodeIdIn} — so cost
 * scales with the number of parked states, not the whole state table.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurParkedConversationService {

    private final TurChatFlowRepository flowRepository;
    private final TurChatFlowStateRepository stateRepository;
    private final TurChatFlowEngineService engineService;
    private final TurAIAgentRepository agentRepository;

    public TurParkedConversationService(TurChatFlowRepository flowRepository,
            TurChatFlowStateRepository stateRepository,
            TurChatFlowEngineService engineService,
            TurAIAgentRepository agentRepository) {
        this.flowRepository = flowRepository;
        this.stateRepository = stateRepository;
        this.engineService = engineService;
        this.agentRepository = agentRepository;
    }

    /**
     * Every conversation parked on a {@code suspend} node across all flows,
     * sorted by waiting time descending (longest-waiting first) so the
     * operator triages the most stale conversations at the top.
     */
    @Transactional(readOnly = true)
    public List<TurParkedConversationDto> listParked() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> agentTitleCache = new HashMap<>();
        List<TurParkedConversationDto> out = new ArrayList<>();

        for (TurChatFlow flow : flowRepository.findAll()) {
            ChatFlowGraph graph = engineService.parseGraph(flow).orElse(null);
            if (graph == null) {
                continue;
            }
            // Reason text keyed by suspend node id, so the loaded states map
            // straight to their authored label without re-walking the graph.
            Map<String, String> suspendReasons = new HashMap<>();
            for (ChatFlowNode node : graph.nodes()) {
                if ("suspend".equals(node.type())) {
                    String label = node.label();
                    suspendReasons.put(node.id(),
                            label == null || label.isBlank() ? "suspended" : label);
                }
            }
            if (suspendReasons.isEmpty()) {
                continue;
            }
            List<TurChatFlowState> parked = stateRepository.findByFlow_IdAndCurrentNodeIdIn(
                    flow.getId(), suspendReasons.keySet());
            if (parked.isEmpty()) {
                continue;
            }
            String agentId = flowRepository.findAgentIdByFlowId(flow.getId()).orElse(null);
            String agentTitle = resolveAgentTitle(agentId, agentTitleCache);
            for (TurChatFlowState state : parked) {
                LocalDateTime since = state.getUpdatedAt();
                long waitingSeconds = since == null ? 0L
                        : Math.max(0L, Duration.between(since, now).getSeconds());
                out.add(new TurParkedConversationDto(
                        state.getConversationId(),
                        agentId,
                        agentTitle,
                        flow.getId(),
                        flow.getName(),
                        state.getCurrentNodeId(),
                        suspendReasons.getOrDefault(state.getCurrentNodeId(), "suspended"),
                        since == null ? null : since.toString(),
                        waitingSeconds));
            }
        }
        out.sort(Comparator.comparingLong(TurParkedConversationDto::waitingSeconds).reversed());
        return out;
    }

    /** Resolves (and memoizes) the agent title for an id; {@code null}-safe. */
    private String resolveAgentTitle(String agentId, Map<String, String> cache) {
        if (agentId == null) {
            return null;
        }
        return cache.computeIfAbsent(agentId,
                id -> agentRepository.findById(id).map(a -> a.getTitle()).orElse(null));
    }

    /**
     * Admin "unblock" — walks every state of {@code conversationId} parked on a
     * {@code suspend} node past it (no slot payload). Idempotent: a conversation
     * with nothing parked returns {@code resumed=0, wasParked=false}.
     */
    public TurChatFlowEngineService.ResumeResult unblock(String conversationId) {
        log.info("[ParkedConversations] admin unblock conv='{}'", conversationId);
        return engineService.resumeSuspendedFlow(conversationId, null, "admin-unblock");
    }

    /** Convenience for the API layer's resume response. */
    public Optional<TurChatFlowEngineService.ResumeResult> unblockIfPresent(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(unblock(conversationId));
    }
}

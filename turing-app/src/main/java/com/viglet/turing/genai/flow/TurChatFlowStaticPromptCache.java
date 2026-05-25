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

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

/**
 * T31 / §IV.5 — caches the deterministic portions of the system-prompt
 * addendum that {@link com.viglet.turing.genai.flow.strategy.TurChatFlowGuardrailStrategy
 * guardrail strategies} build for the active flow node. The addendum has
 * three concatenated pieces (preserving the existing prompt order):
 *
 * <ol>
 *   <li><b>Head</b> — {@code === ACTIVE FLOW STEP: ... ===}, {@code Goal},
 *       {@code Collect}, {@code Validate}. Derived from
 *       {@link com.viglet.turing.genai.flow.ChatFlowNode node fields}
 *       only → cacheable by {@code (flowId, nodeId)}.</li>
 *   <li><b>Variables line</b> — {@code Already collected: {...}}. Derived
 *       from the per-conversation
 *       {@link TurChatFlowState#variablesJson variablesJson}, which
 *       mutates as slots are filled. <b>Not cacheable.</b></li>
 *   <li><b>Tail</b> — the next-step preview hint (depends on the graph
 *       edges) and the conversational {@code HARD_RULES} constant.
 *       Cacheable by {@code (flowId, nodeId, includeNextStepHint)} —
 *       the {@code includeNextStepHint=false} variant is used by the
 *       regen path so it gets its own cache entry.</li>
 * </ol>
 *
 * <h2>Why two cache entries instead of one</h2>
 *
 * The variables line sits BETWEEN the head and the tail. Caching the
 * whole addendum keyed by {@code (flowId, nodeId, hash(variablesJson))}
 * would be correct but the hit rate would collapse to ~0 in practice —
 * every turn fills a slot or two, so the variables hash drifts. Splitting
 * around the dynamic line lets us keep ~100% cache hits on the static
 * portions (which is where the StringBuilder cost lives — Goal /
 * Validate / HARD_RULES collectively are ~600-2000 bytes; the variables
 * line is one short line).
 *
 * <h2>Eviction</h2>
 *
 * Both cache names are listed in the {@code @CacheEvict} block on
 * {@link com.viglet.turing.persistence.repository.agent.TurChatFlowRepository#save(Object)}
 * and {@link com.viglet.turing.persistence.repository.agent.TurChatFlowRepository#delete(String)
 * delete(id)} so any operator edit to the flow (label, aiInstruction,
 * outputVariable, validationRule, edges, neighbor instructions)
 * propagates on the next chat turn — same convention used for
 * {@code turChatFlowRouterDecision}.
 *
 * <h2>Self-injection for proxy routing</h2>
 *
 * {@link #buildAddendum(TurChatFlow, com.viglet.turing.genai.flow.ChatFlowNode, TurChatFlowState, ChatFlowGraph, boolean)}
 * is the orchestrator the strategies call. It in turn invokes
 * {@link #staticHead(String, String, com.viglet.turing.genai.flow.ChatFlowNode)
 * staticHead(...)} and
 * {@link #staticTail(String, String, boolean, com.viglet.turing.genai.flow.ChatFlowNode, ChatFlowGraph)
 * staticTail(...)}, both of which are {@code @Cacheable}. Calling them via
 * {@code this.method(...)} would bypass the Spring proxy and silently
 * defeat the cache, so the bean self-injects a {@code @Lazy} reference and
 * routes those calls through it — the standard Spring pattern for
 * intra-class proxy-routed invocations.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurChatFlowStaticPromptCache {

    /** Cache holding the static head per {@code (flowId, nodeId)}. */
    public static final String HEAD_CACHE = "turChatFlowStaticAddendumHead";
    /** Cache holding the static tail per {@code (flowId, nodeId, includeNextStepHint)}. */
    public static final String TAIL_CACHE = "turChatFlowStaticAddendumTail";

    private final TurChatFlowStaticPromptCache self;

    /**
     * The {@link Lazy @Lazy} self-reference is resolved by Spring to a
     * proxy of this bean — the only way to route intra-class calls
     * through Spring's caching advice without bypassing
     * {@code @Cacheable}. Spring 4.3+ auto-wires single-constructor
     * components without {@code @Autowired}.
     */
    public TurChatFlowStaticPromptCache(@Lazy TurChatFlowStaticPromptCache self) {
        this.self = self;
    }

    /**
     * Orchestrates the three concatenated pieces. Returns an empty string
     * when there is no active node or the active node is terminal — the
     * guardrail strategy then knows to skip the addendum entirely (chat
     * runs free once the flow ended).
     *
     * @param flow                 the active flow; the id is the cache key
     *                             prefix. {@code null}-safe: the orchestrator
     *                             degenerates to the uncached path.
     * @param node                 the active node — supplies the content
     *                             behind the {@code (flowId, nodeId)} cache
     *                             key.
     * @param state                the conversation state — read for the
     *                             dynamic "Already collected" line.
     * @param graph                the parsed flow graph — read by the tail
     *                             to compute the next-step preview hint.
     * @param includeNextStepHint  whether to include the next-step preview.
     *                             {@code false} on the regen path (see
     *                             {@link ChatFlowOps#buildBaseAddendum} doc).
     */
    public String buildAddendum(TurChatFlow flow,
            com.viglet.turing.genai.flow.ChatFlowNode node,
            TurChatFlowState state,
            ChatFlowGraph graph,
            boolean includeNextStepHint) {
        if (node == null || "end".equals(node.type())) {
            return "";
        }
        String flowId = flow == null || flow.getId() == null ? "" : flow.getId();
        // Routing through the self-proxy ensures Spring's @Cacheable
        // around-advice fires; a direct `this.staticHead(...)` would skip
        // the proxy and the cache wouldn't be consulted.
        String head = self.staticHead(flowId, node.id(), node);
        String vars = ChatFlowOps.buildVariablesLine(state);
        String tail = self.staticTail(flowId, node.id(), includeNextStepHint, node, graph);
        return head + vars + tail;
    }

    /**
     * Cached head of the addendum. {@code node} is the content source; it
     * is intentionally excluded from the cache key — for a given
     * {@code (flowId, nodeId)} the node content is stable until the flow
     * is edited (which evicts the cache). {@code unless} avoids storing
     * the empty-string result so a degenerate input doesn't pin a cache
     * slot.
     */
    @Cacheable(value = HEAD_CACHE,
            key = "#flowId + ':' + #nodeId",
            unless = "#result == null || #result.isEmpty()")
    public String staticHead(String flowId, String nodeId,
            com.viglet.turing.genai.flow.ChatFlowNode node) {
        return ChatFlowOps.buildStaticAddendumHead(node);
    }

    /**
     * Cached tail of the addendum. {@code includeNextStepHint} is part of
     * the key so the two variants ({@code true} for the standard initial
     * call, {@code false} for the regen path) get separate entries.
     */
    @Cacheable(value = TAIL_CACHE,
            key = "#flowId + ':' + #nodeId + ':' + #includeNextStepHint",
            unless = "#result == null || #result.isEmpty()")
    public String staticTail(String flowId, String nodeId, boolean includeNextStepHint,
            com.viglet.turing.genai.flow.ChatFlowNode node,
            ChatFlowGraph graph) {
        return ChatFlowOps.buildStaticAddendumTail(node, graph, includeNextStepHint);
    }
}

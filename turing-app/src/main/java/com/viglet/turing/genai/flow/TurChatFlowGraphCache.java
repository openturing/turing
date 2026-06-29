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

import java.util.function.Supplier;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * T487 / §XXVIII.2 — dedicated read-model cache for the parsed
 * {@link ChatFlowGraph} on the chat hot path.
 *
 * <p>{@code TurChatFlow.definitionJson} is the React-Flow editor payload; the
 * engine deserializes it into an <em>immutable</em> {@link ChatFlowGraph} record
 * tree on every read. A single chat turn walks the graph many times
 * ({@code parseGraph(...)} is called ~dozens of times across the trigger router,
 * the transparent-node walk, sub-flow descent and the prompt builder), and each
 * call re-parses the identical JSON. Memoizing the parsed graph by flow id
 * collapses that to one parse per flow until the flow is edited.
 *
 * <p><b>Why this is Block AC-safe.</b> The cached value is the parsed
 * {@link ChatFlowGraph} — an immutable record tree with <em>no</em> JPA lazy
 * proxies and no session affinity — never the {@link
 * com.viglet.turing.persistence.model.agent.TurChatFlow} entity. So a hit is
 * safe to read anywhere, any time, outside a transaction; this is precisely the
 * property the old repository-level entity cache never had (T486). The graph
 * record tree is {@link java.io.Serializable} so it round-trips through a
 * clustered Hazelcast {@code IMap} as well as the in-process map.
 *
 * <p><b>Why a separate bean.</b> Spring's {@code @Cacheable} is proxy-based, so
 * {@link TurChatFlowEngineService#parseGraph(com.viglet.turing.persistence.model.agent.TurChatFlow)}
 * cannot memoize via a {@code this} call (it would no-op). Routing the cacheable
 * call through this injected bean keeps the Spring proxy in the path — the
 * canonical pattern from {@code TurChatFlowStaticPromptCache} and
 * {@code TurRagRerankCache}.
 *
 * <p><b>Eviction.</b> The {@code turChatFlowGraph} cache name is cleared by
 * {@link TurChatFlowEngineService#evictFlowDerivedCaches()}, invoked from the
 * {@code TurChatFlowRouterEvictionListener} JPA callback on any {@link
 * com.viglet.turing.persistence.model.agent.TurChatFlow} persist/update/remove
 * and at the one bulk-DML delete site — so an operator edit to the flow
 * definition propagates on the next chat turn, alongside the router-decision and
 * static head/tail caches.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurChatFlowGraphCache {

    /** Cache holding the parsed {@link ChatFlowGraph} per flow id. */
    public static final String CACHE_NAME = "turChatFlowGraph";

    /**
     * Returns the cached parsed graph for {@code flowId}, computing it via
     * {@code parse} on a miss. The {@code parse} supplier is intentionally
     * excluded from the cache key (only {@code flowId} forms it); on a hit the
     * supplier is never invoked — which is the point, since it re-reads the
     * definition JSON and (for a merge-managed proxy) may issue a DB round-trip
     * to hydrate the flow.
     *
     * <p>{@code unless = "#result == null"} keeps an unparseable / empty flow
     * out of the cache so a transient parse failure is retried rather than
     * pinned. Callers wrap the result in {@code Optional.ofNullable(...)}.
     *
     * @param flowId the flow id — the only part of the cache key
     * @param parse  produces the parsed graph (or {@code null}) on a cache miss
     * @return the parsed graph, or {@code null} when the flow has no usable
     *         definition
     */
    @Cacheable(cacheNames = CACHE_NAME, key = "#flowId", unless = "#result == null")
    public ChatFlowGraph graph(String flowId, Supplier<ChatFlowGraph> parse) {
        return parse.get();
    }
}

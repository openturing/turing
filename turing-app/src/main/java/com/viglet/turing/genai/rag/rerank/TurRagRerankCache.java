/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.rerank;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * T341 / §XVIII.1 — optional memoization layer for the SN RAG reranker.
 *
 * <p>A rerank result is deterministic per
 * {@code (strategy, model, query, candidate-set)}: the same query against the
 * same chunks, ranked by the same backend, always yields the same ordering. This
 * bean wraps that computation in a {@code @Cacheable("turRagRerankScore")} so a
 * repeated identical rerank call — the same query landing on the same retrieval
 * pool — skips the expensive LLM/HTTP scoring round-trip.
 *
 * <p><b>Why a separate bean.</b> Spring's {@code @Cacheable} is proxy-based, so
 * the {@link com.viglet.turing.genai.rag.TurRagReranker} facade can't cache an
 * intra-class method via {@code this} (it would no-op). Routing the cacheable
 * call through this injected bean keeps the proxy in the path — the canonical
 * pattern from {@code TurChatFlowStaticPromptCache}.
 *
 * <p><b>What is cached.</b> Only the strategy's ordered list of document ids
 * (most relevant first), never the {@link org.springframework.ai.document.Document}
 * objects themselves — a {@code List<String>} is trivially serializable across a
 * clustered Hazelcast {@code IMap}, and the facade re-maps ids back to the live
 * candidate documents. The candidate set is pinned by a content hash in the key,
 * so a different (or edited) pool never collides with a stale ordering.
 *
 * <p><b>Eviction.</b> The {@code turRagRerankScore} cache name is paired with a
 * {@code @CacheEvict} on the reranker-config setters in
 * {@code TurGlobalSettingsService} (strategy / model / endpoint / api-key /
 * cache-enabled): changing any of those changes the ranking and must drop every
 * memoized ordering. Content edits self-invalidate via the candidate hash.
 *
 * <p>The facade only routes through this bean when the cache is enabled
 * (Global Settings, default off), so when disabled the {@code @Cacheable} method
 * is never invoked and behavior is identical to the pre-T341 path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurRagRerankCache {

    /**
     * Returns the cached strategy ordering for the given key, computing it via
     * {@code compute} on a miss. The {@code compute} supplier is intentionally
     * excluded from the cache key (only the named scalar args form it); on a hit
     * the supplier is never invoked, which is the whole point — the LLM/HTTP
     * scoring it performs is skipped.
     *
     * <p>The supplier may throw: a strategy failure propagates out (it is
     * <em>not</em> cached) and the facade maps it to "keep retrieval order".
     *
     * @param strategy       the strategy type name (part of the key)
     * @param model          the ranking model id, or {@code ""} (part of the key)
     * @param query          the user query (part of the key)
     * @param candidatesHash a content hash pinning the exact candidate set+order
     * @param topK           how many results were requested (part of the key)
     * @param compute        produces the ordered document ids on a cache miss
     * @return the strategy's ordered document ids, most relevant first
     */
    @Cacheable(cacheNames = "turRagRerankScore",
            key = "#strategy + '|' + #model + '|' + #topK + '|' + #candidatesHash + '|' + #query")
    public List<String> orderedIds(String strategy, String model, String query,
            String candidatesHash, int topK, Supplier<List<String>> compute) {
        return compute.get();
    }
}

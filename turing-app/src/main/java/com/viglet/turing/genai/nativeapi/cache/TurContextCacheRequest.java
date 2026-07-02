/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.cache;

import java.time.Duration;
import java.util.List;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * T499 / §X.20 — the vendor-neutral input to {@link TurContextCacheProvider}: the
 * agent's <em>stable prefix</em> that repeats across turns (and, for explicit
 * caches like Gemini's, across users and conversations).
 *
 * <p>It carries only provider-agnostic values — no vendor SDK types — so the
 * same request feeds the Gemini explicit-cache implementation, the Anthropic
 * {@code cache_control} mapping (T502), and the no-op default alike:
 *
 * <ul>
 *   <li>{@code instance} — the resolved {@link TurLLMInstance}; a provider uses
 *       it to obtain its own native client + per-instance options. It is the
 *       domain entity already threaded everywhere, not a vendor client.</li>
 *   <li>{@code model} — the resolved model name the cache is bound to (a Gemini
 *       cache is model-scoped).</li>
 *   <li>{@code systemInstruction} — the assembled system prompt (persona + RAG
 *       override + brand context). The dominant stable prefix.</li>
 *   <li>{@code pinnedContents} — any additional large stable text blocks pinned
 *       into context (the T500 full-context corpus, a big manual). Empty for the
 *       plain system-prompt case.</li>
 *   <li>{@code ttl} — requested cache lifetime; {@code null} lets the provider
 *       apply its configured default.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurContextCacheRequest(
        TurLLMInstance instance,
        String model,
        String systemInstruction,
        List<String> pinnedContents,
        Duration ttl) {

    public TurContextCacheRequest {
        pinnedContents = pinnedContents == null ? List.of() : List.copyOf(pinnedContents);
    }

    /** Convenience for the common system-prompt-only case (no pinned corpus). */
    public static TurContextCacheRequest ofSystemInstruction(TurLLMInstance instance, String model,
            String systemInstruction, Duration ttl) {
        return new TurContextCacheRequest(instance, model, systemInstruction, List.of(), ttl);
    }

    /** True when there is no cacheable prefix at all (nothing to cache). */
    public boolean isEmpty() {
        boolean noSystem = systemInstruction == null || systemInstruction.isBlank();
        boolean noPinned = pinnedContents.stream().allMatch(s -> s == null || s.isBlank());
        return noSystem && noPinned;
    }
}

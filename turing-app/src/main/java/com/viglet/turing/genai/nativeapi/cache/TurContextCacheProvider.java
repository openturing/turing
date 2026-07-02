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

import java.util.Optional;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * T499 / §X.20 — the cross-vendor <em>context caching</em> seam. The single
 * largest recurring cost in RAG/agent turns is re-sending the same large prefix
 * (system prompt + persona + pinned grounding) every turn. Each vendor avoids
 * paying full price for that prefix differently — Gemini exposes <em>explicit,
 * named, TTL'd</em> cache objects ({@code client.caches().create}) reusable
 * across turns <b>and users</b> at ~75% off; Anthropic marks message blocks with
 * <em>ephemeral</em> {@code cache_control} breakpoints (T502); OpenAI caches the
 * prefix <em>automatically</em> with no API surface.
 *
 * <p>This interface unifies the first two behind one contract: given the stable
 * prefix ({@link TurContextCacheRequest}) it returns a vendor-neutral
 * {@link TurContextCacheRef} the native path threads into the request, or
 * {@link Optional#empty()} to keep the call on its default path. The
 * {@linkplain TurNoOpContextCacheProvider no-op default} always returns empty —
 * so OpenAI (and any unconfigured instance) stays on its automatic path
 * byte-for-byte unchanged.
 *
 * <p>Implementations must <b>fail open</b>: any error creating or reusing a
 * cache yields {@link Optional#empty()} (a missed discount), never a failed
 * turn.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurContextCacheProvider {

    /**
     * Lowercased provider plugin type this implementation serves (matches
     * {@code TurGenAiLlmProvider#getPluginType()}); {@code none} for the no-op.
     */
    String getPluginType();

    /** True when this provider can cache the prefix for the given instance. */
    boolean supports(TurLLMInstance instance);

    /**
     * Ensure a context cache exists for the request's stable prefix, creating it
     * if needed or reusing a still-valid one, and return its handle. Returns
     * {@link Optional#empty()} when caching is unavailable, not worthwhile, or
     * failed (fail-open) — the caller then proceeds on the default path.
     */
    Optional<TurContextCacheRef> ensureCache(TurContextCacheRequest request);
}

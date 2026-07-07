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

import java.time.Instant;

/**
 * T499 / §X.20 — the vendor-neutral handle a {@link TurContextCacheProvider}
 * returns for a successfully created (or reused) context cache. The native chat
 * path threads {@link #handle()} into the provider request so the cached prefix
 * is billed once and reused at a discount.
 *
 * <ul>
 *   <li>{@code pluginType} — the vendor that owns the cache ({@code gemini}).</li>
 *   <li>{@code handle} — the opaque vendor cache reference (for Gemini, the
 *       {@code cachedContents/...} resource name set on
 *       {@code GenerateContentConfig.cachedContent}).</li>
 *   <li>{@code cachedTokenCount} — tokens the cache covers, when the vendor
 *       reports it ({@code null} otherwise) — useful to log the savings.</li>
 *   <li>{@code expiresAt} — when the cache lapses, when known ({@code null}
 *       otherwise).</li>
 *   <li>{@code reused} — {@code true} when served from the provider's in-process
 *       registry (no create call this turn), {@code false} on a fresh create.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurContextCacheRef(
        String pluginType,
        String handle,
        Integer cachedTokenCount,
        Instant expiresAt,
        boolean reused) {
}

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

import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * T499 / §X.20 — the default {@link TurContextCacheProvider}: it never caches.
 * {@link #ensureCache} always returns {@link Optional#empty()}, so any instance
 * without an explicit-cache implementation (notably OpenAI, which caches the
 * prefix automatically with no API surface to manage) stays on its default path
 * byte-for-byte unchanged. The {@link TurContextCacheProviderFactory} falls back
 * to this when no vendor provider matches.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurNoOpContextCacheProvider implements TurContextCacheProvider {

    /** Sentinel plugin type — never matches a real instance vendor. */
    public static final String PLUGIN_TYPE = "none";

    @Override
    public String getPluginType() {
        return PLUGIN_TYPE;
    }

    @Override
    public boolean supports(TurLLMInstance instance) {
        return false;
    }

    @Override
    public Optional<TurContextCacheRef> ensureCache(TurContextCacheRequest request) {
        return Optional.empty();
    }
}

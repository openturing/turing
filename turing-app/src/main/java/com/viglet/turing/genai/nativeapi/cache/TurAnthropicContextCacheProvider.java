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

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * T502 / §X.20 — the Anthropic leg of the cross-vendor context-caching seam.
 * Unlike Gemini's <em>explicit, named</em> cache object (a remote
 * {@code cachedContents} handle), Anthropic caching is <em>inline</em>: you mark
 * message blocks (the system prompt, the retrieved {@code search_result} blocks)
 * with an ephemeral {@code cache_control} breakpoint in the same request and the
 * API serves the repeated prefix at a discount. So this provider creates nothing
 * remote — {@link #ensureCache} simply answers "is caching opted in, and at what
 * TTL?" by returning a {@link TurContextCacheRef} whose {@link TurContextCacheRef#handle()}
 * carries the TTL token ({@code 5m} / {@code 1h}). The Anthropic Messages native
 * path reads that ref and applies the {@code cache_control} breakpoints; every
 * vendor's native path interprets its own ref shape.
 *
 * <p>Opt-in via the per-instance {@code cacheControlEnabled} provider option
 * (with optional {@code cacheControlTtl}); absent / false → {@link Optional#empty()}
 * → byte-for-byte unchanged Anthropic requests.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurAnthropicContextCacheProvider implements TurContextCacheProvider {

    static final String PLUGIN_TYPE = "anthropic";
    /** Default ephemeral TTL when the instance opts in without naming one. */
    static final String DEFAULT_TTL = "5m";

    private final TurProviderOptionsParser optionsParser;

    public TurAnthropicContextCacheProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @Override
    public String getPluginType() {
        return PLUGIN_TYPE;
    }

    @Override
    public boolean supports(TurLLMInstance instance) {
        return PLUGIN_TYPE.equalsIgnoreCase(vendorPlugin(instance));
    }

    @Override
    public Optional<TurContextCacheRef> ensureCache(TurContextCacheRequest request) {
        if (request == null || request.instance() == null) {
            return Optional.empty();
        }
        Map<String, Object> options = optionsParser.parse(request.instance().getProviderOptionsJson());
        if (!isCacheControlEnabled(options)) {
            return Optional.empty();
        }
        String ttl = normalizeTtl(optionsParser.stringValue(options, "cacheControlTtl"));
        // No remote object: the handle carries the TTL token the native path maps
        // to an ephemeral cache_control breakpoint. expiresAt/tokenCount are N/A.
        return Optional.of(new TurContextCacheRef(PLUGIN_TYPE, ttl, null, null, false));
    }

    private boolean isCacheControlEnabled(Map<String, Object> options) {
        Object value = options == null ? null : options.get("cacheControlEnabled");
        return value != null && "true".equalsIgnoreCase(value.toString().trim());
    }

    /** Only {@code 5m} and {@code 1h} are valid Anthropic ephemeral TTLs; default 5m. */
    private static String normalizeTtl(String configured) {
        if (configured == null) {
            return DEFAULT_TTL;
        }
        String t = configured.trim().toLowerCase(Locale.ROOT);
        return "1h".equals(t) ? "1h" : DEFAULT_TTL;
    }

    private static String vendorPlugin(TurLLMInstance instance) {
        if (instance == null || instance.getTurLLMVendor() == null) {
            return null;
        }
        String plugin = instance.getTurLLMVendor().getPlugin();
        if (!StringUtils.hasText(plugin)) {
            plugin = instance.getTurLLMVendor().getId();
        }
        return plugin == null ? null : plugin.toLowerCase(Locale.ROOT);
    }
}

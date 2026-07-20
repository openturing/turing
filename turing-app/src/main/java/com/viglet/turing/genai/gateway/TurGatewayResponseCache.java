/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.gateway.TurOpenAiWire.WireMessage;
import com.viglet.turing.properties.TurGatewayProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * T743 / §XLIX — opt-in response cache for the Governed LLM Gateway, activated
 * per request by the {@code x-turing-cache: semantic} header. Modelled on
 * {@code TurLlmCacheService} (in-memory, TTL) but deliberately gateway-local and
 * NOT gated on the site-chat LLM-cache global flag, so the header is honoured in
 * any gateway deployment.
 *
 * <p>Keys are the SHA-256 of the resolved model + the full message list, so an
 * identical prompt replays the cached completion. Exact-prompt matching ships
 * now; embedding-similarity ("truly semantic") matching is a documented future
 * extension — the header value {@code semantic} already selects this path.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurGatewayResponseCache {

    private record Entry(String content, Instant createdAt) {
    }

    private final TurGatewayProperty gatewayProperty;

    /** Access-ordered LRU map, bounded by {@code cacheMaxEntries}. */
    private final Map<String, Entry> cache;

    public TurGatewayResponseCache(TurGatewayProperty gatewayProperty) {
        this.gatewayProperty = gatewayProperty;
        int max = Math.max(16, gatewayProperty.getCacheMaxEntries());
        this.cache = java.util.Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > max;
            }
        });
    }

    /** Cache key for a (model, messages) pair. */
    public String keyFor(String model, List<WireMessage> messages) {
        StringBuilder sb = new StringBuilder(model == null ? "" : model);
        if (messages != null) {
            for (WireMessage m : messages) {
                sb.append('\n').append(m.role()).append(':').append(m.content());
            }
        }
        return sha256Hex(sb.toString());
    }

    /** Cached completion text, or {@code null} on miss/expiry. */
    public String get(String key) {
        Entry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        long ttlMs = Math.max(1, gatewayProperty.getCacheTtlSeconds()) * 1000L;
        if (Instant.now().isAfter(entry.createdAt().plusMillis(ttlMs))) {
            cache.remove(key);
            return null;
        }
        return entry.content();
    }

    public void put(String key, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        cache.put(key, new Entry(content, Instant.now()));
    }

    public void evictAll() {
        cache.clear();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

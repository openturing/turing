package com.viglet.turing.system;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * In-memory cache for LLM responses, keyed by site ID.
 * Respects TTL and regenerate settings from {@link TurGlobalSettingsService}.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Slf4j
@Service
public class TurLlmCacheService {

    private final TurGlobalSettingsService globalSettingsService;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public TurLlmCacheService(TurGlobalSettingsService globalSettingsService) {
        this.globalSettingsService = globalSettingsService;
    }

    public String get(String siteId) {
        if (!globalSettingsService.isLlmCacheEnabled()) {
            return null;
        }

        CacheEntry entry = cache.get(siteId);
        if (entry == null) {
            return null;
        }

        long ttlMs = globalSettingsService.getLlmCacheTtlMs();
        if (Instant.now().isAfter(entry.createdAt().plusMillis(ttlMs))) {
            cache.remove(siteId);
            log.debug("Cache expired for site {}", siteId);
            return null;
        }

        return entry.content();
    }

    public void put(String siteId, String content) {
        if (!globalSettingsService.isLlmCacheEnabled()) {
            return;
        }
        cache.put(siteId, new CacheEntry(content, Instant.now()));
        log.debug("Cached LLM response for site {}", siteId);
    }

    public void evict(String siteId) {
        cache.remove(siteId);
    }

    public void evictAll() {
        cache.clear();
    }

    private record CacheEntry(String content, Instant createdAt) {
    }
}

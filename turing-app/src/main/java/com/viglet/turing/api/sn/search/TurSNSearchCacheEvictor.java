/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.api.sn.search;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.sn.search.TurSNSearchCacheKey;
import com.viglet.turing.tenant.TurTenantCacheKey;

import lombok.extern.slf4j.Slf4j;

/**
 * T870 / §LXVIII.1 — evicts the {@code searchAPI} cache for the sites a write
 * actually changed.
 *
 * <p>
 * Before this, the cache had exactly one eviction: a {@code @Scheduled} sweep
 * with {@code allEntries = true}. A reindexed, deindexed or edited document kept
 * serving its previous result — including its previous facet counts — until the
 * next sweep, 24 hours later by default. Caching a <em>derived</em> read on a
 * time-based policy alone is correct right up until the underlying data changes,
 * which is the one moment anyone notices.
 * </p>
 *
 * <p>
 * Eviction is per site rather than {@code allEntries} because indexing one site
 * says nothing about another's answers, and a bulk import of one site would
 * otherwise cold-start every site on the instance. The site is the finest
 * granularity available: the answer to a query is derived from the whole index,
 * so a single document cannot be mapped back to the keys it affects.
 * </p>
 *
 * <p>
 * The Spring {@link Cache} contract has no key enumeration and no prefix
 * eviction, so the keys are read off the native cache — a {@link Map} for both
 * managers this app wires ({@code ConcurrentMapCache} and Hazelcast's
 * {@code IMap}) — and evicted through {@link Cache#evict}, which is the
 * provider-agnostic call. A native cache that is not a {@code Map} degrades to
 * {@link Cache#clear()}: coarser than intended, still correct.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurSNSearchCacheEvictor {

    /** The cache name shared with {@link TurSNSiteSearchCachedAPI}. */
    public static final String SEARCH_CACHE = "searchAPI";

    private final CacheManager cacheManager;
    private final TurTenantCacheKey tenantCacheKey;
    private final boolean searchCacheEnabled;

    public TurSNSearchCacheEvictor(CacheManager cacheManager, TurTenantCacheKey tenantCacheKey,
            @Value("${turing.search.cache.enabled:false}") boolean searchCacheEnabled) {
        this.cacheManager = cacheManager;
        this.tenantCacheKey = tenantCacheKey;
        this.searchCacheEnabled = searchCacheEnabled;
    }

    /**
     * Evicts every cached search answer for each of {@code siteNames}. Safe to
     * call with an empty collection, an unknown site, or the cache disabled.
     */
    public void evictSites(Collection<String> siteNames) {
        if (!searchCacheEnabled || siteNames == null || siteNames.isEmpty()) {
            return;
        }
        Cache cache = cacheManager.getCache(SEARCH_CACHE);
        if (cache == null) {
            return;
        }
        siteNames.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .forEach(siteName -> evictSite(cache, siteName));
    }

    /** Evicts every cached search answer for one site. */
    public void evictSite(String siteName) {
        evictSites(List.of(siteName));
    }

    /**
     * T896 / §LXVIII.5 — evicts every cached search answer, for every site and
     * tenant.
     *
     * <p>
     * Used by the configuration write paths (spotlights, synonyms, field/facet/sort
     * config, site settings), which reach the cache through
     * {@code TurSNSearchCacheEviction­Listener} rather than the indexing queue.
     * Coarse on purpose: unlike a document write these are rare, operator-driven
     * edits, so the precision T870 needed for the indexing firehose buys nothing
     * here — and resolving the affected site would mean walking a lazy association
     * inside a JPA callback, which is the shape Block AC removed for good reason.
     * </p>
     */
    public void evictAll() {
        if (!searchCacheEnabled) {
            return;
        }
        Cache cache = cacheManager.getCache(SEARCH_CACHE);
        if (cache == null) {
            return;
        }
        cache.clear();
        log.debug("[T896] Cleared the '{}' cache after a search-affecting configuration write",
                SEARCH_CACHE);
    }

    private void evictSite(Cache cache, String siteName) {
        // T895 / §LXVIII.4 — the stored key is tenant-scoped, so the prefix has to be
        // too, or a write in tenant A would evict every tenant's copy of that site
        // name. The indexing queue binds the tenant from the message header before
        // this runs (T272), so the current tenant is the one that was written.
        String prefix = tenantCacheKey
                .scoped(TurSNSearchCacheKey.sitePrefix(siteName))
                .toLowerCase(Locale.ROOT);
        Object nativeCache = cache.getNativeCache();
        if (!(nativeCache instanceof Map<?, ?> map)) {
            log.debug("[T870] Native cache of '{}' is not a Map ({}) — clearing all entries",
                    SEARCH_CACHE, nativeCache == null ? "null" : nativeCache.getClass().getName());
            cache.clear();
            return;
        }
        // A Hazelcast IMap returns a snapshot from keySet(), so the keys are
        // collected first and then evicted one by one through the Cache contract —
        // removing from the returned collection would be a no-op there.
        List<Object> keys = map.keySet().stream()
                .filter(key -> matchesSite(key, prefix))
                .map(Object.class::cast)
                .toList();
        keys.forEach(cache::evict);
        if (!keys.isEmpty()) {
            log.debug("[T870] Evicted {} search cache entrie(s) of SN Site '{}'", keys.size(),
                    siteName);
        }
    }

    private static boolean matchesSite(Object key, String prefix) {
        return key instanceof String stringKey
                && stringKey.toLowerCase(Locale.ROOT).startsWith(prefix);
    }
}

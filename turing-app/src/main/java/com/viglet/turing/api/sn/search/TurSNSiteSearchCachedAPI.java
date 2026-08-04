/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.api.sn.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.sn.TurSNSearchProcess;

import lombok.extern.slf4j.Slf4j;

/**
 * The {@code @Cacheable} seam of the search read path: one proxied method whose
 * key is supplied by the caller (derived from the search context — T858 /
 * §LXV.3), plus the backstop sweep that bounds an entry's lifetime.
 *
 * <p>
 * T872 / §LXVIII.3 — this bean used to also hold the MongoDB logging
 * configuration and its nightly purge job, so a cache primitive carried seven
 * Mongo constructor parameters it never read. That job now lives in
 * {@code TurLoggingPurgeService}; what remains here is only what the cached
 * search actually uses.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurSNSiteSearchCachedAPI {

    private final TurSNSearchProcess turSNSearchProcess;

    public TurSNSiteSearchCachedAPI(TurSNSearchProcess turSNSearchProcess,
            @Value("${turing.search.cache.ttl.seconds:}") String legacyCacheTtl) {
        warnLegacyCacheTtl(legacyCacheTtl);
        this.turSNSearchProcess = turSNSearchProcess;
    }

    /**
     * T871 / §LXVIII.2 — a configuration that still sets the old key is silently
     * off by a thousand from what its name says, so it is named at startup rather
     * than left to be discovered by the sweep frequency.
     */
    private static void warnLegacyCacheTtl(String legacyCacheTtl) {
        if (legacyCacheTtl != null && !legacyCacheTtl.isBlank()) {
            log.warn("[T871] 'turing.search.cache.ttl.seconds' is deprecated and its value ({}) "
                    + "is read as MILLISECONDS, not seconds. Use 'turing.search.cache.ttl' with a "
                    + "stated unit instead (e.g. 24h, PT1H, 30m).", legacyCacheTtl.trim());
        }
    }

    /**
     * The <strong>backstop</strong> sweep. Since T870 / §LXVIII.1 the authoritative
     * eviction happens per site at the moment a write changes the answer
     * ({@link TurSNSearchCacheEvictor}, driven from the indexing queue); this sweep
     * only bounds the lifetime of entries whose site was never written again — and
     * catches anything a write path forgets.
     *
     * <p>
     * T871 / §LXVIII.2 — the interval is {@code turing.search.cache.ttl}, a
     * <em>unit-bearing</em> value: {@code 24h}, {@code PT1H}, {@code 30m}. It
     * replaces {@code turing.search.cache.ttl.seconds}, whose name promised seconds
     * while {@code fixedRateString} read milliseconds — so an operator who set
     * {@code 3600} for "sweep hourly" got a full-cache eviction every 3.6 seconds.
     * The old key stays bound (as the milliseconds it always was) so an existing
     * configuration keeps its current behaviour for one version.
     * </p>
     */
    @CacheEvict(value = TurSNSearchCacheEvictor.SEARCH_CACHE, allEntries = true)
    @Scheduled(fixedRateString = "${turing.search.cache.ttl:${turing.search.cache.ttl.seconds:24h}}")
    public void cleanSearchCache() {
        log.info("Cleaning Search API");
    }

    /**
     * T895 / §LXVIII.4 — the key is scoped by tenant. It is supplied by the caller
     * (derived from the search context) and begins with the SN site name, which is
     * unique only within a tenant, so without the wrapper two tenants owning a site
     * of the same name would serve each other's documents.
     */
    @Cacheable(value = TurSNSearchCacheEvictor.SEARCH_CACHE,
            key = "@turTenantCacheKey.scoped(#cacheKey)")
    public TurSNSiteSearchBean searchCached(String cacheKey,
            TurSNSiteSearchContext turSNSiteSearchContext) {
        log.info("Search cache key: {}", cacheKey);
        return turSNSearchProcess.search(turSNSiteSearchContext);
    }
}

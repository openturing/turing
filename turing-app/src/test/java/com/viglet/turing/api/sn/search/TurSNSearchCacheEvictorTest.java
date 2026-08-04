package com.viglet.turing.api.sn.search;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.NoOpCacheManager;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNSearchCacheKey;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;

/**
 * T870 / §LXVIII.1 — the evictor must remove exactly the keys of the site that
 * was written and leave every other site's entries alone.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNSearchCacheEvictorTest {

    private static final String SITE = "Sample";
    private static final String OTHER_SITE = "Other";

    private static String keyOf(String siteName, String query) {
        TurSEParameters parameters = new TurSEParameters(new TurSNSearchParams());
        parameters.setQuery(query);
        return TurSNSearchCacheKey.of(new TurSNSiteSearchContext(siteName, null, parameters,
                Locale.ENGLISH, null));
    }

    private static Cache seed(CacheManager cacheManager) {
        Cache cache = cacheManager.getCache(TurSNSearchCacheEvictor.SEARCH_CACHE);
        assertNotNull(cache);
        cache.put(keyOf(SITE, "one"), new TurSNSiteSearchBean());
        cache.put(keyOf(SITE, "two"), new TurSNSiteSearchBean());
        cache.put(keyOf(OTHER_SITE, "one"), new TurSNSiteSearchBean());
        return cache;
    }

    @Test
    void evictSiteRemovesOnlyThatSitesEntries() {
        CacheManager cacheManager = new ConcurrentMapCacheManager();
        Cache cache = seed(cacheManager);

        new TurSNSearchCacheEvictor(cacheManager, true).evictSite(SITE);

        assertNull(cache.get(keyOf(SITE, "one")));
        assertNull(cache.get(keyOf(SITE, "two")));
        assertNotNull(cache.get(keyOf(OTHER_SITE, "one")));
    }

    /**
     * The key carries the site name as the caller spelled it, while the indexing
     * path resolves the site case-insensitively — so a write to {@code sample}
     * must still evict the entries cached for {@code Sample}.
     */
    @Test
    void evictSiteIsCaseInsensitive() {
        CacheManager cacheManager = new ConcurrentMapCacheManager();
        Cache cache = seed(cacheManager);

        new TurSNSearchCacheEvictor(cacheManager, true).evictSite(SITE.toUpperCase(Locale.ROOT));

        assertNull(cache.get(keyOf(SITE, "one")));
        assertNotNull(cache.get(keyOf(OTHER_SITE, "one")));
    }

    @Test
    void evictSitesDeduplicatesAndIgnoresBlankNames() {
        CacheManager cacheManager = new ConcurrentMapCacheManager();
        Cache cache = seed(cacheManager);

        new TurSNSearchCacheEvictor(cacheManager, true)
                .evictSites(List.of(SITE, SITE, "   ", ""));

        assertNull(cache.get(keyOf(SITE, "one")));
        assertNotNull(cache.get(keyOf(OTHER_SITE, "one")));
    }

    /** With the cache off there is nothing to evict — and nothing to create. */
    @Test
    void disabledCacheIsANoOp() {
        CacheManager cacheManager = new ConcurrentMapCacheManager();
        Cache cache = seed(cacheManager);

        new TurSNSearchCacheEvictor(cacheManager, false).evictSite(SITE);

        assertNotNull(cache.get(keyOf(SITE, "one")));
    }

    @Test
    void emptyOrNullSiteListIsANoOp() {
        CacheManager cacheManager = new ConcurrentMapCacheManager();
        Cache cache = seed(cacheManager);
        TurSNSearchCacheEvictor evictor = new TurSNSearchCacheEvictor(cacheManager, true);

        assertDoesNotThrow(() -> evictor.evictSites(null));
        assertDoesNotThrow(() -> evictor.evictSites(Set.of()));

        assertNotNull(cache.get(keyOf(SITE, "one")));
    }

    /**
     * A cache manager whose native cache is not a {@link java.util.Map} degrades to
     * a full clear rather than failing — coarser than intended, still correct.
     */
    @Test
    void nonMapNativeCacheDegradesToClear() {
        CacheManager cacheManager = new NoOpCacheManager();
        TurSNSearchCacheEvictor evictor = new TurSNSearchCacheEvictor(cacheManager, true);

        assertDoesNotThrow(() -> evictor.evictSite(SITE));
    }

    @Test
    void sitePrefixIsTheFirstSegmentOfEveryKey() {
        assertEquals("site=sample|", TurSNSearchCacheKey.sitePrefix(SITE));
        assertEquals("site=sample|", TurSNSearchCacheKey.sitePrefix("SAMPLE"));
    }
}

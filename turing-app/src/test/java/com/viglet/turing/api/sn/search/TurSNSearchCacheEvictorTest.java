package com.viglet.turing.api.sn.search;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.viglet.core.tenancy.VigletTenantContext;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNSearchCacheKey;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.system.cache.TurCacheEvictionScheduler;
import com.viglet.turing.tenant.TurTenantCacheKey;

/**
 * T870 / §LXVIII.1 — the evictor must remove exactly the keys of the site that
 * was written and leave every other site's entries alone.
 *
 * <p>
 * T895 / §LXVIII.4 — and exactly the keys of the <em>tenant</em> that was
 * written: the stored key is tenant-scoped, so a write in one tenant must neither
 * evict nor reveal another tenant's copy of the same site name.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNSearchCacheEvictorTest {

    private static final String SITE = "Sample";
    private static final String OTHER_SITE = "Other";
    private static final String TENANT_A = "tenantA";
    private static final String TENANT_B = "tenantB";

    /** The context is a static thread-local, so it is cleared between cases. */
    private final VigletTenantContext tenantContext = new VigletTenantContext(true, "DEFAULT");
    private final TurTenantCacheKey tenantCacheKey = new TurTenantCacheKey(tenantContext);

    @AfterEach
    void clearTenant() {
        tenantContext.clear();
    }

    /** The key exactly as the `@Cacheable` SpEL would store it for the current tenant. */
    private String keyOf(String siteName, String query) {
        TurSEParameters parameters = new TurSEParameters(new TurSNSearchParams());
        parameters.setQuery(query);
        return tenantCacheKey.scoped(TurSNSearchCacheKey.of(new TurSNSiteSearchContext(
                siteName, null, parameters, Locale.ENGLISH, null)));
    }

    private String keyOf(String tenantId, String siteName, String query) {
        tenantContext.setCurrentTenant(tenantId);
        return keyOf(siteName, query);
    }

    private Cache seed(CacheManager cacheManager) {
        Cache cache = cacheManager.getCache(TurSNSearchCacheEvictor.SEARCH_CACHE);
        assertNotNull(cache);
        cache.put(keyOf(SITE, "one"), new TurSNSiteSearchBean());
        cache.put(keyOf(SITE, "two"), new TurSNSiteSearchBean());
        cache.put(keyOf(OTHER_SITE, "one"), new TurSNSiteSearchBean());
        return cache;
    }

    private TurSNSearchCacheEvictor evictor(CacheManager cacheManager, boolean enabled) {
        return new TurSNSearchCacheEvictor(cacheManager,
                new TurCacheEvictionScheduler(cacheManager), tenantCacheKey, enabled);
    }

    @Test
    void evictSiteRemovesOnlyThatSitesEntries() {
        CacheManager cacheManager = new ConcurrentMapCacheManager();
        Cache cache = seed(cacheManager);

        evictor(cacheManager, true).evictSite(SITE);

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

        evictor(cacheManager, true).evictSite(SITE.toUpperCase(Locale.ROOT));

        assertNull(cache.get(keyOf(SITE, "one")));
        assertNotNull(cache.get(keyOf(OTHER_SITE, "one")));
    }

    @Test
    void evictSitesDeduplicatesAndIgnoresBlankNames() {
        CacheManager cacheManager = new ConcurrentMapCacheManager();
        Cache cache = seed(cacheManager);

        evictor(cacheManager, true).evictSites(List.of(SITE, SITE, "   ", ""));

        assertNull(cache.get(keyOf(SITE, "one")));
        assertNotNull(cache.get(keyOf(OTHER_SITE, "one")));
    }

    /** With the cache off there is nothing to evict — and nothing to create. */
    @Test
    void disabledCacheIsANoOp() {
        CacheManager cacheManager = new ConcurrentMapCacheManager();
        Cache cache = seed(cacheManager);

        evictor(cacheManager, false).evictSite(SITE);

        assertNotNull(cache.get(keyOf(SITE, "one")));
    }

    @Test
    void emptyOrNullSiteListIsANoOp() {
        CacheManager cacheManager = new ConcurrentMapCacheManager();
        Cache cache = seed(cacheManager);
        TurSNSearchCacheEvictor evictor = evictor(cacheManager, true);

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
        TurSNSearchCacheEvictor evictor = evictor(cacheManager, true);

        assertDoesNotThrow(() -> evictor.evictSite(SITE));
    }

    @Test
    void sitePrefixIsTheFirstSegmentOfEveryKey() {
        assertEquals("site=sample|", TurSNSearchCacheKey.sitePrefix(SITE));
        assertEquals("site=sample|", TurSNSearchCacheKey.sitePrefix("SAMPLE"));
    }

    // ------------------------------------------------------------------
    // T895 — tenant partitioning
    // ------------------------------------------------------------------

    /** Two tenants owning a site of the same name must not share an entry. */
    @Test
    void sameSiteNameInTwoTenantsAreDistinctKeys() {
        String a = keyOf(TENANT_A, SITE, "one");
        String b = keyOf(TENANT_B, SITE, "one");

        assertNotNull(a);
        assertNotEquals(a, b);
    }

    /** A write in one tenant evicts only that tenant's copy of the site. */
    @Test
    void evictSiteDoesNotCrossTenants() {
        CacheManager cacheManager = new ConcurrentMapCacheManager();
        Cache cache = cacheManager.getCache(TurSNSearchCacheEvictor.SEARCH_CACHE);
        assertNotNull(cache);
        String keyA = keyOf(TENANT_A, SITE, "one");
        String keyB = keyOf(TENANT_B, SITE, "one");
        cache.put(keyA, new TurSNSiteSearchBean());
        cache.put(keyB, new TurSNSiteSearchBean());

        // The indexing queue binds the written tenant before the evictor runs.
        tenantContext.setCurrentTenant(TENANT_A);
        evictor(cacheManager, true).evictSite(SITE);

        assertNull(cache.get(keyA));
        assertNotNull(cache.get(keyB));
    }

    // ------------------------------------------------------------------
    // T900 — coalescing the whole-cache clears the indexing queue provokes
    // ------------------------------------------------------------------

    /** A counting cache manager, so "how many clears" is assertable. */
    private static final class CountingCacheManager extends ConcurrentMapCacheManager {
        private int clears;

        @Override
        protected Cache createConcurrentMapCache(String name) {
            Cache delegate = super.createConcurrentMapCache(name);
            return new org.springframework.cache.support.AbstractValueAdaptingCache(true) {
                @Override
                public String getName() {
                    return delegate.getName();
                }

                @Override
                public Object getNativeCache() {
                    return delegate.getNativeCache();
                }

                @Override
                protected Object lookup(Object key) {
                    ValueWrapper wrapper = delegate.get(key);
                    return wrapper == null ? null : wrapper.get();
                }

                @Override
                public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
                    return delegate.get(key, valueLoader);
                }

                @Override
                public void put(Object key, Object value) {
                    delegate.put(key, value);
                }

                @Override
                public void evict(Object key) {
                    delegate.evict(key);
                }

                @Override
                public void clear() {
                    clears++;
                    delegate.clear();
                }
            };
        }
    }

    /** Many clears inside one window become exactly one, performed on exit. */
    @Test
    void coalescingCollapsesManyClearsIntoOne() {
        CountingCacheManager cacheManager = new CountingCacheManager();
        TurSNSearchCacheEvictor evictor = evictor(cacheManager, true);

        evictor.beginCoalescing();
        try {
            for (int i = 0; i < 500; i++) {
                evictor.evictAll();
            }
            assertEquals(0, cacheManager.clears, "nothing is cleared until the window closes");
        } finally {
            evictor.endCoalescing();
        }

        assertEquals(1, cacheManager.clears);
    }

    /** A window nobody asked to clear in clears nothing. */
    @Test
    void coalescingWithoutAnyEvictionClearsNothing() {
        CountingCacheManager cacheManager = new CountingCacheManager();

        TurSNSearchCacheEvictor evictor = evictor(cacheManager, true);
        evictor.beginCoalescing();
        evictor.endCoalescing();

        assertEquals(0, cacheManager.clears);
    }

    /** Outside a window, behaviour is exactly what T896 shipped. */
    @Test
    void evictAllOutsideAWindowClearsImmediately() {
        CountingCacheManager cacheManager = new CountingCacheManager();
        TurSNSearchCacheEvictor evictor = evictor(cacheManager, true);

        evictor.evictAll();
        evictor.evictAll();

        assertEquals(2, cacheManager.clears);
    }

    /** The window must close even when the work throws, or evictions stay deferred. */
    @Test
    void coalescingReleasesTheWindowOnFailure() {
        CountingCacheManager cacheManager = new CountingCacheManager();
        TurSNSearchCacheEvictor evictor = evictor(cacheManager, true);

        evictor.beginCoalescing();
        try {
            evictor.evictAll();
            throw new IllegalStateException("indexing blew up");
        } catch (IllegalStateException expected) {
            // the caller's finally still closes the window
        } finally {
            evictor.endCoalescing();
        }

        // The pending clear is still performed, and the thread is no longer deferring.
        assertEquals(1, cacheManager.clears);
        evictor.evictAll();
        assertEquals(2, cacheManager.clears);
    }

    // ------------------------------------------------------------------
    // T901 — nothing is evicted until the writing transaction commits
    // ------------------------------------------------------------------

    /** Binds a synchronization-active transaction for the body, like Spring's tx proxy. */
    private static void inTransaction(Runnable body, boolean commit) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            body.run();
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            if (commit) {
                synchronizations.forEach(TransactionSynchronization::afterCommit);
            }
            int status = commit ? TransactionSynchronization.STATUS_COMMITTED
                    : TransactionSynchronization.STATUS_ROLLED_BACK;
            synchronizations.forEach(s -> s.afterCompletion(status));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * The window this closes: a search arriving between the evict and the commit used to
     * re-cache the pre-commit answer.
     */
    @Test
    void perSiteEvictionWaitsForTheCommit() {
        CacheManager cacheManager = new ConcurrentMapCacheManager();
        Cache cache = seed(cacheManager);
        TurSNSearchCacheEvictor evictor = evictor(cacheManager, true);

        inTransaction(() -> {
            evictor.evictSites(List.of(SITE));
            assertNotNull(cache.get(keyOf(SITE, "one")),
                    "still cached: the write it invalidates is not visible yet");
        }, true);

        assertNull(cache.get(keyOf(SITE, "one")));
        assertNotNull(cache.get(keyOf(OTHER_SITE, "one")));
    }

    /** A rolled-back write changed nothing, so it must discard nothing. */
    @Test
    void aRolledBackWriteEvictsNothing() {
        CacheManager cacheManager = new ConcurrentMapCacheManager();
        Cache cache = seed(cacheManager);
        TurSNSearchCacheEvictor evictor = evictor(cacheManager, true);

        inTransaction(() -> evictor.evictSites(List.of(SITE)), false);

        assertNotNull(cache.get(keyOf(SITE, "one")));
    }

    @Test
    void wholeCacheClearWaitsForTheCommit() {
        CountingCacheManager cacheManager = new CountingCacheManager();
        TurSNSearchCacheEvictor evictor = evictor(cacheManager, true);

        inTransaction(() -> {
            evictor.evictAll();
            assertEquals(0, cacheManager.clears);
        }, true);

        assertEquals(1, cacheManager.clears);
    }

    /** One clear per transaction, however many writes ask for it. */
    @Test
    void manyWritesInOneTransactionRegisterOneClear() {
        CountingCacheManager cacheManager = new CountingCacheManager();
        TurSNSearchCacheEvictor evictor = evictor(cacheManager, true);

        inTransaction(() -> {
            for (int i = 0; i < 40; i++) {
                evictor.evictAll();
            }
        }, true);

        assertEquals(1, cacheManager.clears);
    }

    /** The dedupe flag is per transaction, not per thread for all time. */
    @Test
    void aLaterTransactionOnTheSameThreadClearsAgain() {
        CountingCacheManager cacheManager = new CountingCacheManager();
        TurSNSearchCacheEvictor evictor = evictor(cacheManager, true);

        inTransaction(evictor::evictAll, true);
        inTransaction(evictor::evictAll, true);

        assertEquals(2, cacheManager.clears);
    }

    /** The queue's coalesced clear is closed inside the transaction, so it waits too. */
    @Test
    void theCoalescedClearAlsoWaitsForTheCommit() {
        CountingCacheManager cacheManager = new CountingCacheManager();
        TurSNSearchCacheEvictor evictor = evictor(cacheManager, true);

        inTransaction(() -> {
            evictor.beginCoalescing();
            try {
                evictor.evictAll();
                evictor.evictAll();
            } finally {
                evictor.endCoalescing();
            }
            assertEquals(0, cacheManager.clears, "the coalesced clear is not visible yet");
        }, true);

        assertEquals(1, cacheManager.clears);
    }

    /** With no transaction in progress, eviction is immediate — the pre-T901 path. */
    @Test
    void withoutATransactionEvictionIsImmediate() {
        CacheManager cacheManager = new ConcurrentMapCacheManager();
        Cache cache = seed(cacheManager);

        evictor(cacheManager, true).evictSites(List.of(SITE));

        assertNull(cache.get(keyOf(SITE, "one")));
    }

    /** Tenancy off collapses to one partition, so behaviour is unchanged. */
    @Test
    void tenancyOffUsesOnePartition() {
        VigletTenantContext off = new VigletTenantContext(false, "DEFAULT");
        TurTenantCacheKey key = new TurTenantCacheKey(off);
        off.setCurrentTenant("ignored");

        assertEquals("DEFAULT::site=sample|", key.scoped(TurSNSearchCacheKey.sitePrefix(SITE)));
        off.clear();
    }
}

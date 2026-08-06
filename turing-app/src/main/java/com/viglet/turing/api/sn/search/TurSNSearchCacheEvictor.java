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
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    /** Guards the T897 degradation warning so it is said once, not per write. */
    private final AtomicBoolean degradedWarningIssued = new AtomicBoolean(false);
    /**
     * T900 — the current thread's coalescing window, or {@code null} outside one. A
     * thread-local because the indexing queue's listener is what opens it, and the JPA
     * callbacks it collapses fire on that same thread.
     */
    private static final ThreadLocal<Deferral> coalescing = new ThreadLocal<>();
    /**
     * T901 — set once a whole-cache clear is registered for the current transaction, so
     * the other writes in it do not each register their own. Released in
     * {@code afterCompletion}.
     */
    private static final ThreadLocal<Boolean> clearRegistered = new ThreadLocal<>();

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
        // T901 / §LXVIII.9 — the tenant-scoped prefixes are resolved HERE, not in the
        // after-commit callback. The tenant lives in a thread-local, and binding what is
        // evicted to when it is evicted is exactly the kind of coupling that makes an
        // ordering fix reintroduce a scoping bug.
        List<String> prefixes = siteNames.stream()
                .filter(StringUtils::hasText)
                .map(this::sitePrefixFor)
                .distinct()
                .toList();
        if (prefixes.isEmpty()) {
            return;
        }
        whenVisible(() -> evictPrefixes(prefixes));
    }

    private void evictPrefixes(List<String> prefixes) {
        Cache cache = cacheManager.getCache(SEARCH_CACHE);
        if (cache == null) {
            return;
        }
        prefixes.forEach(prefix -> evictPrefix(cache, prefix));
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
        // T900 / §LXVIII.8 — inside the indexing queue this clear is redundant, not
        // merely expensive: the queue evicts each written site when the message ends.
        // Record that one was asked for and collapse the run into a single clear.
        Deferral deferral = coalescing.get();
        if (deferral != null) {
            deferral.requested = true;
            return;
        }
        // T901 / §LXVIII.9 — a JPA callback fires at flush, so outside the queue's
        // coalescing window this is still inside the writing transaction.
        scheduleClear();
    }

    private void clearNow() {
        Cache cache = cacheManager.getCache(SEARCH_CACHE);
        if (cache == null) {
            return;
        }
        cache.clear();
        log.debug("[T896] Cleared the '{}' cache after a search-affecting configuration write",
                SEARCH_CACHE);
    }

    /**
     * T900 / §LXVIII.8 — opens a window in which whole-cache clears are coalesced into
     * at most one, performed by {@link #endCoalescing()}.
     *
     * <p>
     * An <em>unmanaged spotlight</em> is not an operator edit: it arrives as a job item
     * on the indexing queue, and creating one saves the spotlight plus a row per term
     * and per document — each a managed write, so each fires {@link #evictAll()}. T899
     * roughly doubled that by making the delete path fire callbacks too, which it has
     * to. A single import carrying spotlights was therefore issuing on the order of a
     * thousand full clears where T870 was careful to issue one eviction per site.
     * </p>
     *
     * <p>
     * The clears are <em>redundant</em> there rather than wrong: the queue calls
     * {@link #evictSites} for every site it wrote before the message ends. So the run
     * collapses to one clear, performed on exit only if something asked for one — never
     * fewer evictions than before, only fewer repetitions of the same one.
     * </p>
     *
     * <p>
     * Deliberately bounded: a callback that fires at transaction commit runs after this
     * window closes and still clears immediately, exactly as it does today. This removes
     * the repetition it can see and claims nothing about the rest.
     * </p>
     *
     * <p>
     * Deliberately <em>not</em> a {@code coalescing(Runnable)} wrapper. Routing the
     * caller's body through this bean means a test that mocks it runs no body at all —
     * which is how the first attempt turned 14 queue tests into silent no-ops. Paired
     * begin/end calls degrade the other way: against a mock, both are no-ops and every
     * eviction simply happens immediately, which is the pre-T900 behaviour.
     * </p>
     */
    public void beginCoalescing() {
        if (searchCacheEnabled && coalescing.get() == null) {
            coalescing.set(new Deferral());
        }
    }

    /**
     * Closes the window opened by {@link #beginCoalescing()} and performs the single
     * clear it accumulated, if any. Must be called from a {@code finally}.
     */
    public void endCoalescing() {
        Deferral deferral = coalescing.get();
        if (deferral == null) {
            return;
        }
        coalescing.remove();
        if (deferral.requested) {
            log.debug("[T900] Coalesced the run's whole-cache clears into one");
            // T901 — the queue closes its window inside the transaction, so the one
            // clear it accumulated still has to wait for the commit.
            scheduleClear();
        }
    }

    /** Mutable flag for the current thread's coalescing window. */
    private static final class Deferral {
        private boolean requested;
    }

    /**
     * T901 / §LXVIII.9 — runs {@code eviction} when the data it invalidates is actually
     * visible to other threads: after commit if a transaction is in progress, inline if
     * none is.
     *
     * <p>
     * Every eviction this block added ran <em>inside</em> the writing transaction —
     * {@code processIndexingQueue} is {@code @Transactional}, and the JPA callbacks fire
     * at flush. That left a window between the evict and the commit in which a
     * concurrent search reads the pre-commit state and caches it, and the entry then
     * survives to the backstop sweep: the staleness this block spent five tasks
     * removing, reintroduced by ordering rather than by a missing eviction.
     * </p>
     *
     * <p>
     * The window never touched documents — those reach the search engine through
     * {@code indexDocuments}/{@code commit}, independently of the JPA transaction, so
     * they were already visible. What was exposed is the entity-backed configuration
     * written in the same transaction: a spotlight, a field, a synonym.
     * </p>
     *
     * <p>
     * Registering on {@code afterCommit} also makes a rollback correct rather than
     * merely harmless: a failed indexing message used to evict anyway, discarding good
     * entries to reflect a write that never happened.
     * </p>
     */
    private void whenVisible(Runnable eviction) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eviction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eviction.run();
                    }
                });
    }

    /**
     * A whole-cache clear, at most once per transaction.
     *
     * <p>
     * The T900 window collapses the clears the indexing queue provokes, but an admin
     * transaction that writes forty field-ext rows fires the listener forty times with no
     * window open. One clear invalidates everything the other thirty-nine would have, so
     * only the first is registered; the flag is released in {@code afterCompletion} so a
     * later transaction on the same thread registers its own.
     * </p>
     */
    private void scheduleClear() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            clearNow();
            return;
        }
        if (Boolean.TRUE.equals(clearRegistered.get())) {
            return;
        }
        clearRegistered.set(Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        clearNow();
                    }

                    @Override
                    public void afterCompletion(int status) {
                        clearRegistered.remove();
                    }
                });
    }

    /**
     * T895 / §LXVIII.4 — the stored key is tenant-scoped, so the prefix has to be too,
     * or a write in tenant A would evict every tenant's copy of that site name. The
     * indexing queue binds the tenant from the message header before this runs (T272),
     * so the current tenant is the one that was written.
     */
    private String sitePrefixFor(String siteName) {
        return tenantCacheKey.scoped(TurSNSearchCacheKey.sitePrefix(siteName))
                .toLowerCase(Locale.ROOT);
    }

    private void evictPrefix(Cache cache, String prefix) {
        Object nativeCache = cache.getNativeCache();
        if (!(nativeCache instanceof Map<?, ?> map)) {
            warnDegradedOnce(nativeCache);
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
            log.debug("[T870] Evicted {} search cache entrie(s) under '{}'", keys.size(), prefix);
        }
    }

    /**
     * T897 / §LXVIII.6 — says once, at {@code WARN}, that per-site eviction has
     * degraded to a full clear.
     *
     * <p>
     * The scan reads keys off {@link Cache#getNativeCache()}, which is an assumption
     * about the provider rather than about the {@link Cache} abstraction — true for
     * both managers this app wires. A provider whose native cache is not a
     * {@link Map} still evicts correctly, but it cold-starts every other site, which
     * is precisely the cost per-site eviction exists to avoid. That is worth
     * knowing, so it is not left at {@code DEBUG}; it is a property of the
     * deployment rather than of the request, so it is said once and not per write.
     * </p>
     */
    private void warnDegradedOnce(Object nativeCache) {
        if (degradedWarningIssued.compareAndSet(false, true)) {
            log.warn("[T897] Native cache of '{}' is a {}, not a Map — per-site eviction has "
                    + "degraded to clearing every site's entries. Still correct, but each "
                    + "indexed site now cold-starts every other one.", SEARCH_CACHE,
                    nativeCache == null ? "null" : nativeCache.getClass().getName());
        }
    }

    private static boolean matchesSite(Object key, String prefix) {
        return key instanceof String stringKey
                && stringKey.toLowerCase(Locale.ROOT).startsWith(prefix);
    }
}

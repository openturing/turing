/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.exchange.sn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.genai.TurGenAiContext;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.genai.TurGenAiContextFactory;
import com.viglet.turing.genai.TurSNGenAi;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.sn.field.TurSNSiteFieldService;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for exporting and importing indexed content from SN sites.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Slf4j
@Service
public class TurSNSiteContentExchangeService {

    // --- S1192: extracted duplicated literals ---
    private static final String COMPLETED = "completed";
    private static final String INDEXING = "indexing";
    private static final String UNKNOWN = "unknown";


    private static final int EXPORT_PAGE_SIZE = 500;
    public static final int IMPORT_CHUNK_SIZE = 100;

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSearchEnginePluginFactory pluginFactory;
    private final TurSNSiteFieldService turSNSiteFieldService;
    private final TurSNGenAi turSNGenAi;
    private final TurGenAiContextFactory turGenAiContextFactory;
    private final com.viglet.turing.genai.TurDefaultAgentResolver turDefaultAgentResolver;
    private final TurConfigProperties turConfigProperties;

    private final ConcurrentHashMap<String, ContentExchangeProgress> progressMap = new ConcurrentHashMap<>();
    /**
     * Sidecar map exposing the worker-pool size of an in-flight task. Kept
     * separate from {@link ContentExchangeProgress} so the record stays
     * focused on counters; only the reindex flow currently sets it.
     */
    private final ConcurrentHashMap<String, Integer> parallelismByTaskId = new ConcurrentHashMap<>();

    public record ContentExchangeProgress(int totalDocuments, int processedDocuments,
            String currentLocale, String phase, long startTimeMillis) {

        public int percentage() {
            return totalDocuments == 0 ? 0
                    : Math.min(100, (int) ((processedDocuments * 100L) / totalDocuments));
        }

        public long estimatedRemainingMillis() {
            if (processedDocuments == 0) {
                return -1;
            }
            long elapsed = System.currentTimeMillis() - startTimeMillis;
            double docsPerMs = (double) processedDocuments / elapsed;
            int remaining = totalDocuments - processedDocuments;
            return (long) (remaining / docsPerMs);
        }
    }

    public TurSNSiteContentExchangeService(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurSearchEnginePluginFactory pluginFactory,
            TurSNSiteFieldService turSNSiteFieldService,
            TurSNGenAi turSNGenAi,
            TurGenAiContextFactory turGenAiContextFactory,
            com.viglet.turing.genai.TurDefaultAgentResolver turDefaultAgentResolver,
            TurConfigProperties turConfigProperties) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.pluginFactory = pluginFactory;
        this.turSNSiteFieldService = turSNSiteFieldService;
        this.turSNGenAi = turSNGenAi;
        this.turGenAiContextFactory = turGenAiContextFactory;
        this.turDefaultAgentResolver = turDefaultAgentResolver;
        this.turConfigProperties = turConfigProperties;
    }

    public Optional<ContentExchangeProgress> getProgress(String taskId) {
        return Optional.ofNullable(progressMap.get(taskId));
    }

    public void removeProgress(String taskId) {
        progressMap.remove(taskId);
        parallelismByTaskId.remove(taskId);
    }

    /**
     * @return worker-pool size of the in-flight task, or {@code 1} when the
     *         task didn't register a parallelism (sequential flows like
     *         export/import).
     */
    public int getParallelism(String taskId) {
        Integer p = parallelismByTaskId.get(taskId);
        return p != null ? p : 1;
    }

    /**
     * Returns the in-flight RAG reindex task id for the given site, or
     * {@link Optional#empty()} when none is running. Used by the GenAI form
     * to resume the progress UI after the admin navigates away and back —
     * the task itself keeps running on a virtual thread, the frontend just
     * re-subscribes to its SSE channel.
     * <p>
     * Tasks are matched by the {@code rag-reindex-{siteId}-{timestamp}}
     * naming convention used by {@code TurSNSiteAPI.turSNSiteGenAiReindex};
     * only entries whose phase is not {@code completed} are considered
     * active.
     *
     * @since 2026.2.4
     */
    public Optional<String> findActiveReindexTask(String siteId) {
        if (siteId == null || siteId.isBlank()) {
            return Optional.empty();
        }
        String prefix = "rag-reindex-" + siteId + "-";
        return progressMap.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .filter(e -> !COMPLETED.equals(e.getValue().phase()))
                .map(Map.Entry::getKey)
                .max(java.util.Comparator.naturalOrder());
    }

    public void updatePhase(String taskId, String phase) {
        var current = progressMap.get(taskId);
        if (current != null) {
            progressMap.put(taskId, new ContentExchangeProgress(
                    current.totalDocuments(), current.processedDocuments(),
                    current.currentLocale(), phase, current.startTimeMillis()));
        } else {
            progressMap.put(taskId, new ContentExchangeProgress(
                    0, 0, "", phase, System.currentTimeMillis()));
        }
    }

    /**
     * Exports all indexed content for a site, organized by locale.
     * Structure: { "siteName": { "en_US": [ {doc1}, {doc2}, ... ], "pt_BR": [...] } }
     */
    public Map<String, Map<String, List<Map<String, Object>>>> exportContent(String siteId, String taskId) {
        return turSNSiteRepository.findById(siteId).map(turSNSite -> {
            Map<String, Map<String, List<Map<String, Object>>>> contentMap = new LinkedHashMap<>();
            List<TurSNSiteLocale> locales = turSNSiteLocaleRepository.findByTurSNSite(turSNSite);
            TurSearchEnginePlugin plugin = pluginFactory.getPluginForSite(turSNSite);
            Map<String, TurSNSiteField> siteFieldMap = turSNSiteFieldService.toMap(turSNSite);

            long totalDocs = 0;
            log.info("Exporting content for site '{}': plugin={}, locales={}, siteFields={}",
                    turSNSite.getName(), plugin.getClass().getSimpleName(), locales.size(), siteFieldMap.size());
            for (TurSNSiteLocale locale : locales) {
                long localeCount = plugin.getDocumentTotal(locale);
                log.info("  Locale '{}' (core='{}'): {} documents", locale.getLanguage(), locale.getCore(), localeCount);
                totalDocs += localeCount;
            }

            progressMap.put(taskId, new ContentExchangeProgress(
                    (int) totalDocs, 0, "", "counting", System.currentTimeMillis()));

            Map<String, List<Map<String, Object>>> localeContent = new LinkedHashMap<>();
            int processedSoFar = 0;
            long startTime = System.currentTimeMillis();

            for (TurSNSiteLocale locale : locales) {
                String localeKey = locale.getLanguage().toString();
                List<Map<String, Object>> documents = new ArrayList<>();

                progressMap.put(taskId, new ContentExchangeProgress(
                        (int) totalDocs, processedSoFar, localeKey, "exporting", startTime));

                int start = 0;
                boolean hasMore = true;
                while (hasMore) {
                    List<Map<String, Object>> page = plugin.retrieveDocumentPage(locale, siteFieldMap, start, EXPORT_PAGE_SIZE);
                    documents.addAll(page);
                    processedSoFar += page.size();

                    progressMap.put(taskId, new ContentExchangeProgress(
                            (int) totalDocs, processedSoFar, localeKey, "exporting", startTime));

                    start += EXPORT_PAGE_SIZE;
                    hasMore = page.size() == EXPORT_PAGE_SIZE;
                }
                localeContent.put(localeKey, documents);
            }
            contentMap.put(turSNSite.getName(), localeContent);

            return contentMap;
        }).orElse(Map.of());
    }

    /**
     * Imports content from the content map, indexing documents in chunks of 50.
     * Returns total documents indexed.
     */
    public int importContent(TurSNSite turSNSite,
            Map<String, Map<String, List<Map<String, Object>>>> contentMap, String taskId) {
        int totalIndexed = 0;

        for (var siteEntry : contentMap.entrySet()) {
            Map<String, List<Map<String, Object>>> localeContent = siteEntry.getValue();
            int totalDocs = localeContent.values().stream().mapToInt(List::size).sum();
            long startTime = System.currentTimeMillis();
            int processedSoFar = 0;

            progressMap.put(taskId, new ContentExchangeProgress(
                    totalDocs, 0, "", INDEXING, startTime));

            TurSearchEnginePlugin plugin = pluginFactory.getPluginForSite(turSNSite);

            for (var localeEntry : localeContent.entrySet()) {
                Locale locale = Locale.forLanguageTag(localeEntry.getKey().replace("_", "-"));
                List<Map<String, Object>> documents = localeEntry.getValue();

                progressMap.put(taskId, new ContentExchangeProgress(
                        totalDocs, processedSoFar, localeEntry.getKey(), INDEXING, startTime));

                for (int i = 0; i < documents.size(); i += IMPORT_CHUNK_SIZE) {
                    int end = Math.min(i + IMPORT_CHUNK_SIZE, documents.size());
                    List<Map<String, Object>> chunk = documents.subList(i, end);

                    totalIndexed += indexChunk(plugin, turSNSite, locale, chunk);
                    feedChunkToVectorStore(turSNSite, locale, chunk);
                    processedSoFar += chunk.size();
                    plugin.commit(turSNSite, locale);

                    progressMap.put(taskId, new ContentExchangeProgress(
                            totalDocs, processedSoFar, localeEntry.getKey(), INDEXING, startTime));
                }
            }

            progressMap.put(taskId, new ContentExchangeProgress(
                    totalDocs, processedSoFar, "", COMPLETED, startTime));
        }
        return totalIndexed;
    }

    /**
     * Indexes one chunk into the search engine, returning the number indexed.
     * Batch-sends the whole chunk in one update request (Solr/ES coalesce); if
     * the batch fails after the resilience layer's retries are exhausted, falls
     * back to per-document indexing so a single bad/slow doc doesn't drop the
     * whole chunk.
     *
     * @since 2026.3.1
     */
    private int indexChunk(TurSearchEnginePlugin plugin, TurSNSite turSNSite, Locale locale,
            List<Map<String, Object>> chunk) {
        try {
            return plugin.indexDocuments(turSNSite, locale, chunk);
        } catch (Exception batchFailure) {
            log.warn("Batch indexing of {} document(s) failed after retries; falling back to per-document indexing: {}",
                    chunk.size(), batchFailure.getMessage());
            int indexedInChunk = 0;
            for (Map<String, Object> doc : chunk) {
                try {
                    if (plugin.indexDocument(turSNSite, locale, doc)) {
                        indexedInChunk++;
                    }
                } catch (Exception perDocFailure) {
                    log.warn("Failed to index document '{}' (fallback): {}",
                            doc.getOrDefault("id", UNKNOWN), perDocFailure.getMessage());
                }
            }
            return indexedInChunk;
        }
    }

    /**
     * Feeds every document in the chunk into the RAG vector store one at a time
     * — embedding generation is an external API call per doc and has its own
     * batching semantics. Per-document failures are logged and skipped.
     *
     * @since 2026.3.1
     */
    private void feedChunkToVectorStore(TurSNSite turSNSite, Locale locale, List<Map<String, Object>> chunk) {
        for (Map<String, Object> doc : chunk) {
            try {
                turSNGenAi.addDocument(turSNSite, locale, doc);
            } catch (Exception e) {
                log.warn("Failed to index document '{}' into vector store: {}",
                        doc.getOrDefault("id", UNKNOWN), e.getMessage());
            }
        }
    }

    /**
     * Re-feeds every document already indexed in the search engine into the
     * RAG vector store, locale by locale. Used by the "Reindex All" admin
     * action — useful after RAG settings change (chunker, embedding model,
     * field partitioning) so existing content is re-embedded without
     * re-importing from sources.
     * <p>
     * Loads the site by id <i>inside</i> the transaction so lazy associations
     * like {@code turSNSiteLocales} and {@code turSNSiteFields} resolve correctly
     * when this runs on a virtual thread without OSIV. Repositories are uncached
     * (T488 / §XXVIII.3), so a plain {@code findById} returns a session-attached
     * entity.
     * <p>
     * Progress is stored under {@code taskId} via the same
     * {@link ContentExchangeProgress} channel used by export/import, so the
     * existing SSE progress endpoint streams it transparently.
     *
     * @return total documents successfully re-fed into the vector store.
     * @since 2026.2.4
     */
    @Transactional(readOnly = true)
    public int reindexVectorStore(String siteId, String taskId) {
        TurSNSite turSNSite = turSNSiteRepository.findById(siteId).orElse(null);
        if (turSNSite == null) {
            log.warn("RAG reindex skipped: site '{}' not found", siteId);
            progressMap.put(taskId, new ContentExchangeProgress(
                    0, 0, "", COMPLETED, System.currentTimeMillis()));
            return 0;
        }
        var genAi = turSNSite.getTurSNSiteGenAi();
        // T790 / §LIV.1 (Block BF) — a site explicitly set to VECTORLESS_STRUCTURED
        // opts out of embeddings; skip the reindex outright (no worker-pool spin-up,
        // no per-doc deletes) regardless of the effective agent's RAG flag.
        if (genAi != null && genAi.getKnowledgeBaseMode() != null
                && !genAi.getKnowledgeBaseMode().needsVectorSetup()) {
            log.info("RAG reindex skipped for site '{}': knowledge-base mode is VECTORLESS_STRUCTURED (no embeddings)",
                    turSNSite.getName());
            progressMap.put(taskId, new ContentExchangeProgress(
                    0, 0, "", COMPLETED, System.currentTimeMillis()));
            return 0;
        }
        // T622 — honour the global default-agent fallback so a search-only seed
        // site (no per-site agent) still reindexes into the default agent's store.
        var agent = turDefaultAgentResolver.resolveEffectiveAgent(genAi);
        if (agent == null || agent.getEnabled() != 1 || !agent.isRagEnabled()) {
            log.warn("RAG reindex skipped for site '{}': agent not configured for RAG", turSNSite.getName());
            progressMap.put(taskId, new ContentExchangeProgress(
                    0, 0, "", COMPLETED, System.currentTimeMillis()));
            return 0;
        }

        List<TurSNSiteLocale> locales = turSNSiteLocaleRepository.findByTurSNSite(turSNSite);
        TurSearchEnginePlugin plugin = pluginFactory.getPluginForSite(turSNSite);
        Map<String, TurSNSiteField> siteFieldMap = turSNSiteFieldService.toMap(turSNSite);

        long totalDocs = 0;
        for (TurSNSiteLocale locale : locales) {
            totalDocs += plugin.getDocumentTotal(locale);
        }
        log.debug("RAG reindex starting for site '{}': locales={}, totalDocuments={}",
                turSNSite.getName(), locales.size(), totalDocs);

        long startTime = System.currentTimeMillis();
        // Effectively-final snapshot for capture inside lambdas below.
        final int totalDocsForLambda = (int) totalDocs;
        progressMap.put(taskId, new ContentExchangeProgress(
                totalDocsForLambda, 0, "", "counting", startTime));

        // Pool size from `turing.genai.reindex.parallel` (default 4). Embedding
        // calls are I/O-bound (HTTP), so a small fixed pool fully utilizes the
        // embedding API without saturating typical rate limits.
        int parallelism = Math.max(1, turConfigProperties.getGenai().getReindex().getParallel());
        // Expose so the SSE / status endpoints can show "X threads" in the UI.
        parallelismByTaskId.put(taskId, parallelism);
        ExecutorService executor = Executors.newFixedThreadPool(parallelism, r -> {
            Thread t = new Thread(r);
            t.setName("rag-reindex-" + taskId.hashCode());
            t.setDaemon(true);
            return t;
        });
        AtomicInteger processedCounter = new AtomicInteger(0);
        AtomicInteger reindexedCounter = new AtomicInteger(0);
        log.debug("RAG reindex worker pool: site='{}' parallelism={}", turSNSite.getName(), parallelism);

        ReindexRun run = new ReindexRun(executor, processedCounter, reindexedCounter, taskId,
                totalDocsForLambda, startTime, turSNSite);
        try {
            for (TurSNSiteLocale siteLocale : locales) {
                reindexLocale(siteLocale, genAi, plugin, siteFieldMap, run);
            }
        } finally {
            executor.shutdown();
        }

        int reindexed = reindexedCounter.get();
        progressMap.put(taskId, new ContentExchangeProgress(
                (int) totalDocs, processedCounter.get(), "", COMPLETED, startTime));
        log.debug("RAG reindex finished for site '{}': {}/{} documents re-embedded (parallelism={})",
                turSNSite.getName(), reindexed, totalDocs, parallelism);
        return reindexed;
    }

    /**
     * Per-call state shared across every locale/page of a single
     * {@link #reindexVectorStore} run — bundled so the extracted helpers stay
     * within a sane parameter count.
     *
     * @since 2026.3.1
     */
    private record ReindexRun(ExecutorService executor, AtomicInteger processedCounter,
            AtomicInteger reindexedCounter, String taskId, int totalDocs, long startTime,
            TurSNSite site) {
    }

    /**
     * Re-feeds every indexed document of one locale into the RAG vector store,
     * paging through the search engine and draining each page before advancing
     * the cursor (bounds in-flight tasks to one page). Skips the locale when its
     * GenAI context is unavailable.
     *
     * @since 2026.3.1
     */
    private void reindexLocale(TurSNSiteLocale siteLocale, TurSNSiteGenAi genAi, TurSearchEnginePlugin plugin,
            Map<String, TurSNSiteField> siteFieldMap, ReindexRun run) {
        String localeKey = siteLocale.getLanguage().toString();
        Locale locale = siteLocale.getLanguage();
        // Resolve the GenAI context once per locale — sharing it across every
        // doc of this locale eliminates per-doc DB round-trips and VectorStore
        // re-construction. The context is locale-bound (it carries the locale's
        // vector store collection) but otherwise doc-independent.
        String collectionName = siteLocale.getCore();
        TurGenAiContext context = turGenAiContextFactory.build(genAi, collectionName);
        if (!context.isEnabled() || context.getVectorStore() == null) {
            log.warn("RAG reindex skipping locale '{}' for site '{}': context not available",
                    localeKey, run.site().getName());
            return;
        }

        progressMap.put(run.taskId(), new ContentExchangeProgress(
                run.totalDocs(), run.processedCounter().get(), localeKey, "reindexing", run.startTime()));

        int start = 0;
        boolean hasMore = true;
        while (hasMore) {
            List<Map<String, Object>> page = plugin.retrieveDocumentPage(siteLocale, siteFieldMap,
                    start, EXPORT_PAGE_SIZE);
            List<Future<?>> futures = submitReindexPage(page, locale, context, localeKey, run);
            drainReindexFutures(futures);
            start += EXPORT_PAGE_SIZE;
            hasMore = page.size() == EXPORT_PAGE_SIZE;
        }
    }

    /**
     * Submits every document of a page to the reindex executor, returning the
     * futures so the caller can wait for the page to drain. Each task re-embeds
     * one doc, counts success/processed, and publishes progress.
     *
     * @since 2026.3.1
     */
    private List<Future<?>> submitReindexPage(List<Map<String, Object>> page, Locale locale,
            TurGenAiContext context, String localeKey, ReindexRun run) {
        List<Future<?>> futures = new ArrayList<>(page.size());
        for (Map<String, Object> doc : page) {
            futures.add(run.executor().submit(() -> {
                try {
                    turSNGenAi.addDocumentToContext(run.site(), locale, context, doc);
                    run.reindexedCounter().incrementAndGet();
                } catch (Exception e) {
                    log.warn("RAG reindex failed for doc '{}' (locale={}): {}",
                            doc.getOrDefault("id", UNKNOWN), localeKey, e.getMessage());
                } finally {
                    int p = run.processedCounter().incrementAndGet();
                    progressMap.put(run.taskId(), new ContentExchangeProgress(
                            run.totalDocs(), p, localeKey, "reindexing", run.startTime()));
                }
            }));
        }
        return futures;
    }

    /**
     * Waits for every future of a page to complete, propagating interruption
     * and logging worker errors without aborting the remaining futures.
     *
     * @since 2026.3.1
     */
    private void drainReindexFutures(List<Future<?>> futures) {
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while draining RAG reindex futures", ie);
            } catch (ExecutionException ee) {
                log.warn("RAG reindex worker error: {}", ee.getCause() == null
                        ? ee.getMessage() : ee.getCause().getMessage());
            }
        }
    }
}

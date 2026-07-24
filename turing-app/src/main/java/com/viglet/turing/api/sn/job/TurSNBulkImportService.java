/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.api.sn.job;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.viglet.turing.api.sn.queue.TurSNProcessQueue;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.domain.sn.TurSNSiteRepositoryPort;

import lombok.extern.slf4j.Slf4j;

/**
 * T807 / §LV.5 (Block BG) — opt-in <strong>direct bulk-index fast path</strong>
 * that bypasses the {@code indexing.queue} JMS hop for strictly
 * <em>vectorless</em> (RAG-disabled) reindexes.
 *
 * <p>The JMS round-trip exists to offload asynchronous embedding work; a
 * vectorless / RAG-disabled site (T801) has none, so for a very large reindex
 * the queue and its per-message transaction buy nothing. When enabled, this
 * service writes documents straight to the search engine by calling the
 * consumer logic ({@link TurSNProcessQueue#processIndexingQueue}) synchronously
 * in bounded chunks — reusing the exact T803 batching, T805 schema convergence
 * and per-document enrichment the queued path uses, but skipping the queue and
 * one transaction-per-message.
 *
 * <p><b>Gated and default-off</b> ({@code turing.sn.import.bulk-direct.enabled});
 * the ordered JMS queue stays the default, authoritative path. The fast path is
 * additionally <b>vectorless-only</b>: if any targeted site has RAG enabled the
 * request is declared ineligible and the caller falls back to the queue, so a
 * site that needs async embedding is never silently starved of it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurSNBulkImportService {

    private final TurSNProcessQueue turSNProcessQueue;
    private final TurSNSiteRepositoryPort turSNSiteRepositoryPort;

    /** Master switch for the direct fast path. Default {@code false} (off). */
    @Value("${turing.sn.import.bulk-direct.enabled:false}")
    private boolean enabled;

    /**
     * Chunk size for the direct path: each chunk is processed in its own
     * {@code @Transactional} call so the transaction stays bounded for a
     * catalog-scale reindex. Default {@code 500}; {@code 0} or negative = one
     * chunk (unbounded).
     */
    @Value("${turing.sn.import.bulk-direct.batch-size:500}")
    private int batchSize;

    public TurSNBulkImportService(TurSNProcessQueue turSNProcessQueue,
            TurSNSiteRepositoryPort turSNSiteRepositoryPort) {
        this.turSNProcessQueue = turSNProcessQueue;
        this.turSNSiteRepositoryPort = turSNSiteRepositoryPort;
    }

    /**
     * Whether {@code turSNJobItems} may take the direct fast path: the feature is
     * enabled AND no targeted site has RAG enabled (async embedding would be
     * skipped otherwise). A {@code null} / empty payload is never eligible.
     */
    public boolean isEligible(TurSNJobItems turSNJobItems) {
        if (!enabled || turSNJobItems == null || turSNJobItems.size() == 0) {
            return false;
        }
        for (TurSNJobItem item : turSNJobItems) {
            if (item == null || item.getSiteNames() == null) {
                continue;
            }
            for (String siteName : item.getSiteNames()) {
                if (turSNSiteRepositoryPort.hasRagEnabledForSiteName(siteName)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Writes every job item straight to the search engine in bounded chunks,
     * bypassing JMS. Each chunk is handed to {@link TurSNProcessQueue} — whose
     * {@code @Transactional} boundary keeps the transaction per-chunk — where the
     * T803 batching + T805 convergence apply. Per-chunk fail-open: a failing chunk
     * is logged and skipped so one bad chunk never aborts the rest.
     *
     * @return the number of job items handed to the processor
     */
    public int importDirect(TurSNJobItems turSNJobItems) {
        List<TurSNJobItem> all = new ArrayList<>(turSNJobItems.getTuringDocuments());
        List<List<TurSNJobItem>> chunks = partition(all, batchSize);
        int total = chunks.size();
        int processed = 0;
        for (int i = 0; i < total; i++) {
            List<TurSNJobItem> chunk = chunks.get(i);
            try {
                TurSNJobItems chunkItems = new TurSNJobItems();
                chunk.forEach(chunkItems::add);
                turSNProcessQueue.processIndexingQueue(chunkItems);
                processed += chunk.size();
                log.info("Direct bulk import chunk {}/{} processed ({} items)",
                        i + 1, total, chunk.size());
            } catch (RuntimeException e) {
                log.warn("Direct bulk import chunk {}/{} failed ({} items): {}",
                        i + 1, total, chunk.size(), e.getMessage());
            }
        }
        return processed;
    }

    /** Splits a list into consecutive sublists of at most {@code size} (≤0 = one chunk). */
    static <T> List<List<T>> partition(List<T> list, int size) {
        if (list.isEmpty()) {
            return List.of();
        }
        if (size <= 0) {
            return List.of(list);
        }
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return parts;
    }
}

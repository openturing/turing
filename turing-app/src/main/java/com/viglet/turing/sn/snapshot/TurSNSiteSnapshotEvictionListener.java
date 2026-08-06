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
package com.viglet.turing.sn.snapshot;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.tool.TurSNSiteConfigCache;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.observability.TurSearchPipelineObservation;
import com.viglet.turing.system.cache.TurCacheEvictionScheduler;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

/**
 * JPA entity callback that wipes the {@link TurSNSiteSearchSnapshotService}
 * cache whenever any of the persistent entities the snapshot depends on is
 * persisted, updated or removed. Centralizing eviction here means new write
 * paths cannot bypass invalidation by forgetting an explicit
 * {@code @CacheEvict} — the JPA layer fires the callback regardless of which
 * service or controller saved the entity.
 *
 * <p>It also drops the T487 Semantic Navigation field-config read-model cache
 * ({@link com.viglet.turing.genai.tool.TurSNSiteConfigCache#FIELD_CONFIG_CACHE})
 * on the same events — that cache projects the same SN site + field graph, so it
 * must invalidate whenever the snapshot does.
 *
 * <p>The eviction is intentionally coarse (clear the whole snapshot cache):
 * {@code (siteId, locale)} entries cannot be selectively dropped without
 * inspecting each entity's relationship graph, and the snapshot rebuilds in a
 * single round-trip on the next search.
 *
 * <p>Hibernate (via Spring Boot's auto-configured {@code SpringBeanContainer})
 * resolves the listener through the application context, so constructor
 * injection works. The static fallback handles legacy
 * setups where JPA instantiates the listener with the no-arg constructor.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Component
public class TurSNSiteSnapshotEvictionListener {

    private static final AtomicReference<TurCacheEvictionScheduler> evictionSchedulerStatic =
            new AtomicReference<>();
    private static final AtomicReference<TurSearchPipelineObservation> pipelineObservationStatic =
            new AtomicReference<>();

    private final TurCacheEvictionScheduler evictionScheduler;
    private final TurSearchPipelineObservation pipelineObservation;

    public TurSNSiteSnapshotEvictionListener() {
        this.evictionScheduler = null;
        this.pipelineObservation = null;
    }

    @Autowired
    public TurSNSiteSnapshotEvictionListener(TurCacheEvictionScheduler evictionScheduler,
            TurSearchPipelineObservation pipelineObservation) {
        this.evictionScheduler = evictionScheduler;
        this.pipelineObservation = pipelineObservation;
        evictionSchedulerStatic.set(evictionScheduler);
        pipelineObservationStatic.set(pipelineObservation);
    }

    /**
     * T903 / §LXVIII.10 — the clears run <strong>after the writing transaction
     * commits</strong>, through the shared {@link TurCacheEvictionScheduler}.
     *
     * <p>
     * These callbacks fire at flush, so clearing inline was wrong in both directions. A
     * search on another thread arriving between the clear and the commit rebuilt the
     * snapshot from committed state — the <em>old</em> configuration — and cached it,
     * where it outlived the edit. And a read on the writing thread rebuilt it from that
     * transaction's own uncommitted state and published it to everyone, so a rollback
     * could leave the cache holding a configuration that never existed.
     * </p>
     *
     * <p>
     * That mattered more here than for the search cache T901 fixed first:
     * {@code turing.search.cache.enabled} defaults to false, while these two are on in
     * every deployment, and neither has a backstop sweep to bound how long a wrong entry
     * survives.
     * </p>
     */
    @PostPersist
    @PostUpdate
    @PostRemove
    public void onChange(Object entity) {
        TurCacheEvictionScheduler scheduler = evictionScheduler != null
                ? evictionScheduler
                : evictionSchedulerStatic.get();
        if (scheduler == null) {
            return;
        }
        scheduler.clearOnce(TurSNSiteSearchSnapshotService.CACHE_NAME);
        // T487 — the Semantic Navigation field-config read-model cache depends on
        // the same SN entity graph (site + fields), so any write that evicts the
        // search snapshot must also drop the memoized field config. Coarse,
        // whole-cache clear: it rebuilds in one round-trip on the next tool call.
        scheduler.clearOnce(TurSNSiteConfigCache.FIELD_CONFIG_CACHE);
        recordEviction();
    }

    /**
     * The eviction counter is incremented where the eviction is <em>requested</em>, not
     * where it lands. It measures how often a write invalidates the snapshot, which is a
     * property of the write; deferring it to the commit callback would also stop counting
     * the writes that roll back, and those still tell you what the workload is doing.
     */
    private void recordEviction() {
        TurSearchPipelineObservation observation = pipelineObservation != null
                ? pipelineObservation
                : pipelineObservationStatic.get();
        if (observation != null) {
            observation.recordSnapshotOutcome(TurMeterNames.OUTCOME_EVICT);
        }
    }
}

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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.tool.TurSNSiteConfigCache;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.observability.TurSearchPipelineObservation;

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
 * injection of {@link CacheManager} works. The static fallback handles legacy
 * setups where JPA instantiates the listener with the no-arg constructor.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Component
public class TurSNSiteSnapshotEvictionListener {

    private static final AtomicReference<CacheManager> cacheManagerStatic = new AtomicReference<>();
    private static final AtomicReference<TurSearchPipelineObservation> pipelineObservationStatic =
            new AtomicReference<>();

    private final CacheManager cacheManager;
    private final TurSearchPipelineObservation pipelineObservation;

    public TurSNSiteSnapshotEvictionListener() {
        this.cacheManager = null;
        this.pipelineObservation = null;
    }

    @Autowired
    public TurSNSiteSnapshotEvictionListener(CacheManager cacheManager,
            TurSearchPipelineObservation pipelineObservation) {
        this.cacheManager = cacheManager;
        this.pipelineObservation = pipelineObservation;
        cacheManagerStatic.set(cacheManager);
        pipelineObservationStatic.set(pipelineObservation);
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    public void onChange(Object entity) {
        CacheManager cm = cacheManager != null ? cacheManager : cacheManagerStatic.get();
        if (cm == null) {
            return;
        }
        Cache cache = cm.getCache(TurSNSiteSearchSnapshotService.CACHE_NAME);
        if (cache != null) {
            cache.clear();
            TurSearchPipelineObservation observation = pipelineObservation != null
                    ? pipelineObservation
                    : pipelineObservationStatic.get();
            if (observation != null) {
                observation.recordSnapshotOutcome(TurMeterNames.OUTCOME_EVICT);
            }
        }
        // T487 — the Semantic Navigation field-config read-model cache depends on
        // the same SN entity graph (site + fields), so any write that evicts the
        // search snapshot must also drop the memoized field config. Coarse,
        // whole-cache clear: it rebuilds in one round-trip on the next tool call.
        Cache fieldConfigCache = cm.getCache(TurSNSiteConfigCache.FIELD_CONFIG_CACHE);
        if (fieldConfigCache != null) {
            fieldConfigCache.clear();
        }
    }
}

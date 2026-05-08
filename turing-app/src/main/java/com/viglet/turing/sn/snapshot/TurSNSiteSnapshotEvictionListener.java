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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

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
 * <p>The eviction is intentionally coarse (clear the whole snapshot cache):
 * {@code (siteId, locale)} entries cannot be selectively dropped without
 * inspecting each entity's relationship graph, and the snapshot rebuilds in a
 * single round-trip on the next search. The trade-off matches the existing
 * {@code allEntries = true} pattern used by the per-method
 * {@code @CacheEvict} annotations on the SN repositories.
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

    private static volatile CacheManager cacheManagerStatic;
    private static volatile TurSearchPipelineObservation pipelineObservationStatic;

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
        TurSNSiteSnapshotEvictionListener.cacheManagerStatic = cacheManager;
        TurSNSiteSnapshotEvictionListener.pipelineObservationStatic = pipelineObservation;
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    public void onChange(Object entity) {
        CacheManager cm = cacheManager != null ? cacheManager : cacheManagerStatic;
        if (cm == null) {
            return;
        }
        Cache cache = cm.getCache(TurSNSiteSearchSnapshotService.CACHE_NAME);
        if (cache != null) {
            cache.clear();
            TurSearchPipelineObservation observation = pipelineObservation != null
                    ? pipelineObservation
                    : pipelineObservationStatic;
            if (observation != null) {
                observation.recordSnapshotOutcome(TurMeterNames.OUTCOME_EVICT);
            }
        }
    }
}

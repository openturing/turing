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

import com.viglet.turing.api.sn.search.TurSNSearchCacheEvictor;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

/**
 * T896 / §LXVIII.5 — JPA entity callback that drops the cached search
 * <em>answers</em> whenever configuration a query result is derived from is
 * persisted, updated or removed.
 *
 * <p>
 * T870 evicts from the indexing queue, because that is where a <em>document</em>
 * changes. But an answer is derived from more than the documents: spotlights are
 * injected at query time, synonyms rewrite the query, and the field / facet / sort
 * configuration decides what is counted and how results are ordered. All of that
 * is edited through admin controllers that never touch the queue.
 * {@code TurSNSiteSpotlightAPI} showed the shape — each write carries
 * {@code @CacheEvict(value = {"spotlight", "spotlight_term"})}: the author knew a
 * write invalidates a cache, and invalidated the two their own package owned.
 * </p>
 *
 * <p>
 * Hooking the JPA layer rather than the controllers is the point. Nothing
 * enumerates "the writes that change an answer", so any list kept in a controller
 * is a list someone has to remember to extend; the callback fires regardless of
 * which service or controller saved the entity — the same argument
 * {@link TurSNSiteSnapshotEvictionListener} makes for the snapshot.
 * </p>
 *
 * <p>
 * This is deliberately a <strong>second</strong> listener rather than more work
 * inside the snapshot one: the set of entities that change an answer is a strict
 * superset (it adds spotlights and synonyms, which the snapshot does not contain),
 * and widening the snapshot's own listener to them would clear the snapshot on
 * writes it does not depend on.
 * </p>
 *
 * <p>
 * The eviction is a whole-cache clear. Unlike the indexing path these are rare,
 * operator-driven edits, so per-site precision buys nothing — and resolving the
 * affected site would mean walking a lazy association inside a JPA callback, the
 * shape Block AC removed. The static fallback mirrors the snapshot listener's, for
 * setups where JPA instantiates the listener with the no-arg constructor.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurSNSearchCacheEvictionListener {

    private static final AtomicReference<TurSNSearchCacheEvictor> evictorStatic =
            new AtomicReference<>();

    private final TurSNSearchCacheEvictor evictor;

    public TurSNSearchCacheEvictionListener() {
        this.evictor = null;
    }

    @Autowired
    public TurSNSearchCacheEvictionListener(TurSNSearchCacheEvictor evictor) {
        this.evictor = evictor;
        evictorStatic.set(evictor);
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    public void onChange(Object entity) {
        TurSNSearchCacheEvictor target = evictor != null ? evictor : evictorStatic.get();
        if (target != null) {
            target.evictAll();
        }
    }
}

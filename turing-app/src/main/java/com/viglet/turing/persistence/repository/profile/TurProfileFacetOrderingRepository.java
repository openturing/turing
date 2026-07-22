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
package com.viglet.turing.persistence.repository.profile;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.profile.TurProfile;
import com.viglet.turing.persistence.model.profile.TurProfileFacetOrdering;
import com.viglet.turing.persistence.model.sn.TurSNSite;

/**
 * Repository for {@link TurProfileFacetOrdering} — the facet-ordering
 * configuration of a global profile for a single SN site (one per profile × site).
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
public interface TurProfileFacetOrderingRepository
        extends JpaRepository<TurProfileFacetOrdering, String> {

    String FIND_BY_PROFILE_AND_SITE_WITH_ITEMS = "turProfileFacetOrderingFindByProfileAndSiteWithItems";

    /** Uncached read (console/save paths, within a transaction — LAZY items ok). */
    Optional<TurProfileFacetOrdering> findByTurProfileAndTurSNSite(TurProfile turProfile,
            TurSNSite turSNSite);

    /**
     * Runtime read for facet ordering resolution: fetches {@code items} eagerly so the
     * result is safe to cache (a cache hit returns a detached entity, and reading the
     * LAZY {@code items} collection would otherwise throw LazyInitializationException).
     */
    @Cacheable(FIND_BY_PROFILE_AND_SITE_WITH_ITEMS)
    @Query("SELECT DISTINCT o FROM TurProfileFacetOrdering o LEFT JOIN FETCH o.items "
            + "WHERE o.turProfile = :profile AND o.turSNSite = :site")
    Optional<TurProfileFacetOrdering> findByTurProfileAndTurSNSiteWithItems(
            @Param("profile") TurProfile profile, @Param("site") TurSNSite site);

    List<TurProfileFacetOrdering> findByTurSNSite(TurSNSite turSNSite);

    List<TurProfileFacetOrdering> findByTurProfile(TurProfile turProfile);

    @CacheEvict(value = FIND_BY_PROFILE_AND_SITE_WITH_ITEMS, allEntries = true)
    @NotNull
    @Override
    <S extends TurProfileFacetOrdering> S save(@NotNull S entity);

    @CacheEvict(value = FIND_BY_PROFILE_AND_SITE_WITH_ITEMS, allEntries = true)
    @Override
    void delete(@NotNull TurProfileFacetOrdering entity);
}

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

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.profile.TurProfile;
import com.viglet.turing.persistence.model.profile.TurProfileFieldConfig;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;

/**
 * Repository for {@link TurProfileFieldConfig} — a profile's per-field facet
 * configuration overrides (one per profile × field).
 * <p>
 * {@link #findByProfileId(String)} is cached (keyed by the stable profile id
 * String, so cache hits actually happen — an entity/collection key would not):
 * it is the hot read used on every search to overlay the profile's field-facet
 * config (spec 003). Writes evict all entries. Only the flat override columns and
 * the LAZY {@code turSNSiteFieldExt}'s id are read from cached (detached) rows.
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
public interface TurProfileFieldConfigRepository
        extends JpaRepository<TurProfileFieldConfig, String> {

    String FIND_BY_PROFILE_ID = "turProfileFieldConfigFindByProfileId";

    Optional<TurProfileFieldConfig> findByTurProfileAndTurSNSiteFieldExt(TurProfile turProfile,
            TurSNSiteFieldExt turSNSiteFieldExt);

    List<TurProfileFieldConfig> findByTurProfileAndTurSNSiteFieldExtIn(TurProfile turProfile,
            Collection<TurSNSiteFieldExt> fieldExts);

    List<TurProfileFieldConfig> findByTurProfile(TurProfile turProfile);

    @Cacheable(FIND_BY_PROFILE_ID)
    @Query("SELECT c FROM TurProfileFieldConfig c WHERE c.turProfile.id = :profileId")
    List<TurProfileFieldConfig> findByProfileId(@Param("profileId") String profileId);

    @CacheEvict(value = FIND_BY_PROFILE_ID, allEntries = true)
    @NotNull
    @Override
    <S extends TurProfileFieldConfig> S save(@NotNull S entity);

    @CacheEvict(value = FIND_BY_PROFILE_ID, allEntries = true)
    @Override
    void delete(@NotNull TurProfileFieldConfig entity);
}

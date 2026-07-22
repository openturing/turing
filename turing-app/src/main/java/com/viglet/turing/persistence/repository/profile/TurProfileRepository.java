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

import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.profile.TurProfile;

/**
 * Repository for {@link TurProfile} (global, system-wide generic profiles).
 * <p>
 * The lookups are cached: profiles are tiny and change only on admin actions, but
 * are resolved on every search (runtime facet ordering). Writes evict all entries.
 * The entity is flat (no LAZY relations), so cached/detached instances are safe.
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
public interface TurProfileRepository extends JpaRepository<TurProfile, String> {

    String FIND_BY_NAME = "turProfileFindByNameIgnoreCase";
    String FIND_BY_DEFAULT = "turProfileFindByDefaultProfileTrue";
    String FIND_BY_ID = "turProfileFindById";

    @Cacheable(FIND_BY_NAME)
    Optional<TurProfile> findByNameIgnoreCase(String name);

    @Cacheable(FIND_BY_DEFAULT)
    Optional<TurProfile> findByDefaultProfileTrue();

    @Cacheable(FIND_BY_ID)
    @NotNull
    @Override
    Optional<TurProfile> findById(@NotNull String id);

    boolean existsByNameIgnoreCase(String name);

    @CacheEvict(value = { FIND_BY_NAME, FIND_BY_DEFAULT, FIND_BY_ID }, allEntries = true)
    @NotNull
    @Override
    <S extends TurProfile> S save(@NotNull S entity);

    @CacheEvict(value = { FIND_BY_NAME, FIND_BY_DEFAULT, FIND_BY_ID }, allEntries = true)
    @Override
    void delete(@NotNull TurProfile entity);
}

/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.persistence.repository.llm;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.llm.TurLLMInstanceCapability;

/**
 * T132 / §X.2 — repository for the per-LLM-instance native capability matrix.
 *
 * <p>{@code findByInstanceId} is read once per native chat turn, so it is
 * cached under {@code turNativeCapability} (as documented in the
 * {@code TurNativeProviderClient} seam design). Every write evicts the whole
 * cache so a freshly-toggled capability takes effect on the next turn — the
 * cache key is the instance id, but a coarse {@code allEntries} evict keeps the
 * convention simple and matches the other LLM repositories.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurLLMInstanceCapabilityRepository
        extends JpaRepository<TurLLMInstanceCapability, String> {

    @Cacheable("turNativeCapability")
    List<TurLLMInstanceCapability> findByInstanceId(String instanceId);

    Optional<TurLLMInstanceCapability> findByInstanceIdAndCapabilityKey(String instanceId,
            String capabilityKey);

    @CacheEvict(value = "turNativeCapability", allEntries = true)
    @NotNull
    @Override
    <S extends TurLLMInstanceCapability> S save(@NotNull S entity);

    @CacheEvict(value = "turNativeCapability", allEntries = true)
    @Override
    void delete(@NotNull TurLLMInstanceCapability entity);

    @CacheEvict(value = "turNativeCapability", allEntries = true)
    @Override
    void deleteById(@NotNull String id);
}

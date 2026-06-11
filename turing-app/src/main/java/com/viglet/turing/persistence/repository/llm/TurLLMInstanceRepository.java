/*
 * Copyright (C) 2016-2022 the original author or authors.
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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;

public interface TurLLMInstanceRepository extends JpaRepository<TurLLMInstance, String> {

	@Override
	@Cacheable("turLLMInstancefindAll")
	List<TurLLMInstance> findAll();

	@Override
	@Cacheable("turLLMInstancefindById")
	@NotNull
	Optional<TurLLMInstance> findById(@NotNull String id);

	/**
	 * Agent-import fallback: resolves a referenced LLM by title when the
	 * export's UUID doesn't match any existing row. Not cached because
	 * import writes immediately after reading.
	 *
	 * @since 2026.2.8
	 */
	Optional<TurLLMInstance> findByTitleIgnoreCase(String title);

	@CacheEvict(value = { "turLLMInstancefindAll", "turLLMInstancefindById" }, allEntries = true)
	@NotNull
	@Override
	<S extends TurLLMInstance> S save(@NotNull S entity);

	@Modifying
	@Query("delete from  TurLLMInstance li where li.id = ?1")
	@CacheEvict(value = { "turLLMInstancefindAll", "turLLMInstancefindById" }, allEntries = true)
	void delete(String id);

    /**
     * T275 / §XIV.5.1 — instances visible to a tenant: its own ({@code tenantId = :tenantId})
     * plus the platform-provided global pool ({@code tenantId IS NULL}).
     */
    @Query("select e from TurLLMInstance e where e.tenantId = :tenantId or e.tenantId is null")
    java.util.List<TurLLMInstance> findVisibleToTenant(@Param("tenantId") String tenantId);
}

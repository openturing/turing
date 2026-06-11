/*
 * Copyright (C) 2016-2022 the original author or authors. 
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

package com.viglet.turing.persistence.repository.sn;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.viglet.turing.persistence.model.sn.TurSNSite;

public interface TurSNSiteRepository extends JpaRepository<TurSNSite, String> {
	@Override
	@Cacheable("turSNSitefindAll")
	@NotNull
	List<TurSNSite> findAll(@NotNull Sort name);

	@Cacheable("turSNSitefindAByCreatedBy")
	List<TurSNSite> findByCreatedBy(Sort name, String createdBy);

	@Override
	@Cacheable("turSNSitefindById")
	@NotNull
	Optional<TurSNSite> findById(@NotNull String id);

	@Cacheable("turSNSitefindByName")
	Optional<TurSNSite> findByName(String name);

	@Cacheable("turSNSitefindByNameIgnoreCase")
	Optional<TurSNSite> findByNameIgnoreCase(String name);

	@Query("SELECT s FROM TurSNSite s WHERE s.id = :id")
	Optional<TurSNSite> findByIdNoCache(@Param("id") String id);

	/**
	 * Returns every site eagerly loaded with its locales, intended for the
	 * listing endpoint. Uses {@code LEFT JOIN FETCH} to avoid the N+1 pattern
	 * that occurs when {@code TurSNSite.turSNSiteLocales} is mapped after the
	 * Hibernate session closes. Only the locale association is fetched — all
	 * other {@code @OneToMany} collections remain lazy and untouched.
	 */
	@Cacheable("turSNSitefindAllForListing")
	@Query("SELECT DISTINCT s FROM TurSNSite s LEFT JOIN FETCH s.turSNSiteLocales ORDER BY LOWER(s.name)")
	List<TurSNSite> findAllForListing();

	// T262 / §XIV.2.6 — findAllForListingByCreatedBy (the legacy createdBy
	// pseudo-tenant filter) is retired; isolation is the Hibernate @TenantId
	// discriminator, applied automatically to findAllForListing().

	@CacheEvict(value = { "turSNSitefindAll", "turSNSitefindAByCreatedBy", "turSNSitefindById",
			"turSNSitefindByName", "turSNSitefindByNameIgnoreCase",
			"turSNSitefindAllForListing" }, allEntries = true)
	@NotNull
	@Override
	<S extends TurSNSite> S save(@NotNull S entity);

	@Override
	@CacheEvict(value = { "turSNSitefindAll", "turSNSitefindAByCreatedBy", "turSNSitefindById",
			"turSNSitefindByName", "turSNSitefindByNameIgnoreCase",
			"turSNSitefindAllForListing" }, allEntries = true)
	void delete(@NotNull TurSNSite turSNSite);
}

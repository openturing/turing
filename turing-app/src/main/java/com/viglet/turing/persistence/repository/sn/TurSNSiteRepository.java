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

import com.viglet.turing.persistence.model.sn.TurSNSite;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TurSNSiteRepository extends JpaRepository<TurSNSite, String> {

	@Cacheable("turSNSitefindAll")
	@NotNull
	List<TurSNSite> findAll(@NotNull Sort name);

	@Cacheable("turSNSitefindAByCreatedBy")
	List<TurSNSite> findByCreatedBy(Sort name, String createdBy);
	
	@Cacheable("turSNSitefindById")
	@NotNull
	Optional<TurSNSite> findById(@NotNull String id);

	@Cacheable("turSNSitefindByName")
	TurSNSite findByName(String name);

	@SuppressWarnings("unchecked")
	@CacheEvict(value = { "turSNSitefindAll", "turSNSitefindById", "turSNSitefindByName" }, allEntries = true)
	@NotNull
	TurSNSite save(@NotNull TurSNSite turSNSite);

	@CacheEvict(value = { "turSNSitefindAll", "turSNSitefindById", "turSNSitefindByName" }, allEntries = true)
	void delete(@NotNull TurSNSite turSNSite);

	@Modifying
	@Query("delete from  TurSNSite ss where ss.id = ?1")
	@CacheEvict(value = { "turSNSitefindAll", "turSNSitefindById", "turSNSitefindByName" }, allEntries = true)
	void delete(String id);
}

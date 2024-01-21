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
package com.viglet.turing.persistence.repository.sn.field;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.sn.TurSNFieldType;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TurSNSiteFieldExtRepository extends JpaRepository<TurSNSiteFieldExt, String> {

	@Cacheable("turSNSiteFieldExtfindByTurSNSite")
	List<TurSNSiteFieldExt> findByTurSNSite(Sort sort, TurSNSite turSNSite);

	@Cacheable("turSNSiteFieldExtfindByTurSNSiteAndEnabled")
	List<TurSNSiteFieldExt> findByTurSNSiteAndEnabled(TurSNSite turSNSite, int enabled);

	@Cacheable("turSNSiteFieldExtfindByTurSNSiteAndFacetAndEnabled")
	List<TurSNSiteFieldExt> findByTurSNSiteAndFacetAndEnabled(TurSNSite turSNSite, int facet, int enabled);

	@Cacheable("turSNSiteFieldExtfindByTurSNSiteAndHlAndEnabled")
	List<TurSNSiteFieldExt> findByTurSNSiteAndHlAndEnabled(TurSNSite turSNSite, int hl, int enabled);

	@Cacheable("turSNSiteFieldExtfindByTurSNSiteAndMltAndEnabled")
	List<TurSNSiteFieldExt> findByTurSNSiteAndMltAndEnabled(TurSNSite turSNSite, int mlt, int enabled);

	@Cacheable("turSNSiteFieldExtfindByTurSNSiteAndRequiredAndEnabled")
	List<TurSNSiteFieldExt> findByTurSNSiteAndRequiredAndEnabled(TurSNSite turSNSite, int required, int enabled);

	@Cacheable("turSNSiteFieldExtfindByTurSNSiteAndNlpAndEnabled")
	List<TurSNSiteFieldExt> findByTurSNSiteAndNlpAndEnabled(TurSNSite turSNSite, int nlp, int enabled);

	List<TurSNSiteFieldExt> findByTurSNSiteAndName(TurSNSite turSNSite, String name);

	boolean existsByTurSNSiteAndName(TurSNSite turSNSite, String name);

	@SuppressWarnings("unchecked")
	@CacheEvict(value = { "turSNSiteFieldExtfindByTurSNSite", "turSNSiteFieldExtfindByTurSNSiteAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndFacetAndEnabled", "turSNSiteFieldExtfindByTurSNSiteAndHlAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndMltAndEnabled", "turSNSiteFieldExtfindByTurSNSiteAndRequiredAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndNlpAndEnabled" }, allEntries = true)
	@NotNull
	TurSNSiteFieldExt save(@NotNull TurSNSiteFieldExt turSNSiteFieldExt);

	@CacheEvict(value = { "turSNSiteFieldExtfindByTurSNSite", "turSNSiteFieldExtfindByTurSNSiteAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndFacetAndEnabled", "turSNSiteFieldExtfindByTurSNSiteAndHlAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndMltAndEnabled", "turSNSiteFieldExtfindByTurSNSiteAndRequiredAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndNlpAndEnabled" }, allEntries = true)
	void delete(@NotNull TurSNSiteFieldExt turSNSiteFieldExt);

	@Modifying
	@Query("delete from TurSNSiteFieldExt ssfe where ssfe.id = ?1")
	@CacheEvict(value = { "turSNSiteFieldExtfindByTurSNSite", "turSNSiteFieldExtfindByTurSNSiteAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndFacetAndEnabled", "turSNSiteFieldExtfindByTurSNSiteAndHlAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndMltAndEnabled", "turSNSiteFieldExtfindByTurSNSiteAndRequiredAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndNlpAndEnabled" }, allEntries = true)
	void delete(String turSnSiteFieldId);

	@Modifying
	@Query("delete from TurSNSiteFieldExt ssfe where ssfe.turSNSite= ?1 and ssfe.snType = ?2")
	@CacheEvict(value = { "turSNSiteFieldExtfindByTurSNSite", "turSNSiteFieldExtfindByTurSNSiteAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndFacetAndEnabled", "turSNSiteFieldExtfindByTurSNSiteAndHlAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndMltAndEnabled", "turSNSiteFieldExtfindByTurSNSiteAndRequiredAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndNlpAndEnabled" }, allEntries = true)
	void deleteByTurSNSiteAndSnType(TurSNSite turSNSite, TurSNFieldType turSNFieldType);
}

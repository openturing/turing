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

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.sn.TurSNFieldType;

public interface TurSNSiteFieldExtRepository extends JpaRepository<TurSNSiteFieldExt, String> {

	List<TurSNSiteFieldExt> findByTurSNSite(Sort sort, TurSNSite turSNSite);

	List<TurSNSiteFieldExt> findByTurSNSiteAndName(TurSNSite turSNSite, String name);

	List<TurSNSiteFieldExt> findByTurSNSiteAndEnabled(TurSNSite turSNSite, int enabled);

	List<TurSNSiteFieldExt> findByTurSNSiteAndFacetAndEnabled(TurSNSite turSNSite, int facet, int enabled);

	List<TurSNSiteFieldExt> findByTurSNSiteAndNameAndFacetAndEnabled(TurSNSite turSNSite, String name, int facet,
			int enabled);

	List<TurSNSiteFieldExt> findByTurSNSiteAndFacetAndEnabledOrderByFacetPosition(TurSNSite turSNSite, int facet,
			int enabled);

	List<TurSNSiteFieldExt> findByTurSNSiteAndFacetAndEnabledAndType(TurSNSite turSNSite, int facet, int enabled,
			TurSEFieldType type);

	List<TurSNSiteFieldExt> findByTurSNSiteAndHlAndEnabled(TurSNSite turSNSite, int hl, int enabled);

	List<TurSNSiteFieldExt> findByTurSNSiteAndMltAndEnabled(TurSNSite turSNSite, int mlt, int enabled);

	List<TurSNSiteFieldExt> findByTurSNSiteAndRequiredAndEnabled(TurSNSite turSNSite, int required, int enabled);

	boolean existsByTurSNSiteAndName(TurSNSite turSNSite, String name);

	@Query("SELECT MAX(t.facetPosition) FROM TurSNSiteFieldExt t where t.turSNSite = ?1")
	Optional<Integer> findMaxFacetPosition(TurSNSite turSNSite);

	/**
	 * T899 / §LXVIII.7 — the two {@code @Modifying} delete queries that used to live
	 * here are gone on purpose.
	 *
	 * <p>
	 * A JPQL bulk delete issues SQL without loading anything, so the persistence
	 * context manages no entity and no {@code @PostRemove} fires. This entity carries
	 * two callbacks that a delete must run — the snapshot eviction listener and the
	 * T896 search-cache one — so deleting a field through them left a stale field
	 * configuration in both caches. The admin delete endpoint had already loaded the
	 * entity before calling {@code delete(id)}, which is what made the batched form
	 * pure loss.
	 * </p>
	 *
	 * <p>
	 * Use the inherited {@code delete(TurSNSiteFieldExt)} / {@code deleteAll(Iterable)}
	 * instead, and {@link #findByTurSNSiteAndSnType} to load what is being removed.
	 * {@code TurSNBulkDeleteConventionsTest} fails if a bulk delete is reintroduced on
	 * an entity whose answers are cached.
	 * </p>
	 */
	List<TurSNSiteFieldExt> findByTurSNSiteAndSnType(TurSNSite turSNSite,
			TurSNFieldType turSNFieldType);
}

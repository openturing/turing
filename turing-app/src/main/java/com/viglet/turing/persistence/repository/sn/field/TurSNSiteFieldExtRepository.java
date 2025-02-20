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

import com.viglet.turing.commons.se.field.TurSEFieldType;
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
import java.util.Optional;

public interface TurSNSiteFieldExtRepository extends JpaRepository<TurSNSiteFieldExt, String> {

	String FIND_BY_TUR_SN_SITE = "turSNSiteFieldExtFindByTurSNSite";
	String FIND_BY_TUR_SN_SITE_AND_ENABLED = "turSNSiteFieldExtFindByTurSNSiteAndEnabled";
	String FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED = "turSNSiteFieldExtFindByTurSNSiteAndFacetAndEnabled";
	String FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED_ORDER_BY_FACET_POSITION = "findByTurSNSiteAndFacetAndEnabledOrderByFacetPosition";
	String FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED_AND_TYPE = "findByTurSNSiteAndFacetAndEnabledAndType";
	String FIND_BY_TUR_SN_SITE_AND_HL_AND_ENABLED = "turSNSiteFieldExtFindByTurSNSiteAndHlAndEnabled";
	String FIND_BY_TUR_SN_SITE_AND_MLT_AND_ENABLED = "turSNSiteFieldExtFindByTurSNSiteAndMltAndEnabled";
	String FIND_BY_TUR_SN_SITE_AND_REQUIRED_AND_ENABLED = "turSNSiteFieldExtFindByTurSNSiteAndRequiredAndEnabled";
	String FIND_BY_TUR_SN_SITE_AND_NAME_AND_FACET_AND_ENABLED = "turSNSiteFieldExtFindByTurSNSiteAndNameAndFacetAndEnabled";

	@Cacheable(FIND_BY_TUR_SN_SITE)
	List<TurSNSiteFieldExt> findByTurSNSite(Sort sort, TurSNSite turSNSite);

	@Cacheable(FIND_BY_TUR_SN_SITE_AND_ENABLED)
	List<TurSNSiteFieldExt> findByTurSNSiteAndEnabled(TurSNSite turSNSite, int enabled);

	@Cacheable(FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED)
	List<TurSNSiteFieldExt> findByTurSNSiteAndFacetAndEnabled(TurSNSite turSNSite, int facet, int enabled);
	@Cacheable(FIND_BY_TUR_SN_SITE_AND_NAME_AND_FACET_AND_ENABLED)
	List<TurSNSiteFieldExt> findByTurSNSiteAndNameAndFacetAndEnabled(TurSNSite turSNSite, String name, int facet,
																	 int enabled);

	@Cacheable(FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED_ORDER_BY_FACET_POSITION)
	List<TurSNSiteFieldExt> findByTurSNSiteAndFacetAndEnabledOrderByFacetPosition(TurSNSite turSNSite, int facet,
																				  int enabled);

	@Cacheable(FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED_AND_TYPE)
	List<TurSNSiteFieldExt> findByTurSNSiteAndFacetAndEnabledAndType(TurSNSite turSNSite, int facet, int enabled,
																	 TurSEFieldType type);

	@Cacheable(FIND_BY_TUR_SN_SITE_AND_HL_AND_ENABLED)
	List<TurSNSiteFieldExt> findByTurSNSiteAndHlAndEnabled(TurSNSite turSNSite, int hl, int enabled);

	@Cacheable(FIND_BY_TUR_SN_SITE_AND_MLT_AND_ENABLED)
	List<TurSNSiteFieldExt> findByTurSNSiteAndMltAndEnabled(TurSNSite turSNSite, int mlt, int enabled);

	@Cacheable(FIND_BY_TUR_SN_SITE_AND_REQUIRED_AND_ENABLED)
	List<TurSNSiteFieldExt> findByTurSNSiteAndRequiredAndEnabled(TurSNSite turSNSite, int required, int enabled);

	boolean existsByTurSNSiteAndName(TurSNSite turSNSite, String name);

	@Query("SELECT MAX(t.facetPosition) FROM TurSNSiteFieldExt t")
	Optional<Integer> findMaxFacetPosition();

	@CacheEvict(value = {FIND_BY_TUR_SN_SITE, FIND_BY_TUR_SN_SITE_AND_ENABLED,
			FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED, FIND_BY_TUR_SN_SITE_AND_HL_AND_ENABLED,
			FIND_BY_TUR_SN_SITE_AND_MLT_AND_ENABLED, FIND_BY_TUR_SN_SITE_AND_REQUIRED_AND_ENABLED,
			FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED_AND_TYPE,
			FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED_ORDER_BY_FACET_POSITION,
			FIND_BY_TUR_SN_SITE_AND_NAME_AND_FACET_AND_ENABLED}, allEntries = true)
	@NotNull
	TurSNSiteFieldExt save(@NotNull TurSNSiteFieldExt turSNSiteFieldExt);

	@CacheEvict(value = {FIND_BY_TUR_SN_SITE, FIND_BY_TUR_SN_SITE_AND_ENABLED,
			FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED, FIND_BY_TUR_SN_SITE_AND_HL_AND_ENABLED,
			FIND_BY_TUR_SN_SITE_AND_MLT_AND_ENABLED, FIND_BY_TUR_SN_SITE_AND_REQUIRED_AND_ENABLED,
			FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED_AND_TYPE,
			FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED_ORDER_BY_FACET_POSITION,
			FIND_BY_TUR_SN_SITE_AND_NAME_AND_FACET_AND_ENABLED}, allEntries = true)
	void delete(@NotNull TurSNSiteFieldExt turSNSiteFieldExt);

	@Modifying
	@Query("delete from TurSNSiteFieldExt ssfe where ssfe.id = ?1")
	@CacheEvict(value = {FIND_BY_TUR_SN_SITE, FIND_BY_TUR_SN_SITE_AND_ENABLED,
			FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED, FIND_BY_TUR_SN_SITE_AND_HL_AND_ENABLED,
			FIND_BY_TUR_SN_SITE_AND_MLT_AND_ENABLED, FIND_BY_TUR_SN_SITE_AND_REQUIRED_AND_ENABLED,
			FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED_AND_TYPE,
			FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED_ORDER_BY_FACET_POSITION,
			FIND_BY_TUR_SN_SITE_AND_NAME_AND_FACET_AND_ENABLED}, allEntries = true)
	void delete(String turSnSiteFieldId);

	@Modifying
	@Query("delete from TurSNSiteFieldExt ssfe where ssfe.turSNSite= ?1 and ssfe.snType = ?2")
	@CacheEvict(value = {FIND_BY_TUR_SN_SITE, FIND_BY_TUR_SN_SITE_AND_ENABLED,
			FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED, FIND_BY_TUR_SN_SITE_AND_HL_AND_ENABLED,
			FIND_BY_TUR_SN_SITE_AND_MLT_AND_ENABLED, FIND_BY_TUR_SN_SITE_AND_REQUIRED_AND_ENABLED,
			FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED_AND_TYPE,
			FIND_BY_TUR_SN_SITE_AND_FACET_AND_ENABLED_ORDER_BY_FACET_POSITION,
			FIND_BY_TUR_SN_SITE_AND_NAME_AND_FACET_AND_ENABLED}, allEntries = true)
	void deleteByTurSNSiteAndSnType(TurSNSite turSNSite, TurSNFieldType turSNFieldType);
}

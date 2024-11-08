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

package com.viglet.turing.persistence.repository.sn.spotlight;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;
import java.util.Locale;
/**
 * @author Alexandre Oliveira
 * @since 0.3.4
 */
public interface TurSNSiteSpotlightRepository extends JpaRepository<TurSNSiteSpotlight, String> {

	String FIND_BY_UNMANAGED_ID_AND_TUR_SN_SITE_AND_LANGUAGE = "turSNSiteSpotlightFindByUnmanagedIdAndTurSNSiteAndLanguage";
	String FIND_BY_TUR_SN_SITE_AND_LANGUAGE = "turSNSiteSpotlightFindByTurSNSiteAndLanguage";
	String FIND_BY_TUR_SN_SITE = "turSNSiteSpotlightFindByTurSNSite";
	String FIND_BY_PROVIDER = "turSNSiteSpotlightFindByProvider";

	@Cacheable(FIND_BY_UNMANAGED_ID_AND_TUR_SN_SITE_AND_LANGUAGE)
	Set<TurSNSiteSpotlight> findByUnmanagedIdAndTurSNSiteAndLanguage(String unmanagedId, TurSNSite turSNSite,
			Locale language);

	@Cacheable(FIND_BY_TUR_SN_SITE_AND_LANGUAGE)
	List<TurSNSiteSpotlight> findByTurSNSiteAndLanguage(TurSNSite turSNSite, Locale language);

	@Cacheable(FIND_BY_TUR_SN_SITE)
	List<TurSNSiteSpotlight> findByTurSNSite(Sort orders, TurSNSite turSNSite);

	@Cacheable(FIND_BY_PROVIDER)
	Set<TurSNSiteSpotlight> findByProvider(String provider);

	@CacheEvict(value = {FIND_BY_UNMANAGED_ID_AND_TUR_SN_SITE_AND_LANGUAGE, FIND_BY_TUR_SN_SITE_AND_LANGUAGE,
			FIND_BY_TUR_SN_SITE, FIND_BY_PROVIDER}, allEntries = true)
	@NotNull
	TurSNSiteSpotlight save(@NotNull TurSNSiteSpotlight turSNSiteSpotlight);

	@CacheEvict(value = {FIND_BY_UNMANAGED_ID_AND_TUR_SN_SITE_AND_LANGUAGE, FIND_BY_TUR_SN_SITE_AND_LANGUAGE,
			FIND_BY_TUR_SN_SITE, FIND_BY_PROVIDER}, allEntries = true)
	void delete(@NotNull TurSNSiteSpotlight turSNSiteSpotlight);

}

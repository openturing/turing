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
package com.viglet.turing.sn.spotlight;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 *
 */
@Component
public class TurSpotlightCache {
	private final TurSNSiteRepository turSNSiteRepository;
	private final TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;

	@Inject
	public TurSpotlightCache(TurSNSiteRepository turSNSiteRepository,
							 TurSNSiteSpotlightRepository turSNSiteSpotlightRepository) {
		this.turSNSiteRepository = turSNSiteRepository;
		this.turSNSiteSpotlightRepository = turSNSiteSpotlightRepository;
	}


	@Cacheable(value = "spotlight", sync = true)
	public List<TurSNSiteSpotlight> findSpotlightBySNSiteAndLanguage(String snSite, Locale language) {
		// Firstly, find the SNSite by its name.
		// Then, find the SNSiteSpotlight by the found SNSite and the language.
		// Return the result or an empty list if the SNSite is not found.
		return turSNSiteRepository.findByName(snSite).map( turSNSite -> turSNSiteSpotlightRepository
				.findByTurSNSiteAndLanguage(turSNSite, language)).orElse(Collections.emptyList());

	}
	
	@Cacheable(value = "spotlight_term", sync = true)
	public List<TurSNSpotlightTermCacheBean> findTermsBySNSiteAndLanguage(String snSite, Locale language) {

		// Firstly, get the SNSiteSpotlight by the SNSite and the language.
		// Treat the result list as a stream
		// For each SNSiteSpotlight, get the SNSiteSpotlightTerms.
		
		return  this.findSpotlightBySNSiteAndLanguage(snSite, language).stream().flatMap(turSNSiteSpotlight ->
				turSNSiteSpotlight.getTurSNSiteSpotlightTerms().stream()).map(turSNSiteSpotlightTerm ->
				new TurSNSpotlightTermCacheBean(
				turSNSiteSpotlightTerm.getName(), turSNSiteSpotlightTerm.getTurSNSiteSpotlight()))
				.toList();
	}
	
	
}

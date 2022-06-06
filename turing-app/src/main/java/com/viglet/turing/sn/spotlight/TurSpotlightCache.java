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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 *
 */
@Component
public class TurSpotlightCache {
	@Autowired
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;

	@Cacheable(value = "spotlight", sync = true)
	public List<TurSNSiteSpotlight> findSpotlightBySNSiteAndLanguage(String snSite, String language) {
		return turSNSiteSpotlightRepository
				.findByTurSNSiteAndLanguage(turSNSiteRepository.findByName(snSite), language);
	}
	
	@Cacheable(value = "spotlight_term", sync = true)
	public List<TurSNSpotlightTermCacheBean> findTermsBySNSiteAndLanguage(String snSite, String language) {
		List<TurSNSiteSpotlight> turSNSiteSpotlights = this.findSpotlightBySNSiteAndLanguage(snSite, language);
		List<TurSNSpotlightTermCacheBean> terms = new ArrayList<>();
		for (TurSNSiteSpotlight turSNSiteSpotlight : turSNSiteSpotlights) {
			for (TurSNSiteSpotlightTerm turSNSiteSpotlightTerm : turSNSiteSpotlight.getTurSNSiteSpotlightTerms()) {
				TurSNSpotlightTermCacheBean turSNSpotlightTermCacheBean = new TurSNSpotlightTermCacheBean(
						turSNSiteSpotlightTerm.getName(), turSNSiteSpotlightTerm.getTurSNSiteSpotlight());
				terms.add(turSNSpotlightTermCacheBean);
			}
		}
		return terms;
	}
	
	
}

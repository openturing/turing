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

import java.util.List;
import java.util.Locale;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 *
 */
@Component
public class TurSpotlightCache {

	/**
	 * T904 / §LXVIII.11 — the two spotlight cache names, declared where the cache is so
	 * an evictor references the same constant the reader does rather than repeating a
	 * string literal at every write path.
	 */
	public static final String SPOTLIGHT_CACHE = "spotlight";
	public static final String SPOTLIGHT_TERM_CACHE = "spotlight_term";

	private final TurSpotlightService turSpotlightService;
	public TurSpotlightCache(TurSpotlightService turSpotlightService) {
        this.turSpotlightService = turSpotlightService;
    }

	@Cacheable(value = SPOTLIGHT_TERM_CACHE, sync = true)
	public List<TurSNSpotlightTermCacheBean> findTermsBySNSiteAndLanguage(String snSite, Locale language) {
		return turSpotlightService.findSpotlightBySNSiteAndLanguage(snSite, language).stream()
				.flatMap(turSNSiteSpotlight -> turSNSiteSpotlight.getTurSNSiteSpotlightTerms().stream())
				.map(turSNSiteSpotlightTerm -> new TurSNSpotlightTermCacheBean(
						turSNSiteSpotlightTerm.getName(), turSNSiteSpotlightTerm.getTurSNSiteSpotlight()))
				.toList();
	}

}

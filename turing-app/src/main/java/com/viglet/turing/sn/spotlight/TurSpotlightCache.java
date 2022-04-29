/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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

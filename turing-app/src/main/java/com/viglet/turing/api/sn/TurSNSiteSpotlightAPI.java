/*
 * Copyright (C) 2016-2021 the original author or authors. 
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

package com.viglet.turing.api.sn;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

/**
 * @author Alexandre Oliveira
 * @since 0.3.4
 */

@RestController
@RequestMapping("/api/sn/{snSiteId}/spotlight")
@Tag(name = "Semantic Navigation Spotlight", description = "Semantic Navigation Spotlight API")
public class TurSNSiteSpotlightAPI {

	@Autowired
	TurSNSiteRepository turSNSiteRepository;
	@Autowired
	TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;

	@Operation(summary = "Semantic Navigation Site Spotlight List")
	@GetMapping
	public List<TurSNSiteSpotlight> turSNSiteSpotlightList(@PathVariable String snSiteId) {
		return turSNSiteRepository.findById(snSiteId).map(this.turSNSiteSpotlightRepository::findByTurSNSite)
				.orElse(new ArrayList<>());
	}

	@Operation(summary = "Show a Semantic Navigation Site Spotlight")
	@GetMapping("/{id}")
	public TurSNSiteSpotlight turSNSiteFieldExtGet(@PathVariable String snSiteId, @PathVariable String id) {
		return turSNSiteSpotlightRepository.findById(id).orElse(new TurSNSiteSpotlight());
	}
}
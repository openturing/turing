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

package com.viglet.turing.api.sn.console;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */

@RestController
@RequestMapping("/api/sn/{snSiteId}/locale")
@Tag(name = "Semantic Navigation Locale", description = "Semantic Navigation Locale API")
public class TurSNSiteLocaleAPI {

	@Autowired
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurSNSiteLocaleRepository turSNSiteLocaleRepository;

	@Operation(summary = "Semantic Navigation Site Locale List")
	@GetMapping
	public List<TurSNSiteLocale> turSNSiteLocaleList(@PathVariable String snSiteId) {
		return turSNSiteRepository.findById(snSiteId).map(this.turSNSiteLocaleRepository::findByTurSNSite)
				.orElse(new ArrayList<>());
	}

	@Operation(summary = "Show a Semantic Navigation Site Locale")
	@GetMapping("/{id}")
	public TurSNSiteLocale turSNSiteFieldExtGet(@PathVariable String snSiteId, @PathVariable String id) {
		return turSNSiteLocaleRepository.findById(id).orElse(new TurSNSiteLocale());
	}
}
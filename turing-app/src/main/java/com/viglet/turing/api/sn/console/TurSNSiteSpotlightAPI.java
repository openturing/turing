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

package com.viglet.turing.api.sn.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightDocumentRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;

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
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
	@Autowired
	private TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository;
	@Autowired
	private TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository;

	@Operation(summary = "Semantic Navigation Site Spotlight List")
	@GetMapping
	public List<TurSNSiteSpotlight> turSNSiteSpotlightList(@PathVariable String snSiteId) {
		return turSNSiteRepository.findById(snSiteId).map(this.turSNSiteSpotlightRepository::findByTurSNSite)
				.orElse(new ArrayList<>());
	}

	@Operation(summary = "Show a Semantic Navigation Site Spotlight")
	@GetMapping("/{id}")
	public TurSNSiteSpotlight turSNSiteFieldExtGet(@PathVariable String snSiteId, @PathVariable String id) {

		Optional<TurSNSiteSpotlight> turSNSiteSpotlight = turSNSiteSpotlightRepository.findById(id);
		if (turSNSiteSpotlight.isPresent()) {
			turSNSiteSpotlight.get().setTurSNSiteSpotlightDocuments(
					turSNSiteSpotlightDocumentRepository.findByTurSNSiteSpotlight(turSNSiteSpotlight.get()));
			turSNSiteSpotlight.get().setTurSNSiteSpotlightTerms(
					turSNSiteSpotlightTermRepository.findByTurSNSiteSpotlight(turSNSiteSpotlight.get()));
			return turSNSiteSpotlight.get();
		}

		return new TurSNSiteSpotlight();
	}

	@Operation(summary = "Update a Semantic Navigation Site Spotlight")
	@PutMapping("/{id}")
	public TurSNSiteSpotlight turSNSiteSpotlightUpdate(@PathVariable String id,
			@RequestBody TurSNSiteSpotlight turSNSiteSpotlight) {

		return turSNSiteSpotlightRepository.findById(id).map(turSNSiteSpotlightEdit -> {
			turSNSiteSpotlightEdit.setDescription(turSNSiteSpotlight.getDescription());
			turSNSiteSpotlightEdit.setLanguage(turSNSiteSpotlight.getLanguage());
			turSNSiteSpotlightEdit.setModificationDate(turSNSiteSpotlight.getModificationDate());
			turSNSiteSpotlightEdit.setName(turSNSiteSpotlight.getName());
			turSNSiteSpotlightEdit.setProvider(turSNSiteSpotlight.getProvider());
			turSNSiteSpotlightEdit.setTurSNSite(turSNSiteSpotlight.getTurSNSite());
			turSNSiteSpotlightRepository.save(turSNSiteSpotlightEdit);
			return turSNSiteSpotlightEdit;
		}).orElse(new TurSNSiteSpotlight());
	}

	@Transactional
	@Operation(summary = "Delete a Semantic Navigation Site Spotlight")
	@DeleteMapping("/{id}")
	@CacheEvict(value = { "spotlight", "spotlight_term" }, allEntries = true)
	public boolean turSNSiteDelete(@PathVariable String id) {
		turSNSiteSpotlightRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Semantic Navigation Site Spotlight")
	@PostMapping
	public TurSNSiteSpotlight turSNSiteSpotlighAdd(@RequestBody TurSNSiteSpotlight turSNSiteSpotlight) {
		turSNSiteSpotlightRepository.save(turSNSiteSpotlight);
		return turSNSiteSpotlight;
	}

	@Operation(summary = "Semantic Navigation Site Spotlight structure")
	@GetMapping("structure")
	public TurSNSiteSpotlight turSNSiteSpotlightStructure(@PathVariable String snSiteId) {
		return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
			TurSNSiteSpotlight turSNSiteSpotlight = new TurSNSiteSpotlight();
			turSNSiteSpotlight.setTurSNSite(turSNSite);
			return turSNSiteSpotlight;
		}).orElse(new TurSNSiteSpotlight());
	}
}
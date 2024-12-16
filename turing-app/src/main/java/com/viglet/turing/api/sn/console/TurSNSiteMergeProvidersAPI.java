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
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersFieldRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */

@RestController
@RequestMapping("/api/sn/{snSiteId}/merge")
@Tag(name = "Semantic Navigation Merge Providers", description = "Semantic Navigation Merge API")
public class TurSNSiteMergeProvidersAPI {
	private static final String DEFAULT_LANGUAGE = "en_US";
	@Autowired
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurSNSiteMergeProvidersRepository turSNSiteMergeRepository;
	@Autowired
	private TurSNSiteMergeProvidersFieldRepository turSNSiteMergeFieldRepository;

	@Operation(summary = "Semantic Navigation Site Merge List")
	@GetMapping
	public List<TurSNSiteMergeProviders> turSNSiteMergeList(@PathVariable String snSiteId) {
		return turSNSiteRepository.findById(snSiteId).map(this.turSNSiteMergeRepository::findByTurSNSite)
				.orElse(new ArrayList<>());
	}

	@Operation(summary = "Show a Semantic Navigation Site Merge Providers")
	@GetMapping("/{id}")
	public TurSNSiteMergeProviders turSNSiteFieldExtGet(@PathVariable String snSiteId, @PathVariable String id) {
		Optional<TurSNSiteMergeProviders> turSNSiteMergeOptional = turSNSiteMergeRepository.findById(id);
		if (turSNSiteMergeOptional.isPresent()) {
			TurSNSiteMergeProviders turSNSiteMerge = turSNSiteMergeOptional.get();
			turSNSiteMerge.setOverwrittenFields(turSNSiteMergeFieldRepository.findByTurSNSiteMergeProviders(turSNSiteMerge));
			return turSNSiteMerge;
		} else {
			return new TurSNSiteMergeProviders();
		}
	}

	@Operation(summary = "Update a Semantic Navigation Site Merge Providers")
	@PutMapping("/{id}")
	public TurSNSiteMergeProviders turSNSiteMergeUpdate(@PathVariable String id, @RequestBody TurSNSiteMergeProviders turSNSiteMerge) {
		return this.turSNSiteMergeRepository.findById(id).map(turSNSiteMergeEdit -> {
			turSNSiteMergeEdit.setProviderFrom(turSNSiteMerge.getProviderFrom());
			turSNSiteMergeEdit.setProviderTo(turSNSiteMerge.getProviderTo());
			turSNSiteMergeEdit.setRelationFrom(turSNSiteMerge.getRelationFrom());
			turSNSiteMergeEdit.setRelationTo(turSNSiteMerge.getRelationTo());
			turSNSiteMergeEdit.setDescription(turSNSiteMerge.getDescription());
			turSNSiteMergeEdit.setLocale(turSNSiteMerge.getLocale());
			turSNSiteMergeEdit.setTurSNSite(turSNSiteMerge.getTurSNSite());
			turSNSiteMergeEdit.getOverwrittenFields().clear();
			turSNSiteMergeRepository.save(turSNSiteMergeEdit);

			turSNSiteMerge.getOverwrittenFields().forEach(field -> {
				field.setTurSNSiteMergeProviders(turSNSiteMergeEdit);
				turSNSiteMergeFieldRepository.save(field);
			});
			return turSNSiteMergeEdit;
		}).orElse(new TurSNSiteMergeProviders());

	}

	@Transactional
	@Operation(summary = "Delete a Semantic Navigation Site Merge Providers")
	@DeleteMapping("/{id}")
	public boolean turSNSiteMergeDelete(@PathVariable String id) {
		turSNSiteMergeRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Semantic Navigation Site Merge Providers")
	@PostMapping
	public TurSNSiteMergeProviders turSNSiteMergeAdd(@RequestBody TurSNSiteMergeProviders turSNSiteMerge) {
		turSNSiteMergeRepository.save(turSNSiteMerge);
		
		turSNSiteMerge.getOverwrittenFields().forEach(field -> {
			field.setTurSNSiteMergeProviders(turSNSiteMerge);
			turSNSiteMergeFieldRepository.save(field);
		});
		return turSNSiteMerge;
	}

	@Operation(summary = "Semantic Navigation Site Merge structure")
	@GetMapping("structure")
	public TurSNSiteMergeProviders turSNSiteMergeStructure(@PathVariable String snSiteId) {
		return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
			TurSNSiteMergeProviders turSNSiteMerge = new TurSNSiteMergeProviders();
			turSNSiteMerge.setLocale(DEFAULT_LANGUAGE);
			turSNSiteMerge.setTurSNSite(turSNSite);
			return turSNSiteMerge;
		}).orElse(new TurSNSiteMergeProviders());
	}
}
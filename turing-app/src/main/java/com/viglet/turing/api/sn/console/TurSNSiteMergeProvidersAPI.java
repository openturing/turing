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

package com.viglet.turing.api.sn.console;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersFieldRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */

@RestController
@RequestMapping("/api/sn/{snSiteId}/merge")
@Tag(name = "Semantic Navigation Merge Providers", description = "Semantic Navigation Merge API")
public class TurSNSiteMergeProvidersAPI {
	private static final String DEFAULT_LANGUAGE = "en_US";
	private final TurSNSiteRepository turSNSiteRepository;
	private final TurSNSiteMergeProvidersRepository turSNSiteMergeRepository;
	private final TurSNSiteMergeProvidersFieldRepository turSNSiteMergeFieldRepository;
	@Inject
	public TurSNSiteMergeProvidersAPI(TurSNSiteRepository turSNSiteRepository,
									  TurSNSiteMergeProvidersRepository turSNSiteMergeRepository,
									  TurSNSiteMergeProvidersFieldRepository turSNSiteMergeFieldRepository) {
		this.turSNSiteRepository = turSNSiteRepository;
		this.turSNSiteMergeRepository = turSNSiteMergeRepository;
		this.turSNSiteMergeFieldRepository = turSNSiteMergeFieldRepository;
	}

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
		turSNSiteMergeRepository.deleteById(id);
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
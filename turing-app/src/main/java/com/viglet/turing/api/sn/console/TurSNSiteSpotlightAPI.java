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

import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightDocumentRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
	public TurSNSiteSpotlight turSNSiteSpotlightGet(@PathVariable String snSiteId, @PathVariable String id) {

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
	public boolean turSNSiteSpotlighDelete(@PathVariable String id) {
		turSNSiteSpotlightRepository.deleteById(id);
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
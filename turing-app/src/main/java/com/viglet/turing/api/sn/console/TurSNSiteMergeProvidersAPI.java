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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.dto.sn.merge.TurSNSiteMergeProvidersDto;
import com.viglet.turing.persistence.mapper.sn.merge.TurSNSiteMergeProvidersMapper;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersFieldRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */

@RestController
@RequestMapping("/api/sn/{ignoredSnSiteId}/merge")
@Tag(name = "Semantic Navigation Merge Providers", description = "Semantic Navigation Merge API")
public class TurSNSiteMergeProvidersAPI {
	private static final Locale DEFAULT_LANGUAGE = Locale.US;
	private final TurSNSiteRepository turSNSiteRepository;
	private final TurSNSiteMergeProvidersRepository turSNSiteMergeRepository;
	private final TurSNSiteMergeProvidersFieldRepository turSNSiteMergeFieldRepository;
	private final TurSNSiteMergeProvidersMapper turSNSiteMergeProvidersMapper;

	public TurSNSiteMergeProvidersAPI(TurSNSiteRepository turSNSiteRepository,
			TurSNSiteMergeProvidersRepository turSNSiteMergeRepository,
			TurSNSiteMergeProvidersFieldRepository turSNSiteMergeFieldRepository,
			TurSNSiteMergeProvidersMapper turSNSiteMergeProvidersMapper) {
		this.turSNSiteRepository = turSNSiteRepository;
		this.turSNSiteMergeRepository = turSNSiteMergeRepository;
		this.turSNSiteMergeFieldRepository = turSNSiteMergeFieldRepository;
		this.turSNSiteMergeProvidersMapper = turSNSiteMergeProvidersMapper;
	}

	@Operation(summary = "Semantic Navigation Site Merge List")
	@GetMapping
	public List<TurSNSiteMergeProvidersDto> turSNSiteMergeList(@PathVariable String ignoredSnSiteId) {
		return turSNSiteRepository.findById(ignoredSnSiteId)
				.map(site -> turSNSiteMergeProvidersMapper
						.toDtoList(this.turSNSiteMergeRepository.findByTurSNSite(site)))
				.orElse(new ArrayList<>());
	}

	@Operation(summary = "Show a Semantic Navigation Site Merge Providers")
	@GetMapping("/{id}")
	public TurSNSiteMergeProvidersDto turSNSiteFieldExtGet(@PathVariable String ignoredSnSiteId,
			@PathVariable String id) {
		Optional<TurSNSiteMergeProviders> turSNSiteMergeOptional = turSNSiteMergeRepository.findById(id);
		if (turSNSiteMergeOptional.isPresent()) {
			TurSNSiteMergeProviders turSNSiteMerge = turSNSiteMergeOptional.get();
			turSNSiteMerge
					.setOverwrittenFields(turSNSiteMergeFieldRepository.findByTurSNSiteMergeProviders(turSNSiteMerge));
			return turSNSiteMergeProvidersMapper.toDto(turSNSiteMerge);
		} else {
			return new TurSNSiteMergeProvidersDto();
		}
	}

	@Transactional
	@Operation(summary = "Update a Semantic Navigation Site Merge Providers")
	@PutMapping("/{id}")
	public TurSNSiteMergeProvidersDto turSNSiteMergeUpdate(@PathVariable String id,
			@RequestBody TurSNSiteMergeProvidersDto turSNSiteMergeDto, @PathVariable String ignoredSnSiteId) {
		TurSNSiteMergeProviders turSNSiteMerge = turSNSiteMergeProvidersMapper.toEntity(turSNSiteMergeDto);
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
			return turSNSiteMergeProvidersMapper.toDto(turSNSiteMergeEdit);
		}).orElse(new TurSNSiteMergeProvidersDto());

	}

	@Transactional
	@Operation(summary = "Delete a Semantic Navigation Site Merge Providers")
	@DeleteMapping("/{id}")
	public boolean turSNSiteMergeDelete(@PathVariable String id, @PathVariable String ignoredSnSiteId) {
		turSNSiteMergeRepository.deleteById(id);
		return true;
	}

	@Operation(summary = "Create a Semantic Navigation Site Merge Providers")
	@PostMapping
	public TurSNSiteMergeProvidersDto turSNSiteMergeAdd(@RequestBody TurSNSiteMergeProvidersDto turSNSiteMergeDto,
			@PathVariable String ignoredSnSiteId) {
		TurSNSiteMergeProviders turSNSiteMerge = turSNSiteMergeProvidersMapper.toEntity(turSNSiteMergeDto);
		turSNSiteMergeRepository.save(turSNSiteMerge);
		turSNSiteMerge.getOverwrittenFields().forEach(field -> {
			field.setTurSNSiteMergeProviders(turSNSiteMerge);
			turSNSiteMergeFieldRepository.save(field);
		});
		return turSNSiteMergeProvidersMapper.toDto(turSNSiteMerge);
	}

	@Operation(summary = "Semantic Navigation Site Merge structure")
	@GetMapping("structure")
	public TurSNSiteMergeProvidersDto turSNSiteMergeStructure(@PathVariable String ignoredSnSiteId) {
		return turSNSiteRepository.findById(ignoredSnSiteId).map(turSNSite -> {
			TurSNSiteMergeProviders turSNSiteMerge = new TurSNSiteMergeProviders();
			turSNSiteMerge.setLocale(DEFAULT_LANGUAGE);
			turSNSiteMerge.setTurSNSite(turSNSite);
			return turSNSiteMergeProvidersMapper.toDto(turSNSiteMerge);
		}).orElse(new TurSNSiteMergeProvidersDto());
	}
}
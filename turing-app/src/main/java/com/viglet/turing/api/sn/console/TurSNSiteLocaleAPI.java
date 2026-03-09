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

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.dto.sn.locale.TurSNSiteLocaleDto;
import com.viglet.turing.persistence.mapper.sn.locale.TurSNSiteLocaleMapper;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.template.TurSNTemplate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */

@RestController
@RequestMapping("/api/sn/{snSiteId}/locale")
@Tag(name = "Semantic Navigation Locale", description = "Semantic Navigation Locale API")
public class TurSNSiteLocaleAPI {
	private static final Locale DEFAULT_LANGUAGE = Locale.US;
	private final TurSNSiteRepository turSNSiteRepository;
	private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
	private final TurSNTemplate turSNTemplate;
	private final TurSNSiteLocaleMapper turSNSiteLocaleMapper;

	public TurSNSiteLocaleAPI(TurSNSiteRepository turSNSiteRepository,
			TurSNSiteLocaleRepository turSNSiteLocaleRepository,
			TurSNTemplate turSNTemplate,
			TurSNSiteLocaleMapper turSNSiteLocaleMapper) {
		this.turSNSiteRepository = turSNSiteRepository;
		this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
		this.turSNTemplate = turSNTemplate;
		this.turSNSiteLocaleMapper = turSNSiteLocaleMapper;
	}

	@Operation(summary = "Semantic Navigation Site Locale List")
	@GetMapping
	public List<TurSNSiteLocaleDto> turSNSiteLocaleList(@PathVariable String snSiteId) {
		return turSNSiteRepository.findById(snSiteId)
				.map(site -> turSNSiteLocaleMapper.toDtoList(this.turSNSiteLocaleRepository
						.findByTurSNSite(Sort.by(Sort.Order.asc("language").ignoreCase()), site)))
				.orElse(Collections.emptyList());
	}

	@Operation(summary = "Show a Semantic Navigation Site Locale")
	@GetMapping("/{id}")
	public TurSNSiteLocaleDto turSNSiteFieldExtGet(@PathVariable String snSiteId, @PathVariable String id) {
		return turSNSiteLocaleMapper.toDto(turSNSiteLocaleRepository.findById(id).orElse(new TurSNSiteLocale()));
	}

	@Operation(summary = "Update a Semantic Navigation Site Locale")
	@PutMapping("/{id}")
	public TurSNSiteLocaleDto turSNSiteLocaleUpdate(@PathVariable String id,
			@RequestBody TurSNSiteLocaleDto turSNSiteLocaleDto,
			@PathVariable String snSiteId) {
		TurSNSiteLocale turSNSiteLocale = turSNSiteLocaleMapper.toEntity(turSNSiteLocaleDto);
		return this.turSNSiteLocaleRepository.findById(id).map(turSNSiteLocaleEdit -> {
			turSNSiteLocaleEdit.setCore(turSNSiteLocale.getCore());
			turSNSiteLocaleEdit.setLanguage(turSNSiteLocale.getLanguage());
			turSNSiteLocaleEdit.setTurSNSite(turSNSiteLocale.getTurSNSite());

			turSNSiteLocaleRepository.save(turSNSiteLocaleEdit);
			return turSNSiteLocaleMapper.toDto(turSNSiteLocaleEdit);
		}).orElse(new TurSNSiteLocaleDto());

	}

	@Transactional
	@Operation(summary = "Delete a Semantic Navigation Site Locale")
	@DeleteMapping("/{id}")
	public boolean turSNSiteLocaleDelete(@PathVariable String id, @PathVariable String snSiteId) {
		return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
			turSNSiteLocaleRepository.deleteById(id);
			return true;
		}).orElse(false);
	}

	@Operation(summary = "Create a Semantic Navigation Site Locale")
	@PostMapping
	public TurSNSiteLocaleDto turSNSiteLocaleAdd(@RequestBody TurSNSiteLocaleDto turSNSiteLocaleDto,
			Principal principal,
			@PathVariable String snSiteId) {
		TurSNSiteLocale turSNSiteLocale = turSNSiteLocaleMapper.toEntity(turSNSiteLocaleDto);
		return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
			turSNSiteLocale.setTurSNSite(turSNSite);
			if (!StringUtils.hasText(turSNSiteLocale.getCore())) {
				turSNSiteLocale.setCore(turSNTemplate.createSolrCore(turSNSiteLocale, principal.getName()));
			} else {
				turSNTemplate.createSolrCore(turSNSiteLocale, principal.getName());
			}
			turSNSiteLocaleRepository.save(turSNSiteLocale);
			return turSNSiteLocaleMapper.toDto(turSNSiteLocale);
		}).orElse(new TurSNSiteLocaleDto());
	}

	@Operation(summary = "Semantic Navigation Site Locale structure")
	@GetMapping("structure")
	public TurSNSiteLocaleDto turSNSiteLocaleStructure(@PathVariable String snSiteId) {
		return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
			TurSNSiteLocale turSNSiteLocale = new TurSNSiteLocale();
			turSNSiteLocale.setLanguage(DEFAULT_LANGUAGE);
			turSNSiteLocale.setTurSNSite(turSNSite);
			return turSNSiteLocaleMapper.toDto(turSNSiteLocale);
		}).orElse(new TurSNSiteLocaleDto());
	}
}
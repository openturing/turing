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

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
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
	private static final String DEFAULT_LANGUAGE = "en_US";
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

	@Operation(summary = "Update a Semantic Navigation Site Locale")
	@PutMapping("/{id}")
	public TurSNSiteLocale turSNSiteLocaleUpdate(@PathVariable String id,
			@RequestBody TurSNSiteLocale turSNSiteLocale) {
		return this.turSNSiteLocaleRepository.findById(id).map(turSNSiteLocaleEdit -> {
			turSNSiteLocaleEdit.setCore(turSNSiteLocale.getCore());
			turSNSiteLocaleEdit.setLanguage(turSNSiteLocale.getLanguage());
			if (turSNSiteLocale.getTurNLPInstance() != null && turSNSiteLocale.getTurNLPInstance().getId() == null) {
				turSNSiteLocale.setTurNLPInstance(null);
			}
			turSNSiteLocaleEdit.setTurNLPInstance(turSNSiteLocale.getTurNLPInstance());
			turSNSiteLocaleEdit.setTurSNSite(turSNSiteLocale.getTurSNSite());

			turSNSiteLocaleRepository.save(turSNSiteLocaleEdit);
			return turSNSiteLocaleEdit;
		}).orElse(new TurSNSiteLocale());

	}

	@Transactional
	@Operation(summary = "Delete a Semantic Navigation Site Locale")
	@DeleteMapping("/{id}")
	public boolean turSNSiteLocaleDelete(@PathVariable String id) {
		turSNSiteLocaleRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Semantic Navigation Site Locale")
	@PostMapping
	public TurSNSiteLocale turSNSiteLocaleAdd(@RequestBody TurSNSiteLocale turSNSiteLocale) {
		if (turSNSiteLocale.getTurNLPInstance() != null && turSNSiteLocale.getTurNLPInstance().getId() == null) {
			turSNSiteLocale.setTurNLPInstance(null);
		}
		turSNSiteLocaleRepository.save(turSNSiteLocale);
		return turSNSiteLocale;
	}

	@Operation(summary = "Semantic Navigation Site Locale structure")
	@GetMapping("structure")
	public TurSNSiteLocale turSNSiteLocaleStructure(@PathVariable String snSiteId) {
		return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
			TurSNSiteLocale turSNSiteLocale = new TurSNSiteLocale();
			turSNSiteLocale.setLanguage(DEFAULT_LANGUAGE);
			turSNSiteLocale.setTurNLPInstance(new TurNLPInstance());
			turSNSiteLocale.setTurSNSite(turSNSite);
			return turSNSiteLocale;
		}).orElse(new TurSNSiteLocale());
	}
}
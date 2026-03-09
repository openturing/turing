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

import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.dto.sn.locale.TurSNSiteLocaleDto;
import com.viglet.turing.persistence.mapper.sn.locale.TurSNSiteLocaleMapper;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 * @since 2025.2
 */

@RestController
@RequestMapping("/api/sn/name/{siteName}/locale")
@Tag(name = "Semantic Navigation Locale By Name", description = "Semantic Navigation Locale By Name API")
public class TurSNSiteByNameLocaleAPI {
	private final TurSNSiteRepository turSNSiteRepository;
	private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
	private final TurSNSiteLocaleMapper turSNSiteLocaleMapper;

	public TurSNSiteByNameLocaleAPI(TurSNSiteRepository turSNSiteRepository,
			TurSNSiteLocaleRepository turSNSiteLocaleRepository,
			TurSNSiteLocaleMapper turSNSiteLocaleMapper) {
		this.turSNSiteRepository = turSNSiteRepository;
		this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
		this.turSNSiteLocaleMapper = turSNSiteLocaleMapper;
	}

	@Operation(summary = "Semantic Navigation Site Locale List")
	@GetMapping
	public List<TurSNSiteLocaleDto> turSNSiteLocaleByNameList(@PathVariable String siteName) {
		return turSNSiteRepository.findByName(siteName)
				.map(site -> turSNSiteLocaleMapper.toDtoList(this.turSNSiteLocaleRepository
						.findByTurSNSite(Sort.by(Sort.Order.asc("language").ignoreCase()), site)))
				.orElse(Collections.emptyList());
	}

}
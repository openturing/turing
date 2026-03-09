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
package com.viglet.turing.api.integration;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.dto.integration.TurIntegrationInstanceDto;
import com.viglet.turing.persistence.mapper.integration.TurIntegrationInstanceMapper;
import com.viglet.turing.persistence.model.integration.TurIntegrationInstance;
import com.viglet.turing.persistence.repository.integration.TurIntegrationInstanceRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/integration")
@Tag(name = "Integration", description = "Integration API")
public class TurIntegrationInstanceAPI {
	private final TurIntegrationInstanceRepository turIntegrationInstanceRepository;
	private final TurIntegrationInstanceMapper turIntegrationInstanceMapper;

	public TurIntegrationInstanceAPI(TurIntegrationInstanceRepository turIntegrationInstanceRepository,
			TurIntegrationInstanceMapper turIntegrationInstanceMapper) {
		this.turIntegrationInstanceRepository = turIntegrationInstanceRepository;
		this.turIntegrationInstanceMapper = turIntegrationInstanceMapper;
	}

	@Operation(summary = "Integration List")
	@GetMapping
	public List<TurIntegrationInstanceDto> turIntegrationInstanceList() {
		return turIntegrationInstanceMapper
				.toDtoList(this.turIntegrationInstanceRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase()));
	}

	@Operation(summary = "Integration structure")
	@GetMapping("/structure")
	public TurIntegrationInstanceDto turIntegrationInstanceStructure() {
		return new TurIntegrationInstanceDto();

	}

	@Operation(summary = "Show a Integration")
	@GetMapping("/{id}")
	public TurIntegrationInstanceDto turIntegrationInstanceGet(@PathVariable String id) {
		return turIntegrationInstanceMapper
				.toDto(this.turIntegrationInstanceRepository.findById(id).orElse(new TurIntegrationInstance()));
	}

	@Operation(summary = "Update a Integration")
	@PutMapping("/{id}")
	public TurIntegrationInstanceDto turIntegrationInstanceUpdate(@PathVariable String id,
			@RequestBody TurIntegrationInstanceDto turIntegrationInstanceDto) {
		TurIntegrationInstance turIntegrationInstance = turIntegrationInstanceMapper
				.toEntity(turIntegrationInstanceDto);
		return turIntegrationInstanceRepository.findById(id).map(turIntegrationInstanceEdit -> {
			turIntegrationInstanceEdit.setTitle(turIntegrationInstance.getTitle());
			turIntegrationInstanceEdit.setDescription(turIntegrationInstance.getDescription());
			turIntegrationInstanceEdit.setVendor(turIntegrationInstance.getVendor());
			turIntegrationInstanceEdit.setEndpoint(turIntegrationInstance.getEndpoint());
			turIntegrationInstanceEdit.setEnabled(turIntegrationInstance.getEnabled());
			this.turIntegrationInstanceRepository.save(turIntegrationInstanceEdit);
			return turIntegrationInstanceMapper.toDto(turIntegrationInstanceEdit);
		}).orElse(new TurIntegrationInstanceDto());

	}

	@Transactional
	@Operation(summary = "Delete a Integration")
	@DeleteMapping("/{id}")
	public boolean turIntegrationInstanceDelete(@PathVariable String id) {
		this.turIntegrationInstanceRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Integration")
	@PostMapping
	public TurIntegrationInstanceDto turIntegrationInstanceAdd(
			@RequestBody TurIntegrationInstanceDto turIntegrationInstanceDto) {
		TurIntegrationInstance turIntegrationInstance = turIntegrationInstanceMapper
				.toEntity(turIntegrationInstanceDto);
		this.turIntegrationInstanceRepository.save(turIntegrationInstance);
		return turIntegrationInstanceMapper.toDto(turIntegrationInstance);

	}
}

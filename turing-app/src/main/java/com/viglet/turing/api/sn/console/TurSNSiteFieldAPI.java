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

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldDto;
import com.viglet.turing.persistence.mapper.sn.field.TurSNSiteFieldMapper;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/sn/{snSiteId}/field")
@Tag(name = "Semantic Navigation Field", description = "Semantic Navigation Field API")
public class TurSNSiteFieldAPI {
	private final TurSNSiteRepository turSNSiteRepository;
	private final TurSNSiteFieldRepository turSNSiteFieldRepository;
	private final TurSNSiteFieldMapper turSNSiteFieldMapper;

	public TurSNSiteFieldAPI(TurSNSiteRepository turSNSiteRepository,
			TurSNSiteFieldRepository turSNSiteFieldRepository,
			TurSNSiteFieldMapper turSNSiteFieldMapper) {
		this.turSNSiteRepository = turSNSiteRepository;
		this.turSNSiteFieldRepository = turSNSiteFieldRepository;
		this.turSNSiteFieldMapper = turSNSiteFieldMapper;
	}

	@Operation(summary = "Semantic Navigation Site Field List")
	@GetMapping
	public List<TurSNSiteFieldDto> turSNSiteFieldList(@PathVariable String snSiteId) {
		return turSNSiteRepository.findById(snSiteId)
				.map(turSNSite -> turSNSiteFieldMapper
						.toDtoList(this.turSNSiteFieldRepository.findByTurSNSite(turSNSite)))
				.orElse(new ArrayList<>());

	}

	@Operation(summary = "Show a Semantic Navigation Site Field")
	@GetMapping("/{id}")
	public TurSNSiteFieldDto turSNSiteFieldGet(@PathVariable String snSiteId, @PathVariable String id) {
		return turSNSiteFieldMapper.toDto(this.turSNSiteFieldRepository.findById(id).orElse(new TurSNSiteField()));
	}

	@Operation(summary = "Update a Semantic Navigation Site Field")
	@PutMapping("/{id}")
	public TurSNSiteFieldDto turSNSiteFieldUpdate(@PathVariable String snSiteId, @PathVariable String id,
			@RequestBody TurSNSiteFieldDto turSNSiteFieldDto) {
		TurSNSiteField turSNSiteField = turSNSiteFieldMapper.toEntity(turSNSiteFieldDto);
		return this.turSNSiteFieldRepository.findById(id).map(turSNSiteFieldEdit -> {
			turSNSiteFieldEdit.setDescription(turSNSiteField.getDescription());
			turSNSiteFieldEdit.setMultiValued(turSNSiteField.getMultiValued());
			turSNSiteFieldEdit.setName(turSNSiteField.getName());
			turSNSiteFieldEdit.setType(turSNSiteField.getType());
			this.turSNSiteFieldRepository.save(turSNSiteFieldEdit);
			return turSNSiteFieldMapper.toDto(turSNSiteFieldEdit);
		}).orElse(new TurSNSiteFieldDto());

	}

	@Transactional
	@Operation(summary = "Delete a Semantic Navigation Site Field")
	@DeleteMapping("/{id}")
	public boolean turSNSiteFieldDelete(@PathVariable String snSiteId, @PathVariable String id) {
		this.turSNSiteFieldRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Semantic Navigation Site Field")
	@PostMapping
	public TurSNSiteFieldDto turSNSiteFieldAdd(@PathVariable String snSiteId,
			@RequestBody TurSNSiteFieldDto turSNSiteFieldDto) {
		TurSNSiteField turSNSiteField = turSNSiteFieldMapper.toEntity(turSNSiteFieldDto);
		return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
			turSNSiteField.setTurSNSite(turSNSite);
			this.turSNSiteFieldRepository.save(turSNSiteField);
			return turSNSiteFieldMapper.toDto(turSNSiteField);
		}).orElse(new TurSNSiteFieldDto());
	}
}

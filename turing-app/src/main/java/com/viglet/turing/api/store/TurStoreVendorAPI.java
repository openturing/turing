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
package com.viglet.turing.api.store;

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

import com.viglet.turing.persistence.dto.store.TurStoreVendorDto;
import com.viglet.turing.persistence.mapper.store.TurStoreVendorMapper;
import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.store.TurStoreVendorRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/store/vendor")
@Tag(name = "Search Engine Vendor", description = "Search Engine Vendor API")
public class TurStoreVendorAPI {
	private final TurStoreVendorRepository turStoreVendorRepository;
	private final TurStoreVendorMapper turStoreVendorMapper;

	public TurStoreVendorAPI(TurStoreVendorRepository turStoreVendorRepository,
			TurStoreVendorMapper turStoreVendorMapper) {
		this.turStoreVendorRepository = turStoreVendorRepository;
		this.turStoreVendorMapper = turStoreVendorMapper;
	}

	@Operation(summary = "Search Engine Vendor List")
	@GetMapping
	public List<TurStoreVendorDto> turStoreVendorList() {
		return turStoreVendorMapper
				.toDtoList(this.turStoreVendorRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase()));
	}

	@Operation(summary = "Show a Search Engine Vendor")
	@GetMapping("/{id}")
	public TurStoreVendorDto turStoreVendorGet(@PathVariable String id) {
		return turStoreVendorMapper.toDto(this.turStoreVendorRepository.findById(id).orElse(new TurStoreVendor()));
	}

	@Operation(summary = "Update a Search Engine Vendor")
	@PutMapping("/{id}")
	public TurStoreVendorDto turStoreVendorUpdate(@PathVariable String id,
			@RequestBody TurStoreVendorDto turStoreVendorDto) {
		TurStoreVendor turStoreVendor = turStoreVendorMapper.toEntity(turStoreVendorDto);
		return this.turStoreVendorRepository.findById(id).map(turStoreVendorEdit -> {
			turStoreVendorEdit.setDescription(turStoreVendor.getDescription());
			turStoreVendorEdit.setPlugin(turStoreVendor.getPlugin());
			turStoreVendorEdit.setTitle(turStoreVendor.getTitle());
			turStoreVendorEdit.setWebsite(turStoreVendor.getWebsite());
			this.turStoreVendorRepository.save(turStoreVendorEdit);
			return turStoreVendorMapper.toDto(turStoreVendorEdit);
		}).orElse(new TurStoreVendorDto());

	}

	@Transactional
	@Operation(summary = "Delete a Search Engine Vendor")
	@DeleteMapping("/{id}")
	public boolean turStoreVendorDelete(@PathVariable String id) {
		this.turStoreVendorRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Search Engine Vendor")
	@PostMapping
	public TurStoreVendorDto turStoreVendorAdd(@RequestBody TurStoreVendorDto turStoreVendorDto) {
		TurStoreVendor turStoreVendor = turStoreVendorMapper.toEntity(turStoreVendorDto);
		this.turStoreVendorRepository.save(turStoreVendor);
		return turStoreVendorMapper.toDto(turStoreVendor);

	}
}

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
package com.viglet.turing.api.se;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;
import com.viglet.turing.persistence.utils.TurPersistenceUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/se/vendor")
@Tag(name = "Search Engine Vendor", description = "Search Engine Vendor API")
public class TurSEVendorAPI {
	private final TurSEVendorRepository turSEVendorRepository;

	@Inject
	public TurSEVendorAPI(TurSEVendorRepository turSEVendorRepository) {
		this.turSEVendorRepository = turSEVendorRepository;
	}

	@Operation(summary = "Search Engine Vendor List")
	@GetMapping
	public List<TurSEVendor> turSEVendorList() {
		return this.turSEVendorRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase());
	}

	@Operation(summary = "Show a Search Engine Vendor")
	@GetMapping("/{id}")
	public TurSEVendor turSEVendorGet(@PathVariable String id) {
		return this.turSEVendorRepository.findById(id).orElse(new TurSEVendor());
	}

	@Operation(summary = "Update a Search Engine Vendor")
	@PutMapping("/{id}")
	public TurSEVendor turSEVendorUpdate(@PathVariable String id, @RequestBody TurSEVendor turSEVendor) {
		return this.turSEVendorRepository.findById(id).map(turSEVendorEdit -> {
			turSEVendorEdit.setDescription(turSEVendor.getDescription());
			turSEVendorEdit.setPlugin(turSEVendor.getPlugin());
			turSEVendorEdit.setTitle(turSEVendor.getTitle());
			turSEVendorEdit.setWebsite(turSEVendor.getWebsite());
			this.turSEVendorRepository.save(turSEVendorEdit);
			return turSEVendorEdit;
		}).orElse(new TurSEVendor());

	}

	@Transactional
	@Operation(summary = "Delete a Search Engine Vendor")
	@DeleteMapping("/{id}")
	public boolean turSEVendorDelete(@PathVariable String id) {
		this.turSEVendorRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Search Engine Vendor")
	@PostMapping
	public TurSEVendor turSEVendorAdd(@RequestBody TurSEVendor turSEVendor) {
		this.turSEVendorRepository.save(turSEVendor);
		return turSEVendor;

	}
}

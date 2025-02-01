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

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.store.TurStoreVendorRepository;
import com.viglet.turing.persistence.utils.TurPersistenceUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/store/vendor")
@Tag(name = "Search Engine Vendor", description = "Search Engine Vendor API")
public class TurStoreVendorAPI {
	private final TurStoreVendorRepository turStoreVendorRepository;

	@Inject
	public TurStoreVendorAPI(TurStoreVendorRepository turStoreVendorRepository) {
		this.turStoreVendorRepository = turStoreVendorRepository;
	}

	@Operation(summary = "Search Engine Vendor List")
	@GetMapping
	public List<TurStoreVendor> turStoreVendorList() {
		return this.turStoreVendorRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase());
	}

	@Operation(summary = "Show a Search Engine Vendor")
	@GetMapping("/{id}")
	public TurStoreVendor turStoreVendorGet(@PathVariable String id) {
		return this.turStoreVendorRepository.findById(id).orElse(new TurStoreVendor());
	}

	@Operation(summary = "Update a Search Engine Vendor")
	@PutMapping("/{id}")
	public TurStoreVendor turStoreVendorUpdate(@PathVariable String id, @RequestBody TurStoreVendor turStoreVendor) {
		return this.turStoreVendorRepository.findById(id).map(turStoreVendorEdit -> {
			turStoreVendorEdit.setDescription(turStoreVendor.getDescription());
			turStoreVendorEdit.setPlugin(turStoreVendor.getPlugin());
			turStoreVendorEdit.setTitle(turStoreVendor.getTitle());
			turStoreVendorEdit.setWebsite(turStoreVendor.getWebsite());
			this.turStoreVendorRepository.save(turStoreVendorEdit);
			return turStoreVendorEdit;
		}).orElse(new TurStoreVendor());

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
	public TurStoreVendor turStoreVendorAdd(@RequestBody TurStoreVendor turStoreVendor) {
		this.turStoreVendorRepository.save(turStoreVendor);
		return turStoreVendor;

	}
}

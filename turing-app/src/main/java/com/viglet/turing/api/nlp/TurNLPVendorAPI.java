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

package com.viglet.turing.api.nlp;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorRepository;
import com.viglet.turing.persistence.utils.TurPersistenceUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nlp/vendor")
@Tag(name = "Natural Language Processing Vendor", description = "Natural Language Processing Vendor API")
public class TurNLPVendorAPI {
	private final TurNLPVendorRepository turNLPVendorRepository;
	@Inject
	public TurNLPVendorAPI(TurNLPVendorRepository turNLPVendorRepository) {
		this.turNLPVendorRepository = turNLPVendorRepository;
	}

	@Operation(summary = "Natural Language Processing Vendor List")
	@GetMapping
	public List<TurNLPVendor> turNLPVendorList() {
		return this.turNLPVendorRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase());
	}

	@Operation(summary = "Show a Natural Language Processing Vendor")
	@GetMapping("/{id}")
	public TurNLPVendor turNLPVendorGet(@PathVariable String id) {
		return this.turNLPVendorRepository.findById(id).orElse(new TurNLPVendor());
	}

	@Operation(summary = "Update a Natural Language Processing")
	@PutMapping("/{id}")
	public TurNLPVendor turNLPVendorUpdate(@PathVariable String id, @RequestBody TurNLPVendor turNLPVendor) {
		return this.turNLPVendorRepository.findById(id).map(turNLPVendorEdit -> {
			turNLPVendorEdit.setDescription(turNLPVendor.getDescription());
			turNLPVendorEdit.setPlugin(turNLPVendor.getPlugin());
			turNLPVendorEdit.setTitle(turNLPVendor.getTitle());
			turNLPVendorEdit.setWebsite(turNLPVendor.getWebsite());
			this.turNLPVendorRepository.save(turNLPVendorEdit);
			return turNLPVendorEdit;
		}).orElse(new TurNLPVendor());

	}

	@Transactional
	@Operation(summary = "Delete a Natural Language Processing Vendor")
	@DeleteMapping("/{id}")
	public boolean turNLPVendorDelete(@PathVariable String id) {
		this.turNLPVendorRepository.deleteById(id);
		return true;
	}

	@Operation(summary = "Create a Natural Language Processing Vendor")
	@PostMapping
	public TurNLPVendor turNLPVendorAdd(@RequestBody TurNLPVendor turNLPVendor) {
		this.turNLPVendorRepository.save(turNLPVendor);
		return turNLPVendor;

	}
}

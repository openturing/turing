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

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.integration.TurIntegrationInstance;
import com.viglet.turing.persistence.repository.integration.TurIntegrationInstanceRepository;
import com.viglet.turing.persistence.utils.TurPersistenceUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/integration")
@Tag(name = "Integration", description = "Integration API")
public class TurIntegrationInstanceAPI {
	private final TurIntegrationInstanceRepository turIntegrationInstanceRepository;


	@Inject
	public TurIntegrationInstanceAPI(TurIntegrationInstanceRepository turIntegrationInstanceRepository) {
		this.turIntegrationInstanceRepository = turIntegrationInstanceRepository;
	}

	@Operation(summary = "Integration List")
	@GetMapping
	public List<TurIntegrationInstance> turIntegrationInstanceList() {
		return this.turIntegrationInstanceRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase());
	}

	@Operation(summary = "Integration structure")
	@GetMapping("/structure")
	public TurIntegrationInstance turIntegrationInstanceStructure() {
        return new TurIntegrationInstance();

	}

	@Operation(summary = "Show a Integration")
	@GetMapping("/{id}")
	public TurIntegrationInstance turIntegrationInstanceGet(@PathVariable String id) {
		return this.turIntegrationInstanceRepository.findById(id).orElse(new TurIntegrationInstance());
	}

	@Operation(summary = "Update a Integration")
	@PutMapping("/{id}")
	public TurIntegrationInstance turIntegrationInstanceUpdate(@PathVariable String id, @RequestBody TurIntegrationInstance turIntegrationInstance) {
		return turIntegrationInstanceRepository.findById(id).map(turIntegrationInstanceEdit -> {
			turIntegrationInstanceEdit.setTitle(turIntegrationInstance.getTitle());
			turIntegrationInstanceEdit.setDescription(turIntegrationInstance.getDescription());
			turIntegrationInstanceEdit.setVendor(turIntegrationInstance.getVendor());
			turIntegrationInstanceEdit.setEndpoint(turIntegrationInstance.getEndpoint());
			turIntegrationInstanceEdit.setEnabled(turIntegrationInstance.getEnabled());
			this.turIntegrationInstanceRepository.save(turIntegrationInstanceEdit);
			return turIntegrationInstanceEdit;
		}).orElse(new TurIntegrationInstance());

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
	public TurIntegrationInstance turIntegrationInstanceAdd(@RequestBody TurIntegrationInstance turIntegrationInstance) {
		this.turIntegrationInstanceRepository.save(turIntegrationInstance);
		return turIntegrationInstance;

	}
}

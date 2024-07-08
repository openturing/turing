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

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/sn/{snSiteId}/field")
@Tag(name = "Semantic Navigation Field", description = "Semantic Navigation Field API")
public class TurSNSiteFieldAPI {
	private final TurSNSiteRepository turSNSiteRepository;
	private final TurSNSiteFieldRepository turSNSiteFieldRepository;
	@Inject
	public TurSNSiteFieldAPI(TurSNSiteRepository turSNSiteRepository,
							 TurSNSiteFieldRepository turSNSiteFieldRepository) {
		this.turSNSiteRepository = turSNSiteRepository;
		this.turSNSiteFieldRepository = turSNSiteFieldRepository;
	}

	@Operation(summary = "Semantic Navigation Site Field List")
	@GetMapping
	public List<TurSNSiteField> turSNSiteFieldList(@PathVariable String snSiteId) {
		return turSNSiteRepository.findById(snSiteId)
				.map(this.turSNSiteFieldRepository::findByTurSNSite).orElse(new ArrayList<>());

	}

	@Operation(summary = "Show a Semantic Navigation Site Field")
	@GetMapping("/{id}")
	public TurSNSiteField turSNSiteFieldGet(@PathVariable String snSiteId, @PathVariable String id) {
		return this.turSNSiteFieldRepository.findById(id).orElse(new TurSNSiteField());
	}

	@Operation(summary = "Update a Semantic Navigation Site Field")
	@PutMapping("/{id}")
	public TurSNSiteField turSNSiteFieldUpdate(@PathVariable String snSiteId, @PathVariable String id,
			@RequestBody TurSNSiteField turSNSiteField) {
		return this.turSNSiteFieldRepository.findById(id).map(turSNSiteFieldEdit -> {
			turSNSiteFieldEdit.setDescription(turSNSiteField.getDescription());
			turSNSiteFieldEdit.setMultiValued(turSNSiteField.getMultiValued());
			turSNSiteFieldEdit.setName(turSNSiteField.getName());
			turSNSiteFieldEdit.setType(turSNSiteField.getType());
			this.turSNSiteFieldRepository.save(turSNSiteFieldEdit);
			return turSNSiteFieldEdit;
		}).orElse(new TurSNSiteField());

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
	public TurSNSiteField turSNSiteFieldAdd(@PathVariable String snSiteId, @RequestBody TurSNSiteField turSNSiteField) {
		return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
			turSNSiteField.setTurSNSite(turSNSite);
			this.turSNSiteFieldRepository.save(turSNSiteField);
			return turSNSiteField;
		}).orElse(new TurSNSiteField());
	}
}

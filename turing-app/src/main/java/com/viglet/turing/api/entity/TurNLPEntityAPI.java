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

package com.viglet.turing.api.entity;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/entity")
@Tag(name ="Entity", description = "Entity API")
public class TurNLPEntityAPI {
	private final TurNLPEntityRepository turNLPEntityRepository;
	@Inject
	public TurNLPEntityAPI(TurNLPEntityRepository turNLPEntityRepository) {
		this.turNLPEntityRepository = turNLPEntityRepository;
	}

	@Operation(summary = "Entity List")
	@GetMapping
	public List<TurNLPEntity> turNLPEntityList() {
		return this.turNLPEntityRepository.findAll();
	}

	@Operation(summary = "Entity structure")
	@GetMapping("/structure")
	public TurNLPEntity turNLPEntityStructure() {
		return new TurNLPEntity();

	}

	@Operation(summary = "Local Entity list")
	@GetMapping("/local")
	public List<TurNLPEntity> turNLPEntityLocal() {
		return this.turNLPEntityRepository.findByLocal(1);
	}

	@Operation(summary = "Show a entity")
	@GetMapping("/{id}")
	public TurNLPEntity turNLPEntityGet(@PathVariable String id) {
		return this.turNLPEntityRepository.findById(id).orElse(new TurNLPEntity());
	}

	@Operation(summary = "Update a entity")
	@PutMapping("/{id}")
	public TurNLPEntity turNLPEntityUpdate(@PathVariable String id, @RequestBody TurNLPEntity turNLPEntity) {
		return this.turNLPEntityRepository.findById(id).map(turNLPEntityEdit -> {
			turNLPEntityEdit.setName(turNLPEntity.getName());
			turNLPEntityEdit.setDescription(turNLPEntity.getDescription());
			this.turNLPEntityRepository.save(turNLPEntityEdit);
			return turNLPEntityEdit;
		}).orElse(new TurNLPEntity());

	}

	@Transactional
	@Operation(summary = "Delete a entity")
	@DeleteMapping("/{id}")
	public boolean turNLPEntityDelete(@PathVariable String id) {
		return this.turNLPEntityRepository.findById(id).map(turNLPEntity -> {
			this.turNLPEntityRepository.delete(turNLPEntity);
			return true;
		}).orElse(false);

	}

	@Operation(summary = "Create a entity")
	@PostMapping
	public TurNLPEntity turNLPEntityAdd(@RequestBody TurNLPEntity turNLPEntity) {
		this.turNLPEntityRepository.save(turNLPEntity);
		return turNLPEntity;

	}
}
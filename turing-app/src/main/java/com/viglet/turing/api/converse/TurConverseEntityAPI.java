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

package com.viglet.turing.api.converse;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.converse.entity.TurConverseEntity;
import com.viglet.turing.persistence.model.converse.entity.TurConverseEntityTerm;
import com.viglet.turing.persistence.repository.converse.entity.TurConverseEntityRepository;
import com.viglet.turing.persistence.repository.converse.entity.TurConverseEntityTermRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/converse/entity")
@Tag(name ="Converse Entity", description = "Converse Entity API")
public class TurConverseEntityAPI {

	private final TurConverseEntityRepository turConverseEntityRepository;
	private final TurConverseEntityTermRepository turConverseEntityTermRepository;

	@Inject
	public TurConverseEntityAPI(TurConverseEntityRepository turConverseEntityRepository,
								TurConverseEntityTermRepository turConverseEntityTermRepository) {
		this.turConverseEntityRepository = turConverseEntityRepository;
		this.turConverseEntityTermRepository = turConverseEntityTermRepository;
	}

	@Operation(summary = "Converse Entity List")
	@GetMapping
	public List<TurConverseEntity> turConverseEntityList() {
		return this.turConverseEntityRepository.findAll();
	}

	@Operation(summary = "Show a Converse Entity")
	@GetMapping("/{id}")
	public TurConverseEntity turConverseEntityGet(@PathVariable String id) {
		return this.turConverseEntityRepository.findById(id).map(turConverseEntity -> {
			turConverseEntity.setTerms(turConverseEntityTermRepository.findByEntity(turConverseEntity));
			return turConverseEntity;
		}).orElse(new TurConverseEntity());
	}

	@Operation(summary = "Create a Converse Entity")
	@PostMapping
	public TurConverseEntity turConverseEntityAdd(@RequestBody TurConverseEntity turConverseEntity) {
		return this.saveEntity(turConverseEntity);
	}

	@Operation(summary = "Update a Converse Entity")
	@PutMapping("/{id}")
	public TurConverseEntity turConverseEntityUpdate(@PathVariable String id,
			@RequestBody TurConverseEntity turConverseEntity) {
		return this.saveEntity(turConverseEntity);

	}

	private TurConverseEntity saveEntity(TurConverseEntity turConverseEntity) {
		turConverseEntityRepository.save(turConverseEntity);

		Set<TurConverseEntityTerm> terms = turConverseEntity.getTerms();
		for (TurConverseEntityTerm term : terms) {
			if (term != null) {
				term.setEntity(turConverseEntity);
				turConverseEntityTermRepository.save(term);
			}
		}
		return turConverseEntity;
	}

	@Transactional
	@Operation(summary = "Delete a Converse Entity")
	@DeleteMapping("/{id}")
	public boolean turConverseEntityDelete(@PathVariable String id) {
		this.turConverseEntityRepository.deleteById(id);
		return true;
	}

	@Operation(summary = "Converse Entity Model")
	@GetMapping("/model")
	public TurConverseEntity turConverseEntityModel() {
		return new TurConverseEntity();
	}
}

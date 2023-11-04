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
import com.viglet.turing.persistence.model.nlp.term.TurTerm;
import com.viglet.turing.persistence.repository.nlp.term.TurTermRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/entity/terms")
@Tag(name ="Term", description = "Term API")
public class TurNLPEntityTermAPI {
	private final TurTermRepository turTermRepository;

	@Inject
	public TurNLPEntityTermAPI(TurTermRepository turTermRepository) {
		this.turTermRepository = turTermRepository;
	}

	@Operation(summary = "Entity Term list")
	@GetMapping
	public List<TurTerm> turTermList() {
		return this.turTermRepository.findAll();
	}

	@Operation(summary = "Show a Entity Term")
	@GetMapping("/{id}")
	public TurTerm turTermGet(@PathVariable String id) {
		return this.turTermRepository.findById(id).orElse(new TurTerm());
	}

	@Operation(summary = "Update a Entity Term")
	@PutMapping("/{id}")
	public TurTerm turTermUpdate(@PathVariable String id, @RequestBody TurTerm turTerm) {
		return this.turTermRepository.findById(id).map(turTermEdit -> {
			turTermEdit.setName(turTerm.getName());
			turTermEdit.setIdCustom(turTerm.getIdCustom());
			this.turTermRepository.save(turTermEdit);
			return turTermEdit;
		}).orElse(new TurTerm());

	}

	@Transactional
	@Operation(summary = "Delete a Entity Term")
	@DeleteMapping("/{id}")
	public boolean turTermDelete(@PathVariable String id) {
		this.turTermRepository.deleteById(id);
		return true;
	}

	@Operation(summary = "Create a Entity Term")
	@PostMapping
	public TurTerm turTermAdd(@RequestBody TurTerm turTerm) {
		this.turTermRepository.save(turTerm);
		return turTerm;

	}
}

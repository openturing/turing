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

package com.viglet.turing.api.ml.data.sentence;

import java.util.List;

import com.google.inject.Inject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.repository.storage.TurDataGroupSentenceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ml/data/sentence")
@Tag(name = "Machine Learning Sentence", description = "Machine Learning Sentence API")
public class TurMLDataSentenceAPI {

	private final TurDataGroupSentenceRepository turDataGroupSentenceRepository;

	@Inject
	public TurMLDataSentenceAPI(TurDataGroupSentenceRepository turDataGroupSentenceRepository) {
		this.turDataGroupSentenceRepository = turDataGroupSentenceRepository;
	}

	@Operation(summary = "Machine Learning Data Sentence List")
	@GetMapping
	public List<TurDataGroupSentence> turDataSentenceList() {
		return this.turDataGroupSentenceRepository.findAll();
	}

	@Operation(summary = "Show a Machine Learning Data Sentence")
	@GetMapping("/{id}")
	public TurDataGroupSentence turDataSentenceGet(@PathVariable int id) {
		return turDataGroupSentenceRepository.findById(id);
	}

	@Operation(summary = "Update a Machine Learning Data Sentence")
	@PutMapping("/{id}")
	public TurDataGroupSentence turDataSentenceUpdate(@PathVariable int id,
			@RequestBody TurDataGroupSentence turDataGroupSentence) {

		TurDataGroupSentence turDataGroupSentenceEdit = turDataGroupSentenceRepository.findById(id);
		turDataGroupSentenceEdit.setSentence(turDataGroupSentence.getSentence());
		turDataGroupSentenceEdit.setTurData(turDataGroupSentence.getTurData());
		turDataGroupSentenceEdit.setTurMLCategory(turDataGroupSentence.getTurMLCategory());
		this.turDataGroupSentenceRepository.save(turDataGroupSentenceEdit);
		return turDataGroupSentenceEdit;
	}

	@Transactional
	@Operation(summary = "Delete a Machine Learning Data Sentence")
	@DeleteMapping("/{id}")
	public boolean turDataSentenceDelete(@PathVariable int id) {
		this.turDataGroupSentenceRepository.deleteById(id);
		return true;
	}

	@Operation(summary = "Create a Machine Learning Data Sentence")
	@PostMapping
	public TurDataGroupSentence turDataSentenceAdd(@RequestBody TurDataGroupSentence turDataSentence) {
		this.turDataGroupSentenceRepository.save(turDataSentence);
		return turDataSentence;

	}
}

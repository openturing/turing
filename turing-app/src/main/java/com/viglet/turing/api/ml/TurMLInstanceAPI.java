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

package com.viglet.turing.api.ml;

import java.util.List;

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

import com.viglet.turing.persistence.model.ml.TurMLInstance;
import com.viglet.turing.persistence.repository.ml.TurMLInstanceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ml")
@Tag(name ="Machine Learning", description = "Machine Learning API")
public class TurMLInstanceAPI {

	@Autowired
	private TurMLInstanceRepository turMLInstanceRepository;

	@Operation(summary = "Machine Learning List")
	@GetMapping
	public List<TurMLInstance> turMLInstanceList() {
		return this.turMLInstanceRepository.findAll();
	}

	@Operation(summary = "Show a Machine Learning")
	@GetMapping("/{id}")
	public TurMLInstance turMLInstanceGEt(@PathVariable int id) {
		return this.turMLInstanceRepository.findById(id).orElse(new TurMLInstance());
	}

	@Operation(summary = "Update a Machine Learning")
	@PutMapping("/{id}")
	public TurMLInstance turMLInstanceUpdate(@PathVariable int id, @RequestBody TurMLInstance turMLInstance) {
		return this.turMLInstanceRepository.findById(id).map(turMLInstanceEdit -> {
			turMLInstanceEdit.setTitle(turMLInstance.getTitle());
			turMLInstanceEdit.setDescription(turMLInstance.getDescription());
			turMLInstanceEdit.setTurMLVendor(turMLInstance.getTurMLVendor());
			turMLInstanceEdit.setHost(turMLInstance.getHost());
			turMLInstanceEdit.setPort(turMLInstance.getPort());
			turMLInstanceEdit.setEnabled(turMLInstance.getEnabled());
			this.turMLInstanceRepository.save(turMLInstanceEdit);
			return turMLInstanceEdit;
		}).orElse(new TurMLInstance());

	}

	@Transactional
	@Operation(summary = "Delete a Machine Learning")
	@DeleteMapping("/{id}")
	public boolean turMLInstanceDelete(@PathVariable int id) {
		this.turMLInstanceRepository.deleteById(id);
		return true;
	}

	@Operation(summary = "Create a Machine Learning")
	@PostMapping
	public TurMLInstance turMLInstanceAdd(@RequestBody TurMLInstance turMLInstance) {
		this.turMLInstanceRepository.save(turMLInstance);
		return turMLInstance;

	}
}
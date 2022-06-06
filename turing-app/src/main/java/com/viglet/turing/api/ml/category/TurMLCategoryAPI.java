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
package com.viglet.turing.api.ml.category;

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

import com.viglet.turing.persistence.model.ml.TurMLCategory;
import com.viglet.turing.persistence.repository.ml.TurMLCategoryRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ml/category")
@Tag(name ="Category", description = "Category API")
public class TurMLCategoryAPI {

	@Autowired
	private TurMLCategoryRepository turMLCategoryRepository;

	@Operation(summary = "Machine Learning Category List")
	@GetMapping
	public List<TurMLCategory> turMLCategoryList() {
		return this.turMLCategoryRepository.findAll();
	}

	@Operation(summary = "Show a Machine Learning Category")
	@GetMapping("/{id}")
	public TurMLCategory turMLCategoryGet(@PathVariable int id) {
		return this.turMLCategoryRepository.findById(id).orElse(new TurMLCategory());
	}

	@Operation(summary = "Update a Machine Learning Category")
	@PutMapping("/{id}")
	public TurMLCategory turMLCategoryUpdate(@PathVariable int id, @RequestBody TurMLCategory turMLCategory) {
		return this.turMLCategoryRepository.findById(id).map(turMLCategoryEdit -> {
			turMLCategoryEdit.setInternalName(turMLCategory.getInternalName());
			turMLCategoryEdit.setName(turMLCategory.getName());
			turMLCategoryEdit.setDescription(turMLCategory.getDescription());
			this.turMLCategoryRepository.save(turMLCategoryEdit);

			return turMLCategoryEdit;
		}).orElse(new TurMLCategory());

	}

	@Transactional
	@Operation(summary = "Delete a Machine Learning Category")
	@DeleteMapping("/{id}")
	public boolean turMLCategoryDelete(@PathVariable int id) {
		this.turMLCategoryRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Machine Learning Category")
	@PostMapping
	public TurMLCategory turMLCategoryAdd(@RequestBody TurMLCategory turMLCategory) {
		this.turMLCategoryRepository.save(turMLCategory);
		return turMLCategory;

	}
}

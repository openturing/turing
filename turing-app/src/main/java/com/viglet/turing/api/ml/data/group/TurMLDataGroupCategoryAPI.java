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
package com.viglet.turing.api.ml.data.group;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.ml.TurMLCategory;
import com.viglet.turing.persistence.model.storage.TurDataGroupCategory;
import com.viglet.turing.persistence.repository.storage.TurDataGroupCategoryRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/ml/data/group/{dataGroupId}/category")
@Tag(name ="Machine Learning Category by Group", description = "Machine Learning Category by Group API")
public class TurMLDataGroupCategoryAPI {

	private final TurDataGroupRepository turDataGroupRepository;
	private final TurDataGroupCategoryRepository turDataGroupCategoryRepository;

	@Inject
	public TurMLDataGroupCategoryAPI(TurDataGroupRepository turDataGroupRepository,
									 TurDataGroupCategoryRepository turDataGroupCategoryRepository) {
		this.turDataGroupRepository = turDataGroupRepository;
		this.turDataGroupCategoryRepository = turDataGroupCategoryRepository;
	}

	@Operation(summary = "Machine Learning Data Group Category List")
	@GetMapping
	public List<TurDataGroupCategory> turDataGroupCategoryList(@PathVariable int dataGroupId) {
		return turDataGroupRepository.findById(dataGroupId).map(this.turDataGroupCategoryRepository::findByTurDataGroup)
				.orElse(new ArrayList<>());
	}

	@Operation(summary = "Show a Machine Learning Data Group Category")
	@GetMapping("/{id}")
	public TurDataGroupCategory turDataGroupCategoryGet(@PathVariable int dataGroupId, @PathVariable int id) {
		return this.turDataGroupCategoryRepository.findById(id).orElse(new TurDataGroupCategory());
	}

	@Operation(summary = "Update a Machine Learning Data Group Category")
	@PutMapping("/{id}")
	public TurDataGroupCategory turDataGroupCategoryUpdate(@PathVariable int dataGroupId, @PathVariable int id,
			@RequestBody TurMLCategory turMLCategory) {
		return this.turDataGroupCategoryRepository.findById(id).map(turDataGroupCategoryEdit -> {
			turDataGroupCategoryEdit.setTurMLCategory(turMLCategory);
			this.turDataGroupCategoryRepository.save(turDataGroupCategoryEdit);
			return turDataGroupCategoryEdit;
		}).orElse(new TurDataGroupCategory());

	}

	@Transactional
	@Operation(summary = "Delete a Machine Learning Data Group Category")
	@DeleteMapping("/{id}")
	public boolean turDataGroupCategoryDelete(@PathVariable int dataGroupId, @PathVariable int id) {
		this.turDataGroupCategoryRepository.deleteById(id);
		return true;
	}

	@Operation(summary = "Create a Machine Learning Data Group Category")
	@PostMapping
	public TurDataGroupCategory turDataGroupCategoryAdd(@PathVariable int dataGroupId,
			@RequestBody TurDataGroupCategory turDataGroupCategory) {
		return turDataGroupRepository.findById(dataGroupId).map(turDataGroup -> {
			turDataGroupCategory.setTurDataGroup(turDataGroup);
			this.turDataGroupCategoryRepository.save(turDataGroupCategory);
			return turDataGroupCategory;
		}).orElse(new TurDataGroupCategory());

	}
}

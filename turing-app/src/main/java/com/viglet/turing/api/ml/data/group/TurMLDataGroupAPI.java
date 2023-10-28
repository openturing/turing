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

import java.util.List;

import org.json.JSONException;
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

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.repository.storage.TurDataGroupRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ml/data/group")
@Tag(name = "Machine Learning Group", description = "Machine Learning Group API")
public class TurMLDataGroupAPI {

	@Autowired
	private TurDataGroupRepository turDataGroupRepository;

	@Operation(summary = "Machine Learning Data Group List")
	@GetMapping
	public List<TurDataGroup> turDataGroupList() throws JSONException {
		return this.turDataGroupRepository.findAll();
	}

	@Operation(summary = "Show a Machine Learning Data Group")
	@GetMapping("/{id}")
	public TurDataGroup turDataGroupGet(@PathVariable int id) throws JSONException {
		return this.turDataGroupRepository.findById(id).orElse(new TurDataGroup());
	}

	@Operation(summary = "Update a Machine Learning Data Group")
	@PutMapping("/{id}")
	public TurDataGroup turDataGroupUpdate(@PathVariable int id, @RequestBody TurDataGroup turDataGroup) {
		return this.turDataGroupRepository.findById(id).map(turDataGroupEdit -> {
			turDataGroupEdit.setName(turDataGroup.getName());
			turDataGroupEdit.setDescription(turDataGroup.getDescription());
			this.turDataGroupRepository.save(turDataGroupEdit);
			return turDataGroupEdit;
		}).orElse(new TurDataGroup());

	}

	@Transactional
	@Operation(summary = "Delete a Machine Learning Data Group")
	@DeleteMapping("/{id}")
	public boolean turDataGroupDelete(@PathVariable int id) {
		this.turDataGroupRepository.deleteById(id);
		return true;

	}

	@Operation(summary = "Create a Machine Learning Data Group")
	@PostMapping
	public TurDataGroup turDataGroupAdd(@RequestBody TurDataGroup turDataGroup) {
		this.turDataGroupRepository.save(turDataGroup);
		return turDataGroup;

	}
}

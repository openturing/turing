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

import com.viglet.turing.persistence.model.ml.TurMLVendor;
import com.viglet.turing.persistence.repository.ml.TurMLVendorRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ml/vendor")
@Tag(name ="Machine Learning Vendor", description = "Machine Learning Vendor API")
public class TurMLVendorAPI {

	@Autowired
	private TurMLVendorRepository turMLVendorRepository;

	@Operation(summary = "Machine Learning Vendor List")
	@GetMapping
	public List<TurMLVendor> turMLVendorList() {
		return this.turMLVendorRepository.findAll();
	}

	@Operation(summary = "Show a Machine Learning Vendor")
	@GetMapping("/{id}")
	public TurMLVendor turMLVendorGet(@PathVariable String id) {
		return this.turMLVendorRepository.findById(id).orElse(new TurMLVendor());
	}

	@Operation(summary = "Update a Machine Learning Vendor")
	@PutMapping("/{id}")
	public TurMLVendor turMLVendorUpdate(@PathVariable String id, @RequestBody TurMLVendor turMLVendor) {
		return this.turMLVendorRepository.findById(id).map(turMLVendorEdit -> {
			turMLVendorEdit.setDescription(turMLVendor.getDescription());
			turMLVendorEdit.setPlugin(turMLVendor.getPlugin());
			turMLVendorEdit.setTitle(turMLVendor.getTitle());
			turMLVendorEdit.setWebsite(turMLVendor.getWebsite());
			this.turMLVendorRepository.save(turMLVendorEdit);
			return turMLVendorEdit;
		}).orElse(new TurMLVendor());

	}

	@Transactional
	@Operation(summary = "Delete a Machine Learning Vendor")
	@DeleteMapping("/{id}")
	public boolean turMLVendorDelete(@PathVariable String id) {
		this.turMLVendorRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Machine Learning Vendor")
	@PostMapping
	public TurMLVendor turMLVendorAdd(@RequestBody TurMLVendor turMLVendor) {
		this.turMLVendorRepository.save(turMLVendor);
		return turMLVendor;

	}
}

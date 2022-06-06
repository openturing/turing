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

package com.viglet.turing.api.system;

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

import com.viglet.turing.persistence.model.system.TurLocale;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/locale")
@Tag(name = "Locale", description = "Locale API")
public class TurLocaleAPI {

	@Autowired
	private TurLocaleRepository turLocaleRepository;

	@Operation(summary = "Locale List")
	@GetMapping
	public List<TurLocale> turLocaleList() {
		return this.turLocaleRepository.findAll();
	}

	@Operation(summary = "Show a Locale")
	@GetMapping("/{id}")
	public TurLocale turLocaleGet(@PathVariable String id) {
		return this.turLocaleRepository.findById(id).orElse(new TurLocale());
	}

	@Operation(summary = "Update a Locle")
	@PutMapping("/{id}")
	public TurLocale turLocaleUpdate(@PathVariable String id, @RequestBody TurLocale turLocale) {
		return this.turLocaleRepository.findById(id).map(turLocaleEdit -> {
			turLocaleEdit.setEn(turLocale.getEn());
			turLocaleEdit.setPt(turLocale.getPt());
			this.turLocaleRepository.save(turLocaleEdit);
			return turLocaleEdit;
		}).orElse(new TurLocale());

	}

	@Transactional
	@Operation(summary = "Delete a Locale")
	@DeleteMapping("/{id}")
	public boolean turLocaleDelete(@PathVariable String id) {
		this.turLocaleRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Locale")
	@PostMapping
	public TurLocale turLocaleAdd(@RequestBody TurLocale turLocale) {
		this.turLocaleRepository.save(turLocale);
		return turLocale;

	}
}

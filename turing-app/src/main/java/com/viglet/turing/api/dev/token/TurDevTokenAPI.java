/*
 * Copyright (C) 2016-2023 the original author or authors.
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

package com.viglet.turing.api.dev.token;

import com.viglet.turing.persistence.model.dev.token.TurDevToken;
import com.viglet.turing.persistence.repository.dev.token.TurDevTokenRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dev/token")
@Tag(name = "Developer Token", description = "Developer Token API")
public class TurDevTokenAPI {

	@Autowired
	private TurDevTokenRepository turDevTokenRepository;

	@Operation(summary = "Developer Token List")
	@GetMapping
	public List<TurDevToken> turDevTokenList() {
		return this.turDevTokenRepository.findAll();
	}

	@Operation(summary = "Show a Developer Token")
	@GetMapping("/{id}")
	public TurDevToken turDevTokenGet(@PathVariable String id) {
		return this.turDevTokenRepository.findById(id).orElse(new TurDevToken());
	}

	@Operation(summary = "Update a Developer Token")
	@PutMapping("/{id}")
	public TurDevToken turDevTokenUpdate(@PathVariable String id, @RequestBody TurDevToken turDevToken) {
		return this.turDevTokenRepository.findById(id).map(turDevTokenEdit -> {
			turDevTokenEdit.setDescription(turDevToken.getDescription());
			turDevTokenEdit.setTitle(turDevToken.getTitle());
			this.turDevTokenRepository.save(turDevTokenEdit);
			return turDevTokenEdit;
		}).orElse(new TurDevToken());

	}

	@Transactional
	@Operation(summary = "Delete a Developer Token")
	@DeleteMapping("/{id}")
	public boolean turDevTokenDelete(@PathVariable String id) {
		this.turDevTokenRepository.deleteById(id);
		return true;
	}

	@Operation(summary = "Create a Developer Token")
	@PostMapping
	public TurDevToken turDevTokenAdd(@RequestBody TurDevToken turDevToken) {
		turDevToken.setToken(UUID.randomUUID().toString().replace("-","").substring(0,25));
		this.turDevTokenRepository.save(turDevToken);
		return turDevToken;

	}
}

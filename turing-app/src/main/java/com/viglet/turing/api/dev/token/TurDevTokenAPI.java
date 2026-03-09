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

import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.dto.dev.token.TurDevTokenDto;
import com.viglet.turing.persistence.mapper.dev.token.TurDevTokenMapper;
import com.viglet.turing.persistence.model.dev.token.TurDevToken;
import com.viglet.turing.persistence.repository.dev.token.TurDevTokenRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/dev/token")
@Tag(name = "Developer Token", description = "Developer Token API")
public class TurDevTokenAPI {

	private final TurDevTokenRepository turDevTokenRepository;
	private final TurDevTokenMapper turDevTokenMapper;

	public TurDevTokenAPI(TurDevTokenRepository turDevTokenRepository, TurDevTokenMapper turDevTokenMapper) {
		this.turDevTokenRepository = turDevTokenRepository;
		this.turDevTokenMapper = turDevTokenMapper;
	}

	@Operation(summary = "Developer Token List")
	@GetMapping
	public List<TurDevTokenDto> turDevTokenList() {
		return turDevTokenMapper
				.toDtoList(this.turDevTokenRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase()));
	}

	@Operation(summary = "Show a Developer Token")
	@GetMapping("/{id}")
	public TurDevTokenDto turDevTokenGet(@PathVariable String id) {
		return turDevTokenMapper.toDto(this.turDevTokenRepository.findById(id).orElse(new TurDevToken()));
	}

	@Operation(summary = "Update a Developer Token")
	@PutMapping("/{id}")
	public TurDevTokenDto turDevTokenUpdate(@PathVariable String id, @RequestBody TurDevTokenDto turDevTokenDto) {
		TurDevToken turDevToken = turDevTokenMapper.toEntity(turDevTokenDto);
		return this.turDevTokenRepository.findById(id).map(turDevTokenEdit -> {
			turDevTokenEdit.setDescription(turDevToken.getDescription());
			turDevTokenEdit.setTitle(turDevToken.getTitle());
			this.turDevTokenRepository.save(turDevTokenEdit);
			return turDevTokenMapper.toDto(turDevTokenEdit);
		}).orElse(new TurDevTokenDto());

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
	public TurDevTokenDto turDevTokenAdd(@RequestBody TurDevTokenDto turDevTokenDto) {
		TurDevToken turDevToken = turDevTokenMapper.toEntity(turDevTokenDto);
		turDevToken.setToken(UUID.randomUUID().toString().replace("-", "").substring(0, 25));
		this.turDevTokenRepository.save(turDevToken);
		return turDevTokenMapper.toDto(turDevToken);

	}
}

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
package com.viglet.turing.api.auth;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.dto.auth.TurRoleDto;
import com.viglet.turing.persistence.mapper.auth.TurRoleMapper;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;

@RestController
@RequestMapping("/api/v2/role")
@Tag(name = "Role", description = "Role API")
public class TurRoleAPI {

	private final TurRoleRepository turRoleRepository;
	private final TurRoleMapper turRoleMapper;

	public TurRoleAPI(TurRoleRepository turRoleRepository, TurRoleMapper turRoleMapper) {
		this.turRoleRepository = turRoleRepository;
		this.turRoleMapper = turRoleMapper;
	}

	@GetMapping
	public List<TurRoleDto> turRoleList() {
		return turRoleMapper.toDtoList(turRoleRepository.findAll());
	}

	@GetMapping("/{id}")
	public TurRoleDto turRoleEdit(@PathVariable String id) {
		return turRoleMapper
				.toDto(turRoleRepository.findById(id).orElse(new com.viglet.turing.persistence.model.auth.TurRole()));
	}

	@PutMapping("/{id}")
	public TurRoleDto turRoleUpdate(@PathVariable String id, @RequestBody TurRoleDto turRoleDto) {
		com.viglet.turing.persistence.model.auth.TurRole turRole = turRoleMapper.toEntity(turRoleDto);
		turRoleRepository.save(turRole);
		return turRoleMapper.toDto(turRole);
	}

	@Transactional
	@DeleteMapping("/{id}")
	public boolean turRoleDelete(@PathVariable String id) {
		turRoleRepository.deleteById(id);
		return true;
	}

	@PostMapping
	public TurRoleDto turRoleAdd(@RequestBody TurRoleDto turRoleDto) {
		com.viglet.turing.persistence.model.auth.TurRole turRole = turRoleMapper.toEntity(turRoleDto);

		turRoleRepository.save(turRole);

		return turRoleMapper.toDto(turRole);
	}

	@GetMapping("/model")
	public TurRoleDto turRoleStructure() {
		return new TurRoleDto();

	}

}

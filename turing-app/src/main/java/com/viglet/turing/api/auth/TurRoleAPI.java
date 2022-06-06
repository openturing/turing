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

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2/role")
@Tag( name = "Role", description = "Role API")
public class TurRoleAPI {

	@Autowired
	private TurRoleRepository turRoleRepository;

	@GetMapping
	public List<TurRole> turRoleList() {
		return turRoleRepository.findAll();
	}

	@GetMapping("/{id}")
	public TurRole turRoleEdit(@PathVariable String id) {
		return turRoleRepository.findById(id).orElse(new TurRole());
	}

	@PutMapping("/{id}")
	public TurRole turRoleUpdate(@PathVariable String id, @RequestBody TurRole turRole) {
		turRoleRepository.save(turRole);
		return turRole;
	}

	@Transactional
	@DeleteMapping("/{id}")
	public boolean turRoleDelete(@PathVariable String id) {
		turRoleRepository.delete(id);
		return true;
	}

	@PostMapping
	public TurRole turRoleAdd(@RequestBody TurRole turRole) {

		turRoleRepository.save(turRole);

		return turRole;
	}

	@GetMapping("/model")
	public TurRole turRoleStructure() {
		return new TurRole();

	}

}

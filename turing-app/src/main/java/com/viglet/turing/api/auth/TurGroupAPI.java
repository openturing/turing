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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2/group")
@Tag( name = "Group", description = "Group API")
public class TurGroupAPI {

	@Autowired
	private TurGroupRepository turGroupRepository;
	@Autowired
	private TurUserRepository turUserRepository;

	@GetMapping
	public List<TurGroup> turGroupList() {
		return turGroupRepository.findAll();
	}

	@GetMapping("/{id}")
	public TurGroup turGroupEdit(@PathVariable String id) {

		return turGroupRepository.findById(id).map(turGroup -> {
			List<TurGroup> turGroups = new ArrayList<>();
			turGroups = new ArrayList<>();
			turGroups.add(turGroup);
			turGroup.setTurUsers(turUserRepository.findByTurGroupsIn(turGroups));
			return turGroup;
		}).orElse(new TurGroup());
	}

	@PutMapping("/{id}")
	public TurGroup turGroupUpdate(@PathVariable String id, @RequestBody TurGroup turGroup) {
		turGroupRepository.save(turGroup);
		return turGroupRepository.findById(turGroup.getId()).map(turGroupRepos -> {
			List<TurGroup> turGroups = new ArrayList<>();
			turGroups.add(turGroup);
			Set<TurUser> turUsers = turUserRepository.findByTurGroupsIn(turGroups);
			for (TurUser turUser : turUsers) {
				turUser.getTurGroups().remove(turGroupRepos);
				turUserRepository.saveAndFlush(turUser);
			}
			for (TurUser turUser : turGroup.getTurUsers()) {
				TurUser turUserRepos = turUserRepository.findByUsername(turUser.getUsername());
				turUserRepos.getTurGroups().add(turGroup);
				turUserRepository.saveAndFlush(turUserRepos);
			}

			return turGroup;
		}).orElse(new TurGroup());
	}

	@Transactional
	@DeleteMapping("/{id}")
	public boolean turGroupDelete(@PathVariable String id) {
		turGroupRepository.delete(id);
		return true;
	}

	@PostMapping
	public TurGroup turGroupAdd(@RequestBody TurGroup turGroup) {

		turGroupRepository.save(turGroup);

		return turGroup;
	}

	@GetMapping("/model")
	public TurGroup turGroupStructure() {
		return new TurGroup();

	}

}

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

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.dto.auth.TurGroupDto;
import com.viglet.turing.persistence.mapper.auth.TurGroupMapper;
import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;

@RestController
@RequestMapping("/api/v2/group")
@Tag(name = "Group", description = "Group API")
public class TurGroupAPI {

	private final TurGroupRepository turGroupRepository;
	private final TurUserRepository turUserRepository;
	private final TurGroupMapper turGroupMapper;

	public TurGroupAPI(TurGroupRepository turGroupRepository, TurUserRepository turUserRepository,
			TurGroupMapper turGroupMapper) {
		this.turGroupRepository = turGroupRepository;
		this.turUserRepository = turUserRepository;
		this.turGroupMapper = turGroupMapper;
	}

	@GetMapping
	public List<TurGroupDto> turGroupList() {
		return turGroupMapper.toDtoList(turGroupRepository.findAll());
	}

	@GetMapping("/{id}")
	public TurGroupDto turGroupEdit(@PathVariable String id) {

		TurGroup turGroup = turGroupRepository.findById(id).map(turGroupEdit -> {
			List<TurGroup> turGroups = new ArrayList<>();
			turGroups.add(turGroupEdit);
			turGroupEdit.setTurUsers(turUserRepository.findByTurGroupsIn(turGroups));
			return turGroupEdit;
		}).orElse(new TurGroup());
		return turGroupMapper.toDto(turGroup);
	}

	@PutMapping("/{id}")
	public TurGroupDto turGroupUpdate(@PathVariable String id, @RequestBody TurGroupDto turGroupDto) {
		TurGroup turGroup = turGroupMapper.toEntity(turGroupDto);
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

			return turGroupMapper.toDto(turGroup);
		}).orElse(new TurGroupDto());
	}

	@Transactional
	@DeleteMapping("/{id}")
	public boolean turGroupDelete(@PathVariable String id) {
		turGroupRepository.deleteById(id);
		return true;
	}

	@PostMapping
	public TurGroupDto turGroupAdd(@RequestBody TurGroupDto turGroupDto) {
		TurGroup turGroup = turGroupMapper.toEntity(turGroupDto);

		turGroupRepository.save(turGroup);

		return turGroupMapper.toDto(turGroup);
	}

	@GetMapping("/model")
	public TurGroupDto turGroupStructure() {
		return new TurGroupDto();

	}

}

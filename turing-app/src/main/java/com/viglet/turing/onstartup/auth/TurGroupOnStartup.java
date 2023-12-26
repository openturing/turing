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
package com.viglet.turing.onstartup.auth;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
public class TurGroupOnStartup {
	private final TurGroupRepository turGroupRepository;

	private final TurRoleRepository turRoleRepository;

	@Inject
	public TurGroupOnStartup(TurGroupRepository turGroupRepository, TurRoleRepository turRoleRepository) {
		this.turGroupRepository = turGroupRepository;
		this.turRoleRepository = turRoleRepository;
	}

	public void createDefaultRows() {

		if (turGroupRepository.findAll().isEmpty()) {
			TurRole adminRole = turRoleRepository.findByName("ROLE_ADMIN");

			TurGroup adminGroup = new TurGroup();
			adminGroup.setName("Administrator");
			adminGroup.setDescription("Administrator Group");
			adminGroup.setTurRoles(Collections.singletonList(adminRole));
			turGroupRepository.save(adminGroup);

			TurRole userRole = turRoleRepository.findByName("ROLE_USER");

			TurGroup userGroup = new TurGroup();
			userGroup.setName("User");
			userGroup.setDescription("User Group");
			userGroup.setTurRoles(Collections.singletonList(userRole));
			turGroupRepository.save(userGroup);
		}
	}
}

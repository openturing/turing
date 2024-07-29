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
import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;

@Component
public class TurUserOnStartup {
	private final PasswordEncoder passwordEncoder;
	private final TurUserRepository turUserRepository;
	private final TurGroupRepository turGroupRepository;

	@Inject
	public TurUserOnStartup(PasswordEncoder passwordEncoder,
							TurUserRepository turUserRepository,
							TurGroupRepository turGroupRepository) {
		this.passwordEncoder = passwordEncoder;
		this.turUserRepository = turUserRepository;
		this.turGroupRepository = turGroupRepository;
	}

	public void createDefaultRows() {

		if (turUserRepository.findAll().isEmpty()) {
			
			TurGroup turGroup = turGroupRepository.findByName("Administrator");
			TurUser turUser = new TurUser();

			turUser.setEmail("admin@localhost.local");
			turUser.setFirstName("Admin");
			turUser.setLastLogin(new Date());
			turUser.setLastName("Administrator");
			turUser.setLoginTimes(0);
			turUser.setPassword(passwordEncoder.encode("admin"));
			turUser.setRealm("default");
			turUser.setUsername("admin");
			turUser.setEnabled(1);

			turUser.setTurGroups(Collections.singletonList(turGroup));
			
			turUserRepository.save(turUser);
		}

	}
}

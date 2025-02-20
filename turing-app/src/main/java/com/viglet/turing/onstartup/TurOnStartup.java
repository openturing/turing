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
package com.viglet.turing.onstartup;

import com.google.inject.Inject;
import com.viglet.turing.onstartup.auth.TurGroupOnStartup;
import com.viglet.turing.onstartup.auth.TurRoleOnStartup;
import com.viglet.turing.onstartup.auth.TurUserOnStartup;
import com.viglet.turing.onstartup.llm.TurLLMVendorOnStartup;
import com.viglet.turing.onstartup.se.TurSEVendorOnStartup;
import com.viglet.turing.onstartup.store.TurStoreVendorOnStartup;
import com.viglet.turing.onstartup.system.TurConfigVarOnStartup;
import com.viglet.turing.onstartup.system.TurLocaleOnStartup;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Transactional
public class TurOnStartup implements ApplicationRunner {
	public static final String TURING_ADMIN_PASSWORD = "TURING_ADMIN_PASSWORD";
	public static final String FIRST_TIME = "FIRST_TIME";
	public static final int PASSWORD_MINIMUM_SIZE = 6;
	private final TurConfigVarRepository turConfigVarRepository;
	private final TurLocaleOnStartup turLocaleOnStartup;
	private final TurSEVendorOnStartup turSEVendorOnStartup;
	private final TurLLMVendorOnStartup turLLMVendorOnStartup;
	private final TurStoreVendorOnStartup turStoreVendorOnStartup;
	private final TurConfigVarOnStartup turConfigVarOnStartup;
	private final TurUserOnStartup turUserOnStartup;
	private final TurGroupOnStartup turGroupOnStartup;
	private final TurRoleOnStartup turRoleOnStartup;

	@Inject
	public TurOnStartup(TurConfigVarRepository turConfigVarRepository,
                        TurLocaleOnStartup turLocaleOnStartup,
                        TurSEVendorOnStartup turSEVendorOnStartup,
						TurLLMVendorOnStartup turLLMVendorOnStartup, TurStoreVendorOnStartup turStoreVendorOnStartup,
                        TurConfigVarOnStartup turConfigVarOnStartup,
                        TurUserOnStartup turUserOnStartup,
                        TurGroupOnStartup turGroupOnStartup,
                        TurRoleOnStartup turRoleOnStartup) {
		this.turConfigVarRepository = turConfigVarRepository;
		this.turLocaleOnStartup = turLocaleOnStartup;
		this.turSEVendorOnStartup = turSEVendorOnStartup;
        this.turLLMVendorOnStartup = turLLMVendorOnStartup;
        this.turStoreVendorOnStartup = turStoreVendorOnStartup;
        this.turConfigVarOnStartup = turConfigVarOnStartup;
		this.turUserOnStartup = turUserOnStartup;
		this.turGroupOnStartup = turGroupOnStartup;
		this.turRoleOnStartup = turRoleOnStartup;
	}

	@Override
	public void run(ApplicationArguments arg0) {
		if (turConfigVarRepository.findById(FIRST_TIME).isEmpty()) {
			String turAdminPassword = System.getenv(TURING_ADMIN_PASSWORD);

			if (turAdminPassword != null && turAdminPassword.trim().length() >= PASSWORD_MINIMUM_SIZE) {
				log.info("First Time Configuration ...");

				turLocaleOnStartup.createDefaultRows();
				turRoleOnStartup.createDefaultRows();
				turGroupOnStartup.createDefaultRows();
				turUserOnStartup.createDefaultRows(turAdminPassword);
				turSEVendorOnStartup.createDefaultRows();
				turLLMVendorOnStartup.createDefaultRows();
				turStoreVendorOnStartup.createDefaultRows();

				turConfigVarOnStartup.createDefaultRows();

				log.info("Configuration finished.");
			} else {
				log.info("It is necessary to define the Operating System Variable {} " +
						"with a password of at least {} characters. Please try again.",
						TURING_ADMIN_PASSWORD, PASSWORD_MINIMUM_SIZE);
			}
		}

	}

}

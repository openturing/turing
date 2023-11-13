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

import com.viglet.turing.onstartup.auth.TurGroupOnStartup;
import com.viglet.turing.onstartup.auth.TurRoleOnStartup;
import com.viglet.turing.onstartup.auth.TurUserOnStartup;
import com.viglet.turing.onstartup.converse.TurConverseAgentOnStartup;
import com.viglet.turing.onstartup.ml.TurMLInstanceOnStartup;
import com.viglet.turing.onstartup.ml.TurMLVendorOnStartup;
import com.viglet.turing.onstartup.nlp.*;
import com.viglet.turing.onstartup.se.TurSEInstanceOnStartup;
import com.viglet.turing.onstartup.se.TurSEVendorOnStartup;
import com.viglet.turing.onstartup.sn.TurSNSiteOnStartup;
import com.viglet.turing.onstartup.sn.TurSNSourceTypeOnStartup;
import com.viglet.turing.onstartup.storage.TurDataGroupStartup;
import com.viglet.turing.onstartup.system.TurConfigVarOnStartup;
import com.viglet.turing.onstartup.system.TurLocaleOnStartup;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.invoke.MethodHandles;
@Slf4j
@Component
@Transactional
public class TurOnStartup implements ApplicationRunner {
	@Autowired
	private TurConfigVarRepository turConfigVarRepository;
	@Autowired
	private TurLocaleOnStartup turLocaleOnStartup;
	@Autowired
	private TurNLPVendorOnStartup turNLPVendorOnStartup;
	@Autowired
	private TurNLPEntityOnStartup turNLPEntityOnStartup;
	@Autowired
	private TurNLPVendorEntityOnStartup turNLPVendorEntityOnStartup;
	@Autowired
	private TurNLPFeatureOnStartup turNLPFeatureOnStartup;
	@Autowired
	private TurNLPInstanceOnStartup turNLPInstanceOnStartup;
	@Autowired
	private TurMLVendorOnStartup turMLVendorOnStartup;
	@Autowired
	private TurMLInstanceOnStartup turMLInstanceOnStartup;
	@Autowired
	private TurSEVendorOnStartup turSEVendorOnStartup;
	@Autowired
	private TurSEInstanceOnStartup turSEInstanceOnStartup;
	@Autowired
	private TurDataGroupStartup turDataGroupStartup;
	@Autowired
	private TurConfigVarOnStartup turConfigVarOnStartup;
	@Autowired
	private TurSNSiteOnStartup turSNSiteOnStartup;
	@Autowired
	private TurSNSourceTypeOnStartup turSNSourceTypeOnStartup;
	@Autowired
	private TurConverseAgentOnStartup turConverseAgentOnStartup;
	@Autowired
	private TurUserOnStartup turUserOnStartup;
	@Autowired
	private TurGroupOnStartup turGroupOnStartup;
	@Autowired
	private TurRoleOnStartup turRoleOnStartup;
	
	@Override
	public void run(ApplicationArguments arg0) throws Exception {
		final String FIRST_TIME = "FIRST_TIME";

		if (turConfigVarRepository.findById(FIRST_TIME).isEmpty()) {

			log.info("First Time Configuration ...");

			turLocaleOnStartup.createDefaultRows();
			turRoleOnStartup.createDefaultRows();
			turGroupOnStartup.createDefaultRows();
			turUserOnStartup.createDefaultRows();		
			turNLPFeatureOnStartup.createDefaultRows();
			turNLPVendorOnStartup.createDefaultRows();
			turMLVendorOnStartup.createDefaultRows();
			turSEVendorOnStartup.createDefaultRows();

			turNLPEntityOnStartup.createDefaultRows();
			turNLPVendorEntityOnStartup.createDefaultRows();
			turNLPInstanceOnStartup.createDefaultRows();
			turMLInstanceOnStartup.createDefaultRows();
			turSEInstanceOnStartup.createDefaultRows();
			turDataGroupStartup.createDefaultRows();
			turSNSiteOnStartup.createDefaultRows();
			turSNSourceTypeOnStartup.createDefaultRows();
			turConverseAgentOnStartup.createDefaultRows();
			
			turConfigVarOnStartup.createDefaultRows();

			log.info("Configuration finished.");
		}

	}

}
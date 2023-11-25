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

package com.viglet.turing.onstartup.sn;

import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

import com.viglet.turing.sn.template.TurSNTemplate;

import java.util.Locale;

@Component
@Transactional
public class TurSNSiteOnStartup {
	private final TurSNSiteRepository turSNSiteRepository;
	private final TurSEInstanceRepository turSEInstanceRepository;
	private final TurNLPVendorRepository turNLPVendorRepository;
	private final TurSNTemplate turSNTemplate;

	public TurSNSiteOnStartup(TurSNSiteRepository turSNSiteRepository,
							  TurSEInstanceRepository turSEInstanceRepository,
							  TurNLPVendorRepository turNLPVendorRepository,
							  TurSNTemplate turSNTemplate) {
		this.turSNSiteRepository = turSNSiteRepository;
		this.turSEInstanceRepository = turSEInstanceRepository;
		this.turNLPVendorRepository = turNLPVendorRepository;
		this.turSNTemplate = turSNTemplate;
	}

	public void createDefaultRows() {

		if (turSNSiteRepository.findAll().isEmpty()) {
			TurSNSite turSNSite = new TurSNSite();
			turSNSite.setName("Sample");
			turSNSite.setDescription("Semantic Sample Site");
			turSNSite.setTurNLPVendor(turNLPVendorRepository.findAll()
					.stream().findFirst().orElse(new TurNLPVendor()));
			turSNSite.setTurSEInstance(turSEInstanceRepository.findAll()
					.stream().findFirst().orElse(new TurSEInstance()));
						
			turSNSiteRepository.save(turSNSite);
			turSNTemplate.createSNSite(turSNSite, "admin", Locale.US);
		}
	}
}

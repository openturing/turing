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
package com.viglet.turing.onstartup.se;

import com.google.inject.Inject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;

@Component
@Transactional
public class TurSEVendorOnStartup {

	private final TurSEVendorRepository turSEVendorRepository;

	@Inject
	public TurSEVendorOnStartup(TurSEVendorRepository turSEVendorRepository) {
		this.turSEVendorRepository = turSEVendorRepository;
	}

	public void createDefaultRows() {

		if (turSEVendorRepository.findAll().isEmpty()) {
			TurSEVendor turSEVendor = new TurSEVendor();
			turSEVendor.setId("SOLR");
			turSEVendor.setDescription("Apache Solr");
			turSEVendor.setPlugin("");
			turSEVendor.setTitle("Apache Solr");
			turSEVendor.setWebsite("http://lucene.apache.org/solr");
			turSEVendorRepository.save(turSEVendor);
		}
	}
}

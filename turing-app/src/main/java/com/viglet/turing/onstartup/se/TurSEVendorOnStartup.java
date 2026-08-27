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

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;

@Component
@Transactional
public class TurSEVendorOnStartup {

	private final TurSEVendorRepository turSEVendorRepository;

	public TurSEVendorOnStartup(TurSEVendorRepository turSEVendorRepository) {
		this.turSEVendorRepository = turSEVendorRepository;
	}

	/**
	 * Seeds the search-engine vendor catalog, one row at a time.
	 *
	 * <p>This used to be guarded by {@code findAll().isEmpty()}, which made the
	 * whole catalog a fresh-install-only write: a vendor added in a later release
	 * — T1031's {@code CONTENTAI} is the first — never reached an existing
	 * installation, so the engine it names could not be selected on any database
	 * that had ever started the application. Checking per id fixes that and stays
	 * idempotent; an operator's edits to a row's title or description are left
	 * alone, because a row that exists is never rewritten.
	 */
	public void createDefaultRows() {
		createIfAbsent("SOLR", "Apache Solr", "solr", "Apache Solr",
				"https://solr.apache.org");
		createIfAbsent("LUCENE", "Apache Lucene", "lucene", "Apache Lucene",
				"https://lucene.apache.org");
		createIfAbsent("ES", "Elasticsearch", "elasticsearch", "Elasticsearch",
				"https://www.elastic.co");
		createIfAbsent("CONTENTAI", "Adobe AEM Content AI (federated, read-only)", "contentai",
				"Adobe AEM Content AI",
				"https://experienceleague.adobe.com/en/docs/experience-manager-content-ai/using/introduction");
	}

	private void createIfAbsent(String id, String description, String plugin, String title,
			String website) {
		if (turSEVendorRepository.existsById(id)) {
			return;
		}
		TurSEVendor vendor = new TurSEVendor();
		vendor.setId(id);
		vendor.setDescription(description);
		vendor.setPlugin(plugin);
		vendor.setTitle(title);
		vendor.setWebsite(website);
		turSEVendorRepository.save(vendor);
	}
}

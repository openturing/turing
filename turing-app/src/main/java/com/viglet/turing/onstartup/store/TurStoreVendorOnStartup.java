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
package com.viglet.turing.onstartup.store;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.store.TurStoreVendorRepository;

@Component
@Transactional
public class TurStoreVendorOnStartup {

	private final TurStoreVendorRepository turStoreVendorRepository;

	public TurStoreVendorOnStartup(TurStoreVendorRepository turStoreVendorRepository) {
		this.turStoreVendorRepository = turStoreVendorRepository;
	}

	/**
	 * Seeds the vector-store vendor catalog, one row at a time.
	 *
	 * <p>This used to be guarded by {@code findAll().isEmpty()}, which made the
	 * whole catalog a fresh-install-only write: a vendor added in a later release
	 * reached a database that had never started the application and no other, so
	 * the store plugin it names could not be selected on any existing
	 * installation. T1031 hit exactly that with the search-engine vendors, where
	 * {@code CONTENTAI} was the first row added since the table existed; T1043
	 * removes the same guard here before it costs a release. Checking per id stays
	 * idempotent and never rewrites a row an operator has edited.
	 *
	 * @since 2026.3.4
	 */
	public void createDefaultRows() {
		createIfAbsent("CHROMA", "Chroma", "chroma", "Chroma",
				"https://www.trychroma.com/");
		createIfAbsent("MILVUS", "Milvus", "milvus", "Milvus",
				"https://milvus.io/");
		createIfAbsent("PGVECTOR", "PGVector", "pgvector", "PGVector",
				"https://github.com/pgvector/pgvector");
		createIfAbsent("LUCENE", "Apache Lucene (embedded vector store)", "lucene", "Lucene",
				"https://lucene.apache.org/");
	}

	private void createIfAbsent(String id, String description, String plugin, String title,
			String website) {
		if (turStoreVendorRepository.existsById(id)) {
			return;
		}
		TurStoreVendor vendor = new TurStoreVendor();
		vendor.setId(id);
		vendor.setDescription(description);
		vendor.setPlugin(plugin);
		vendor.setTitle(title);
		vendor.setWebsite(website);
		turStoreVendorRepository.save(vendor);
	}
}

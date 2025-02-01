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

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.store.TurStoreVendorRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class TurStoreVendorOnStartup {

	private final TurStoreVendorRepository turStoreVendorRepository;

	@Inject
	public TurStoreVendorOnStartup(TurStoreVendorRepository turStoreVendorRepository) {
		this.turStoreVendorRepository = turStoreVendorRepository;
	}

	public void createDefaultRows() {

		if (turStoreVendorRepository.findAll().isEmpty()) {
			TurStoreVendor chroma = new TurStoreVendor();
			chroma.setId("CHROMA");
			chroma.setDescription("Chroma");
			chroma.setPlugin("");
			chroma.setTitle("Chroma");
			chroma.setWebsite("https://www.trychroma.com/");
			turStoreVendorRepository.save(chroma);

			TurStoreVendor milvus = new TurStoreVendor();
			milvus.setId("MILVUS");
			milvus.setDescription("Milvus");
			milvus.setPlugin("");
			milvus.setTitle("Milvus");
			milvus.setWebsite("https://milvus.io/");
			turStoreVendorRepository.save(milvus);
		}
	}
}

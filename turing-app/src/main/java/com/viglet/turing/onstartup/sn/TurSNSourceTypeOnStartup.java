/*
 * Copyright (C) 2016-2023 the original author or authors. 
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.sn.source.TurSNSourceType;
import com.viglet.turing.persistence.repository.sn.source.TurSNSourceTypeRepository;

@Component
public class TurSNSourceTypeOnStartup {
	
	@Autowired
	private TurSNSourceTypeRepository turSNSourceTypeRepository;
	public void createDefaultRows() {

		if (turSNSourceTypeRepository.findAll().isEmpty()) {

			TurSNSourceType turSNSourceType = new TurSNSourceType();

			turSNSourceType.setId("SITEMAP");
			turSNSourceType.setName("Sitemap");
			turSNSourceType.setDescription("It uses sitemap.xml to crawler the website");
									
			turSNSourceTypeRepository.save(turSNSourceType);
		}
	}
}
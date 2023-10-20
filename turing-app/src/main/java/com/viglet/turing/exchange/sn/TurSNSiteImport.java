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

package com.viglet.turing.exchange.sn;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

@Component
public class TurSNSiteImport {
	@Autowired
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurSEInstanceRepository turSEInstanceRepository;
	@Autowired
	private TurSNSiteFieldRepository turSNSiteFieldRepository;

	public void importSNSite(TurExchange turExchange){
		for (TurSNSiteExchange turSNSiteExchange : turExchange.getSnSites()) {
			if (turSNSiteRepository.findById(turSNSiteExchange.getId()).isEmpty()) {
				
				TurSNSite turSNSite = new TurSNSite();
				turSNSite.setDefaultDateField(turSNSiteExchange.getDefaultDateField());
				turSNSite.setDefaultDescriptionField(turSNSiteExchange.getDefaultDescriptionField());
				turSNSite.setDefaultImageField(turSNSiteExchange.getDefaultImageField());
				turSNSite.setDefaultTextField(turSNSiteExchange.getDefaultTextField());
				turSNSite.setDefaultTitleField(turSNSiteExchange.getDefaultTitleField());
				turSNSite.setDefaultURLField(turSNSiteExchange.getDefaultURLField());
				turSNSite.setDescription(turSNSiteExchange.getDescription());
				turSNSite.setFacet(boolToInteger(turSNSiteExchange.getFacet()));
				turSNSite.setHl(boolToInteger( turSNSiteExchange.getHl()));
				turSNSite.setHlPost(turSNSiteExchange.getHlPost());
				turSNSite.setHlPre(turSNSiteExchange.getHlPre());
				turSNSite.setId(turSNSiteExchange.getId());
				turSNSite.setItemsPerFacet(turSNSiteExchange.getItemsPerFacet());
				turSNSite.setMlt(boolToInteger( turSNSiteExchange.getMlt()));
				turSNSite.setName(turSNSiteExchange.getName());
				turSNSite.setRowsPerPage(turSNSiteExchange.getRowsPerPage());
				turSNSite.setThesaurus(boolToInteger(turSNSiteExchange.getThesaurus()));
				turSNSite.setTurSEInstance(turSEInstanceRepository.findById(turSNSiteExchange.getTurSEInstance()).orElse(null));

				turSNSiteRepository.save(turSNSite);

				saveSNSiteFields(turSNSiteExchange, turSNSite);
			}
		}
	}

	private void saveSNSiteFields(TurSNSiteExchange turSNSiteExchange, TurSNSite turSNSite) {
		for (TurSNSiteField turSNSiteField : turSNSiteExchange.getTurSNSiteFields()) {
			turSNSiteField.setTurSNSite(turSNSite);
			turSNSiteFieldRepository.save(turSNSiteField);
		}
	}

	private Integer boolToInteger(boolean bool) {
		return bool ?  1 :  0;
	}
}

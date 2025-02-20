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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSiteExchange {

	private String id;

	private String name;

	private String description;

	private String language;

	private String core;

	private int rowsPerPage;

	private boolean facet;

	private int itemsPerFacet;

	private boolean hl;

	private String hlPre;

	private String hlPost;

	private boolean mlt;

	private boolean thesaurus;

	private String defaultTitleField;

	private String defaultTextField;

	private String defaultDescriptionField;

	private String defaultDateField;

	private String defaultImageField;

	private String defaultURLField;

	private String turSEInstance;

	private Set<TurSNSiteField> turSNSiteFields;

	public TurSNSiteExchange(TurSNSite turSNSite) {
		this.setDefaultDateField(turSNSite.getDefaultDateField());
		this.setDefaultDescriptionField(turSNSite.getDefaultDescriptionField());
		this.setDefaultImageField(turSNSite.getDefaultImageField());
		this.setDefaultTextField(turSNSite.getDefaultTextField());
		this.setDefaultTitleField(turSNSite.getDefaultTitleField());
		this.setDefaultURLField(turSNSite.getDefaultURLField());
		this.setDescription(turSNSite.getDescription());
		this.setFacet(turSNSite.getFacet() == 1);
		this.setHl(turSNSite.getHl() == 1);
		this.setHlPost(turSNSite.getHlPost());
		this.setHlPre(turSNSite.getHlPre());
		this.setId(turSNSite.getId());
		this.setItemsPerFacet(turSNSite.getItemsPerFacet());
		this.setMlt(turSNSite.getMlt() == 1);
		this.setName(turSNSite.getName());
		this.setRowsPerPage(turSNSite.getRowsPerPage());
		this.setThesaurus(turSNSite.getThesaurus() == 1);
		if (turSNSite.getTurSEInstance() != null) {
			this.setTurSEInstance(turSNSite.getTurSEInstance().getId());
		}
		this.setTurSNSiteFields(turSNSite.getTurSNSiteFields());
	}

    public boolean getHl() {
		return hl;
	}
}
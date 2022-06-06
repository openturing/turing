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

package com.viglet.turing.persistence.model.sn.locale;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;

/**
 * The persistent class for the turSNSiteLocale database table.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Entity
@Table(name = "turSNSiteLocale")
@NamedQuery(name = "TurSNSiteLocale.findAll", query = "SELECT snsl FROM TurSNSiteLocale snsl")
public class TurSNSiteLocale implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = false, length = 5)
	private String language;

	@Column(nullable = false, length = 50)
	private String core;

	// bi-directional many-to-one association to TurNLPInstance
	@ManyToOne
	@JoinColumn(name = "nlp_instance_id", nullable = true)
	private TurNLPInstance turNLPInstance;

	// bi-directional many-to-one association to TurSNSite
	@ManyToOne
	@JoinColumn(name = "sn_site_id", nullable = false)
	private TurSNSite turSNSite;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getCore() {
		return core;
	}

	public void setCore(String core) {
		this.core = core;
	}

	public TurNLPInstance getTurNLPInstance() {
		return turNLPInstance;
	}

	public void setTurNLPInstance(TurNLPInstance turNLPInstance) {
		this.turNLPInstance = turNLPInstance;
	}

	public TurSNSite getTurSNSite() {
		return turSNSite;
	}

	public void setTurSNSite(TurSNSite turSNSite) {
		this.turSNSite = turSNSite;
	}
}
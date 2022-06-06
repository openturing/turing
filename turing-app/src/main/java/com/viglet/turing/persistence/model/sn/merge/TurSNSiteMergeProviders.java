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

package com.viglet.turing.persistence.model.sn.merge;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.viglet.turing.persistence.model.sn.TurSNSite;

/**
 * The persistent class for the turSNSiteMerge database table.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Entity
@Table(name = "turSNSiteMergeProviders")
@NamedQuery(name = "TurSNSiteMerge.findAll", query = "SELECT snsmp FROM TurSNSiteMergeProviders snsmp")
public class TurSNSiteMergeProviders implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	// bi-directional many-to-one association to TurSNSite
	@ManyToOne
	@JoinColumn(name = "sn_site_id", nullable = false)
	private TurSNSite turSNSite;

	@Column(nullable = false, length = 5)
	private String locale;

	@Column(nullable = true, length = 255)
	private String description;
	
	@Column(nullable = false, length = 50)
	private String providerFrom;

	@Column(nullable = false, length = 50)
	private String providerTo;

	@Column(nullable = false, length = 50)
	private String relationFrom;

	@Column(nullable = false, length = 50)
	private String relationTo;

	// bi-directional many-to-one association to turSNSiteSpotlightDocuments
	@OneToMany(mappedBy = "turSNSiteMergeProviders", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteMergeProvidersField> overwrittenFields = new HashSet<>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public TurSNSite getTurSNSite() {
		return turSNSite;
	}

	public void setTurSNSite(TurSNSite turSNSite) {
		this.turSNSite = turSNSite;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getProviderFrom() {
		return providerFrom;
	}

	public void setProviderFrom(String providerFrom) {
		this.providerFrom = providerFrom;
	}

	public String getProviderTo() {
		return providerTo;
	}

	public void setProviderTo(String providerTo) {
		this.providerTo = providerTo;
	}

	public String getRelationFrom() {
		return relationFrom;
	}

	public void setRelationFrom(String relationFrom) {
		this.relationFrom = relationFrom;
	}

	public String getRelationTo() {
		return relationTo;
	}

	public void setRelationTo(String relationTo) {
		this.relationTo = relationTo;
	}

	public Set<TurSNSiteMergeProvidersField> getOverwrittenFields() {
		return overwrittenFields;
	}

	public void setOverwrittenFields(Set<TurSNSiteMergeProvidersField> overwrittenFields) {
		this.overwrittenFields = overwrittenFields;
	}

}
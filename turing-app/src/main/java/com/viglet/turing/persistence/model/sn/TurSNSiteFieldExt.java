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

package com.viglet.turing.persistence.model.sn;

import java.io.Serializable;
import javax.persistence.*;

import org.hibernate.annotations.GenericGenerator;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.sn.TurSNFieldType;

/**
 * The persistent class for the turSNSiteFieldExt database table.
 * 
 */
@Entity
@Table(name = "turSNSiteFieldExt")
@NamedQuery(name = "TurSNSiteFieldExt.findAll", query = "SELECT snsfe FROM TurSNSiteFieldExt snsfe")
public class TurSNSiteFieldExt implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = false)
	private String externalId;
	
	@Column(nullable = false, length = 50)
	private String name;
	
	@Column(nullable = true, length = 255)
	private String description;
	
	@Column(nullable = true, length = 50)
	private String facetName;

	@Column(nullable = false)
	private TurSNFieldType snType;

	@Column(nullable = false)
	private TurSEFieldType type;

	@Column(nullable = true)
	private int multiValued;
	
	@Column(nullable = true)
	private int facet;
	
	@Column(nullable = true)
	private int hl;

	@Column(nullable = true)
	private int mlt;

	@Column(nullable = true)
	private int enabled;

	@Column(nullable = true)
	private int required;
	
	@Column(nullable = true, length = 50)
	private String defaultValue;
	
	@Column(nullable = true)
	private int nlp;
	
	// bi-directional many-to-one association to TurSNSite
	@ManyToOne
	@JoinColumn(name = "sn_site_id", nullable = false)
	@JsonBackReference (value="turSNSiteFieldExt-turSNSite")
	private TurSNSite turSNSite;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getFacetName() {
		return facetName;
	}

	public void setFacetName(String facetName) {
		this.facetName = facetName;
	}

	public TurSEFieldType getType() {
		return type;
	}

	public void setType(TurSEFieldType type) {
		this.type = type;
	}

	public int getMultiValued() {
		return multiValued;
	}

	public void setMultiValued(int multiValued) {
		this.multiValued = multiValued;
	}

	public TurSNSite getTurSNSite() {
		return turSNSite;
	}

	public void setTurSNSite(TurSNSite turSNSite) {
		this.turSNSite = turSNSite;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	public int getFacet() {
		return facet;
	}

	public void setFacet(int facet) {
		this.facet = facet;
	}

	public int getHl() {
		return hl;
	}

	public void setHl(int hl) {
		this.hl = hl;
	}

	public TurSNFieldType getSnType() {
		return snType;
	}

	public void setSnType(TurSNFieldType snType) {
		this.snType = snType;
	}

	public int getMlt() {
		return mlt;
	}

	public void setMlt(int mlt) {
		this.mlt = mlt;
	}

	public int getEnabled() {
		return enabled;
	}

	public void setEnabled(int enabled) {
		this.enabled = enabled;
	}

	public String getExternalId() {
		return externalId;
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public int getRequired() {
		return required;
	}

	public void setRequired(int required) {
		this.required = required;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	public int getNlp() {
		return nlp;
	}

	public void setNlp(int nlp) {
		this.nlp = nlp;
	}

	
}
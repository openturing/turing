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

package com.viglet.turing.persistence.model.nlp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.nlp.term.TurTerm;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.*;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * The persistent class for the turEntities database table.
 * 
 */
@Entity
@Table(name = "turNLPEntity")
@NamedQuery(name = "TurNLPEntity.findAll", query = "SELECT ne FROM TurNLPEntity ne")
@JsonIgnoreProperties({ "turNLPInstanceEntities", "turNLPVendorEntities" } )
public class TurNLPEntity implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "internal_name", nullable = false, length = 50)
	private String internalName;
	
	@Column(name = "collection_name", nullable = false, length = 50)
	private String collectionName;

	@Column(nullable = false, length = 255)
	private String description;


	@Column(nullable = false)
	private int local;

	@Column(nullable = false, length = 50)
	private String name;
	
	@Column
	private int enabled;		

	public TurNLPEntity() {
		super();
	}
	public TurNLPEntity(String internalName, String name, String description,  String collectionName, int local, int enabled) {
		super();
		this.collectionName = collectionName;
		this.description = description;
		this.internalName = internalName;
		this.local = local;
		this.name = name;
		this.enabled = enabled;
	}

	// bi-directional many-to-one association to TurNLPVendorEntity
	@OneToMany(mappedBy = "turNLPEntity", orphanRemoval = true)
	@Cascade({ CascadeType.ALL })
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurNLPVendorEntity> turNLPVendorEntities;
	
	
	// bi-directional many-to-one association to TurTerm
	@OneToMany(mappedBy = "turNLPEntity", orphanRemoval = true)
	@Cascade({ CascadeType.ALL })
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurTerm> turTerms;

	public String getCollectionName() {
		return this.collectionName;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getInternalName() {
		return this.internalName;
	}

	public void setInternalName(String internalName) {
		this.internalName = internalName;
	}

	public int getLocal() {
		return this.local;
	}

	public void setLocal(int local) {
		this.local = local;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public Set<TurNLPVendorEntity> getTurNLPVendorEntities() {
		return this.turNLPVendorEntities;
	}

	public void setTurNLPVendorEntities(Set<TurNLPVendorEntity> turNLPVendorEntities) {
		this.turNLPVendorEntities = turNLPVendorEntities;
	}

	public TurNLPVendorEntity addTurNLPVendorEntity(TurNLPVendorEntity turNLPVendorEntity) {
		getTurNLPVendorEntities().add(turNLPVendorEntity);
		turNLPVendorEntity.setTurNLPEntity(this);

		return turNLPVendorEntity;
	}

	public TurNLPVendorEntity removeTurNLPVendorEntity(TurNLPVendorEntity turNLPVendorEntity) {
		getTurNLPVendorEntities().remove(turNLPVendorEntity);
		turNLPVendorEntity.setTurNLPEntity(null);

		return turNLPVendorEntity;
	}
	public Set<TurTerm> getTurTerms() {
		return this.turTerms;
	}

	public void setTurTerms(Set<TurTerm> turTerms) {
		this.turTerms = turTerms;
	}

	public TurTerm addTurTerm(TurTerm turTerm) {
		getTurTerms().add(turTerm);
		turTerm.setTurNLPEntity(this);

		return turTerm;
	}

	public TurTerm removeTurTerm(TurTerm turTerm) {
		getTurTerms().remove(turTerm);
		turTerm.setTurNLPEntity(null);

		return turTerm;
	}

	public int getEnabled() {
		return enabled;
	}

	public void setEnabled(int enabled) {
		this.enabled = enabled;
	}

	

}
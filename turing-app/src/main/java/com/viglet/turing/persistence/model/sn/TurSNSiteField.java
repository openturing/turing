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

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;

/**
 * The persistent class for the vigServices database table.
 * 
 */
@Getter
@Entity
@Table(name = "turSNSiteField")
@NamedQuery(name = "TurSNSiteField.findAll", query = "SELECT snsf FROM TurSNSiteField snsf")
public class TurSNSiteField implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = false, length = 50)
	private String name;
	
	@Column(nullable = true, length = 255)
	private String description;

	@Column(nullable = false)
	private TurSEFieldType type;

	@Column(nullable = true)
	private int multiValued;
	
	// bi-directional many-to-one association to TurSNSite
	@ManyToOne
	@JoinColumn(name = "sn_site_id", nullable = false)
	@JsonBackReference (value="turSNSiteField-turSNSite")
	private TurSNSite turSNSite;

	public void setId(String id) {
		this.id = id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setType(TurSEFieldType type) {
		this.type = type;
	}

	public void setMultiValued(int multiValued) {
		this.multiValued = multiValued;
	}

	public void setTurSNSite(TurSNSite turSNSite) {
		this.turSNSite = turSNSite;
	}

	
}
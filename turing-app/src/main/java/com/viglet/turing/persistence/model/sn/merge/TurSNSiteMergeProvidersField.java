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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;

/**
 * The persistent class for the turSNSiteMergeField database table.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Getter
@Entity
@Table(name = "turSNSiteMergeField")
@NamedQuery(name = "TurSNSiteMergeProvidersField.findAll", query = "SELECT snsmpf FROM TurSNSiteMergeProvidersField snsmpf")
@JsonIgnoreProperties({ "turSNSiteMergeProviders" })
public class TurSNSiteMergeProvidersField implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = false, length = 50)
	private String name;

	// bi-directional many-to-one association to TurSNSiteSpotlight
	@ManyToOne(fetch = FetchType.LAZY) // (cascade = {CascadeType.ALL})
	@JoinColumn(name = "sn_site_merge_id")
	private TurSNSiteMergeProviders turSNSiteMergeProviders;

	public void setId(String id) {
		this.id = id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setTurSNSiteMergeProviders(TurSNSiteMergeProviders turSNSiteMergeProviders) {
		this.turSNSiteMergeProviders = turSNSiteMergeProviders;
	}

}
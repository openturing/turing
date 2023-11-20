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

package com.viglet.turing.persistence.model.sn.spotlight;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * The persistent class for the turSNSiteSpotlight database table.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.4
 */
@Getter
@Entity
@Table(name = "tur_sn_site_spotlight")
public class TurSNSiteSpotlight implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = false, length = 50)
	private String name;

	@Column
	private String description;

	@Temporal(TemporalType.TIMESTAMP)
	private Date modificationDate;

	@Column
	private int managed = 1;

	@Column
	private String unmanagedId;

	@Column
	private String provider = "TURING";
	
	@Column
	private String language;

	// bi-directional many-to-one association to TurSNSite
	@ManyToOne
	@JoinColumn(name = "sn_site_id", nullable = false)
	private TurSNSite turSNSite;

	// bi-directional many-to-one association to turSNSiteSpotlightTerms
	@OneToMany(mappedBy = "turSNSiteSpotlight", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN  })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteSpotlightTerm> turSNSiteSpotlightTerms = new HashSet<>();

	// bi-directional many-to-one association to turSNSiteSpotlightDocuments
	@OneToMany(mappedBy = "turSNSiteSpotlight", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN  })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteSpotlightDocument> turSNSiteSpotlightDocuments = new HashSet<>();

	public void setId(String id) {
		this.id = id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setModificationDate(Date modificationDate) {
		this.modificationDate = modificationDate;
	}

	public void setManaged(int managed) {
		this.managed = managed;
	}

	public void setUnmanagedId(String unmanagedId) {
		this.unmanagedId = unmanagedId;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public void setTurSNSite(TurSNSite turSNSite) {
		this.turSNSite = turSNSite;
	}

	public void setTurSNSiteSpotlightTerms(Set<TurSNSiteSpotlightTerm> turSNSiteSpotlightTerms) {
		this.turSNSiteSpotlightTerms.clear();
		if (turSNSiteSpotlightTerms != null) {
			this.turSNSiteSpotlightTerms.addAll(turSNSiteSpotlightTerms);
		}
	}

	public void setTurSNSiteSpotlightDocuments(Set<TurSNSiteSpotlightDocument> turSNSiteSpotlightDocuments) {
		this.turSNSiteSpotlightDocuments.clear();
		if (turSNSiteSpotlightDocuments != null) {
			this.turSNSiteSpotlightDocuments.addAll(turSNSiteSpotlightDocuments);
		}
	}
}
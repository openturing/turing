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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.metric.TurSNSiteMetricAccess;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.spring.security.TurAuditable;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * The persistent class for the TurSNSite database table.
 * 
 */
@Getter
@Setter
@Entity
@Table(name = "tur_sn_site")
@NamedQuery(name = "TurSNSite.findAll", query = "SELECT sns FROM TurSNSite sns")
@JsonIgnoreProperties({ "turSNSiteFields", "turSNSiteFieldExts", "turSNSiteSpotlights",
		"turSNSiteLocales", "turSNSiteMetricAccesses", "turSNRankingExpressions" })
@EntityListeners(AuditingEntityListener.class)
public class TurSNSite extends TurAuditable<String> implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false)
	private String description;

	@Column()
	private Integer rowsPerPage;

	@Column()
	private Integer facet;

	@Column()
	private Integer itemsPerFacet;

	@Column()
	private Integer hl;

	@Column(length = 50)
	private String hlPre;

	@Column(length = 50)
	private String hlPost;

	@Column()
	private Integer mlt;

	@Column()
	private TurSNSiteFacetEnum facetType = TurSNSiteFacetEnum.AND;

	@Column()
	private Integer thesaurus;

	@Column()
	private String defaultTitleField;

	@Column()
	private String defaultTextField;

	@Column()
	private String defaultDescriptionField;

	@Column()
	private String defaultDateField;

	@Column()
	private String defaultImageField;

	@Column()
	private String defaultURLField;

	@Column()
	private Integer spellCheck;

	@Column()
	private Integer spellCheckFixes;

	@Column()
	private Integer spotlightWithResults;
	
	// bi-directional many-to-one association to TurSEInstance
	@ManyToOne
	@JoinColumn(name = "se_instance_id", nullable = false)
	private TurSEInstance turSEInstance;

	// bi-directional many-to-one association to TurSEInstance
	@ManyToOne
	@JoinColumn(name = "nlp_vendor_id")
	private TurNLPVendor turNLPVendor;

	// bi-directional many-to-one association to turSNSiteFields
	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteField> turSNSiteFields = new HashSet<>();

	// bi-directional many-to-one association to turSNSiteFieldExts
	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteFieldExt> turSNSiteFieldExts = new HashSet<>();

	// bi-directional many-to-one association to turSNSiteSpotlights
	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteSpotlight> turSNSiteSpotlights = new HashSet<>();

	// bi-directional many-to-one association to turSNSiteLocales
	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteLocale> turSNSiteLocales = new HashSet<>();

	// bi-directional many-to-one association to turSNSiteMetricAccesses
	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteMetricAccess> turSNSiteMetricAccesses = new HashSet<>();

	// bi-directional many-to-one association to turSNRankingExpressions
	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNRankingExpression> turSNRankingExpressions = new HashSet<>();

	public TurSNSiteField addTurSNSiteField(TurSNSiteField turSNSiteField) {
		getTurSNSiteFields().add(turSNSiteField);
		turSNSiteField.setTurSNSite(this);

		return turSNSiteField;
	}
	public TurSNSiteField removeTurSNSiteField(TurSNSiteField turSNSiteField) {
		getTurSNSiteFields().remove(turSNSiteField);
		turSNSiteField.setTurSNSite(this);

		return turSNSiteField;
	}
}
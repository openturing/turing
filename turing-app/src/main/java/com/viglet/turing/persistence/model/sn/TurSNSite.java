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
@Entity
@Table(name = "turSNSite")
@NamedQuery(name = "TurSNSite.findAll", query = "SELECT sns FROM TurSNSite sns")
@JsonIgnoreProperties({ "turSNSiteFields", "turSNSiteFieldExts", "turSNSiteSpotlights", "turSNSiteLocales" })
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

	@Column(nullable = false, length = 255)
	private String description;

	@Column(nullable = true)
	private Integer rowsPerPage;

	@Column(nullable = true)
	private Integer facet;

	@Column(nullable = true)
	private Integer itemsPerFacet;

	@Column(nullable = true)
	private Integer hl;

	@Column(nullable = true, length = 50)
	private String hlPre;

	@Column(nullable = true, length = 50)
	private String hlPost;

	@Column(nullable = true)
	private Integer mlt;

	@Column(nullable = true)
	private Integer thesaurus;

	@Column(nullable = true)
	private String defaultTitleField;

	@Column(nullable = true)
	private String defaultTextField;

	@Column(nullable = true)
	private String defaultDescriptionField;

	@Column(nullable = true)
	private String defaultDateField;

	@Column(nullable = true)
	private String defaultImageField;

	@Column(nullable = true)
	private String defaultURLField;

	@Column(nullable = true)
	private Integer spellCheck;

	@Column(nullable = true)
	private Integer spellCheckFixes;

	@Column(nullable = true)
	private Integer spotlightWithResults;
	
	// bi-directional many-to-one association to TurSEInstance
	@ManyToOne
	@JoinColumn(name = "se_instance_id", nullable = false)
	private TurSEInstance turSEInstance;

	// bi-directional many-to-one association to TurSEInstance
	@ManyToOne
	@JoinColumn(name = "nlp_vendor_id", nullable = true)
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

	// bi-directional many-to-one association to turSNSiteLocales
	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteMetricAccess> turSNSiteMetricAccesses = new HashSet<>();

	// bi-directional many-to-one association to turSNSiteLocales
	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNRankingExpression> turSNRankingExpressions = new HashSet<>();

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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public TurSEInstance getTurSEInstance() {
		return turSEInstance;
	}

	public void setTurSEInstance(TurSEInstance turSEInstance) {
		this.turSEInstance = turSEInstance;
	}

	public TurNLPVendor getTurNLPVendor() {
		return turNLPVendor;
	}

	public void setTurNLPVendor(TurNLPVendor turNLPVendor) {
		this.turNLPVendor = turNLPVendor;
	}

	public Set<TurSNSiteField> getTurSNSiteFields() {
		return turSNSiteFields;
	}

	public void setTurSNSiteFields(Set<TurSNSiteField> turSNSiteFields) {
		this.turSNSiteFields = turSNSiteFields;
	}

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

	public Integer getRowsPerPage() {
		return rowsPerPage;
	}

	public void setRowsPerPage(Integer rowsPerPage) {
		this.rowsPerPage = rowsPerPage;
	}

	public Integer getFacet() {
		return facet;
	}

	public void setFacet(Integer facet) {
		this.facet = facet;
	}

	public Integer getItemsPerFacet() {
		return itemsPerFacet;
	}

	public void setItemsPerFacet(int itemsPerFacet) {
		this.itemsPerFacet = itemsPerFacet;
	}

	public Integer getHl() {
		return hl;
	}

	public void setHl(Integer hl) {
		this.hl = hl;
	}

	public String getHlPre() {
		return hlPre;
	}

	public void setHlPre(String hlPre) {
		this.hlPre = hlPre;
	}

	public String getHlPost() {
		return hlPost;
	}

	public void setHlPost(String hlPost) {
		this.hlPost = hlPost;
	}

	public Integer getMlt() {
		return mlt;
	}

	public void setMlt(Integer mlt) {
		this.mlt = mlt;
	}

	public Integer getThesaurus() {
		return thesaurus;
	}

	public void setThesaurus(Integer thesaurus) {
		this.thesaurus = thesaurus;
	}

	public String getDefaultTextField() {
		return defaultTextField;
	}

	public void setDefaultTextField(String defaultTextField) {
		this.defaultTextField = defaultTextField;
	}

	public String getDefaultDescriptionField() {
		return defaultDescriptionField;
	}

	public void setDefaultDescriptionField(String defaultDescriptionField) {
		this.defaultDescriptionField = defaultDescriptionField;
	}

	public String getDefaultDateField() {
		return defaultDateField;
	}

	public void setDefaultDateField(String defaultDateField) {
		this.defaultDateField = defaultDateField;
	}

	public String getDefaultURLField() {
		return defaultURLField;
	}

	public void setDefaultURLField(String defaultURLField) {
		this.defaultURLField = defaultURLField;
	}

	public String getDefaultTitleField() {
		return defaultTitleField;
	}

	public void setDefaultTitleField(String defaultTitleField) {
		this.defaultTitleField = defaultTitleField;
	}

	public String getDefaultImageField() {
		return defaultImageField;
	}

	public void setDefaultImageField(String defaultImageField) {
		this.defaultImageField = defaultImageField;
	}

	public Integer getSpellCheck() {
		return spellCheck;
	}

	public void setSpellCheck(Integer spellCheck) {
		this.spellCheck = spellCheck;
	}

	public Integer getSpellCheckFixes() {
		return spellCheckFixes;
	}

	public void setSpellCheckFixes(Integer spellCheckFixes) {
		this.spellCheckFixes = spellCheckFixes;
	}

	public Integer getSpotlightWithResults() {
		return spotlightWithResults;
	}

	public void setSpotlightWithResults(Integer spotlightWithResults) {
		this.spotlightWithResults = spotlightWithResults;
	}

}
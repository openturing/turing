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

package com.viglet.turing.persistence.model.sn.metric;

import com.viglet.turing.persistence.model.sn.TurSNSite;

import jakarta.persistence.Entity;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.*;
import java.io.Serializable;
import java.text.Normalizer;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * The persistent class for the turSNSiteMetric database table.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Entity
@Table(name = "turSNSiteMetricAccess")
@NamedQuery(name = "TurSNSiteMetricAccess.findAll", query = "SELECT snsma FROM TurSNSiteMetricAccess snsma")
public class TurSNSiteMetricAccess implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(length = 50)
	private String userId;

	@Temporal(TemporalType.TIMESTAMP)
	private Date accessDate;

	@Column
	private String term;

	@Column
	private String sanatizedTerm;

	@ElementCollection
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	@CollectionTable(name = "tursnsite_metric_access_trs")
	@JoinColumn(name = "tr_id")
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<String> targetingRules = new HashSet<>();

	@Column
	private String language;

	// bi-directional many-to-one association to TurSNSite
	@ManyToOne
	@JoinColumn(name = "sn_site_id", nullable = false)
	private TurSNSite turSNSite;

	private long numFound;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public Date getAccessDate() {
		return accessDate;
	}

	public void setAccessDate(Date accessDate) {
		this.accessDate = accessDate;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
		this.sanatizedTerm = Normalizer.normalize(term, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "")
				.replaceAll("( )+", " ").toLowerCase().trim();
	}

	public Set<String> getTargetingRules() {
		return targetingRules;
	}

	public void setTargetingRules(Set<String> targetingRules) {
		this.targetingRules = targetingRules;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public TurSNSite getTurSNSite() {
		return turSNSite;
	}

	public void setTurSNSite(TurSNSite turSNSite) {
		this.turSNSite = turSNSite;
	}

	public long getNumFound() {
		return numFound;
	}

	public void setNumFound(long numFound) {
		this.numFound = numFound;
	}

	public String getSanatizedTerm() {
		return sanatizedTerm;
	}

	public void setSanatizedTerm(String sanatizedTerm) {
		this.sanatizedTerm = sanatizedTerm;
	}

}
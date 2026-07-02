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

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRule;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSort;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.model.sn.metric.TurSNSiteMetricAccess;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;
import com.viglet.turing.sn.snapshot.TurSNSiteSnapshotEvictionListener;
import com.viglet.turing.spring.security.TurAuditable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The persistent class for the TurSNSite database table.
 * 
 */
@Getter
@Setter
@Entity
@Table(name = "sn_site")
@JsonIgnoreProperties({ "turSNSiteFields", "turSNSiteFieldExts", "turSNSiteSpotlights",
		"turSNSiteLocales", "turSNSiteMetricAccesses", "turSNRankingExpressions", "turSNSiteMergeProviders",
		"turSNSiteCustomSorts", "turSNSiteSearchRules" })
@EntityListeners({ AuditingEntityListener.class, TurSNSiteSnapshotEvictionListener.class })
public class TurSNSite extends TurAuditable<String> implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@VigletAssignableUuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	/**
	 * T260 / §XIV.2.4 — multi-tenancy discriminator. Hibernate's native
	 * {@code @TenantId} stamps this on insert from the
	 * {@code CurrentTenantIdentifierResolver} (which reads
	 * {@code TurTenantContext}) and adds {@code WHERE tenantId = ?} to every
	 * read automatically. The field is Hibernate-managed (not set by callers).
	 * When {@code turing.tenancy.enabled=false} the resolver always yields
	 * {@code DEFAULT}, so this is a constant and single-tenant behaviour is
	 * unchanged. <strong>Pilot entity</strong> for the fan-out in T261.
	 */
	@org.hibernate.annotations.TenantId
	@Column(name = "tenantId", length = 40)
	private String tenantId;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = true, length = 500)
	private String description;

	@Column(length = 150)
	private String icon;

	/**
	 * Number of results per page
	 */
	@Column
	private Integer rowsPerPage = 10;

	@Column
	private Integer wildcardNoResults = 0;

	@Column
	private Integer wildcardAlways = 0;

	@Column
	private Integer exactMatch = 0;

	@Column
	private Integer facet;

	@Column
	private Integer itemsPerFacet;

	@Column
	private Integer hl;

	@Column(length = 50)
	private String hlPre;

	@Column(length = 50)
	private String hlPost;

	@Column
	private Integer mlt;

	@Column
	private TurSNSiteFacetFieldEnum facetType = TurSNSiteFacetFieldEnum.AND;

	@Column
	private TurSNSiteFacetFieldEnum facetItemType = TurSNSiteFacetFieldEnum.AND;

	@Column
	private TurSNSiteFacetSortEnum facetSort = TurSNSiteFacetSortEnum.COUNT;

	@Column
	private Integer thesaurus = 0;

	@Column
	private String defaultField;

	@Column
	private String exactMatchField;

	@Column
	private String defaultTitleField;

	@Column
	private String defaultTextField;

	@Column
	private String defaultDescriptionField;

	@Column
	private String defaultDateField;

	@Column
	private String defaultImageField;

	@Column
	private String defaultURLField;

	@Column
	private Integer spellCheck;

	@Column
	private Integer spellCheckFixes;

	@Column
	private Integer spotlightWithResults;

	@Column(length = 255)
	private String searchTemplate;

	/**
	 * T233 / §VII.6.h — whether the visitor-facing API of this site is open or
	 * requires a Turing-registered API key (Dev Token). Persisted as a String
	 * (per the project enum convention) and defaults to {@link
	 * TurSNSiteApiAuthMode#PUBLIC} so existing sites stay open.
	 *
	 * @since 2026.3.1
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "apiAuthMode", length = 16)
	private TurSNSiteApiAuthMode apiAuthMode = TurSNSiteApiAuthMode.PUBLIC;

	@ManyToOne
	@JoinColumn(name = "se_instance_id", nullable = false)
	private TurSEInstance turSEInstance;

	@OneToOne(fetch = FetchType.LAZY)
	private TurSNSiteGenAi turSNSiteGenAi;

	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteField> turSNSiteFields = new HashSet<>();

	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteFieldExt> turSNSiteFieldExts = new HashSet<>();

	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteSpotlight> turSNSiteSpotlights = new HashSet<>();

	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteLocale> turSNSiteLocales = new HashSet<>();

	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteMetricAccess> turSNSiteMetricAccesses = new HashSet<>();

	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNRankingExpression> turSNRankingExpressions = new HashSet<>();

	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteMergeProviders> turSNSiteMergeProviders = new HashSet<>();

	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteCustomSort> turSNSiteCustomSorts = new HashSet<>();

	@OneToMany(mappedBy = "turSNSite", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurSNSiteSearchRule> turSNSiteSearchRules = new HashSet<>();

}

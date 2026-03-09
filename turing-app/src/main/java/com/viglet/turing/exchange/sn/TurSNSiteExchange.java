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

package com.viglet.turing.exchange.sn;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetSortEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSiteExchange {

	private String id;
	private String name;
	private String description;
	private Integer rowsPerPage;
	private Integer wildcardNoResults;
	private Integer wildcardAlways;
	private Integer exactMatch;
	private boolean facet;
	private Integer itemsPerFacet;
	private boolean hl;
	private String hlPre;
	private String hlPost;
	private boolean mlt;
	private TurSNSiteFacetFieldEnum facetType;
	private TurSNSiteFacetFieldEnum facetItemType;
	private TurSNSiteFacetSortEnum facetSort;
	private boolean thesaurus;
	private String defaultField;
	private String exactMatchField;
	private String defaultTitleField;
	private String defaultTextField;
	private String defaultDescriptionField;
	private String defaultDateField;
	private String defaultImageField;
	private String defaultURLField;
	private Integer spellCheck;
	private Integer spellCheckFixes;
	private Integer spotlightWithResults;
	private String turSEInstance;
	private TurSNSiteGenAiExchange turSNSiteGenAi;
	private Set<TurSNSiteField> turSNSiteFields;
	private Set<TurSNSiteFieldExt> turSNSiteFieldExts;
	private Set<TurSNSiteSpotlight> turSNSiteSpotlights;
	private Set<TurSNSiteLocale> turSNSiteLocales;
	private Set<TurSNRankingExpression> turSNRankingExpressions;
	private Set<TurSNSiteMergeProviders> turSNSiteMergeProviders;

	public TurSNSiteExchange(TurSNSite turSNSite) {
		this.setDefaultDateField(turSNSite.getDefaultDateField());
		this.setDefaultDescriptionField(turSNSite.getDefaultDescriptionField());
		this.setDefaultImageField(turSNSite.getDefaultImageField());
		this.setDefaultTextField(turSNSite.getDefaultTextField());
		this.setDefaultTitleField(turSNSite.getDefaultTitleField());
		this.setDefaultURLField(turSNSite.getDefaultURLField());
		this.setDescription(turSNSite.getDescription());
		this.setFacet(turSNSite.getFacet() == 1);
		this.setHl(turSNSite.getHl() == 1);
		this.setHlPost(turSNSite.getHlPost());
		this.setHlPre(turSNSite.getHlPre());
		this.setId(turSNSite.getId());
		this.setItemsPerFacet(turSNSite.getItemsPerFacet());
		this.setMlt(turSNSite.getMlt() == 1);
		this.setName(turSNSite.getName());
		this.setRowsPerPage(turSNSite.getRowsPerPage());
		this.setThesaurus(turSNSite.getThesaurus() == 1);
		this.setWildcardNoResults(turSNSite.getWildcardNoResults());
		this.setWildcardAlways(turSNSite.getWildcardAlways());
		this.setExactMatch(turSNSite.getExactMatch());
		this.setFacetType(turSNSite.getFacetType());
		this.setFacetItemType(turSNSite.getFacetItemType());
		this.setFacetSort(turSNSite.getFacetSort());
		this.setDefaultField(turSNSite.getDefaultField());
		this.setExactMatchField(turSNSite.getExactMatchField());
		this.setSpellCheck(turSNSite.getSpellCheck());
		this.setSpellCheckFixes(turSNSite.getSpellCheckFixes());
		this.setSpotlightWithResults(turSNSite.getSpotlightWithResults());
		if (turSNSite.getTurSEInstance() != null) {
			this.setTurSEInstance(turSNSite.getTurSEInstance().getId());
		}
		this.setTurSNSiteGenAi(this.toReferenceGenAi(turSNSite.getTurSNSiteGenAi()));
		this.setTurSNSiteFields(turSNSite.getTurSNSiteFields());
		this.setTurSNSiteFieldExts(turSNSite.getTurSNSiteFieldExts());
		this.setTurSNSiteSpotlights(turSNSite.getTurSNSiteSpotlights());
		this.setTurSNSiteLocales(turSNSite.getTurSNSiteLocales());
		this.setTurSNRankingExpressions(turSNSite.getTurSNRankingExpressions());
		this.setTurSNSiteMergeProviders(turSNSite.getTurSNSiteMergeProviders());
	}

	private TurSNSiteGenAiExchange toReferenceGenAi(TurSNSiteGenAi source) {
		if (source == null) {
			return null;
		}
		TurSNSiteGenAiExchange referenceGenAi = new TurSNSiteGenAiExchange();
		referenceGenAi.setId(source.getId());
		referenceGenAi.setEnabled(source.isEnabled());
		referenceGenAi.setSystemPrompt(source.getSystemPrompt());
		if (source.getTurLLMInstance() != null && source.getTurLLMInstance().getId() != null) {
			referenceGenAi.setTurLLMInstance(source.getTurLLMInstance().getId());
		}
		if (source.getTurStoreInstance() != null && source.getTurStoreInstance().getId() != null) {
			referenceGenAi.setTurStoreInstance(source.getTurStoreInstance().getId());
		}
		return referenceGenAi;
	}

	public boolean getHl() {
		return hl;
	}
}
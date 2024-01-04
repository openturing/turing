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

package com.viglet.turing.sn.template;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingCondition;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.solr.TurSolrUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * @author Alexandre Oliveira
 * @since 0.3.4
 */

@Slf4j
@Component
public class TurSNTemplate {
	private final ResourceLoader resourceloader;
	private final TurSNSiteFieldRepository turSNSiteFieldRepository;
	private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
	private final TurNLPInstanceRepository turNLPInstanceRepository;
	private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
	private final TurSEInstanceRepository turSEInstanceRepository;
	private final TurSNRankingExpressionRepository turSNRankingExpressionRepository;
	private final TurSNRankingConditionRepository turSNRankingConditionRepository;
	private final TurConfigProperties turConfigProperties;

	public TurSNTemplate(ResourceLoader resourceloader,
						 TurSNSiteFieldRepository turSNSiteFieldRepository,
						 TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
						 TurNLPInstanceRepository turNLPInstanceRepository,
						 TurSNSiteLocaleRepository turSNSiteLocaleRepository,
						 TurSEInstanceRepository turSEInstanceRepository,
						 TurSNRankingExpressionRepository turSNRankingExpressionRepository,
						 TurSNRankingConditionRepository turSNRankingConditionRepository,
						 TurConfigProperties turConfigProperties) {
		this.resourceloader = resourceloader;
		this.turSNSiteFieldRepository = turSNSiteFieldRepository;
		this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
		this.turNLPInstanceRepository = turNLPInstanceRepository;
		this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
		this.turSEInstanceRepository = turSEInstanceRepository;
		this.turSNRankingExpressionRepository = turSNRankingExpressionRepository;
		this.turSNRankingConditionRepository = turSNRankingConditionRepository;
		this.turConfigProperties = turConfigProperties;
	}

	public void createSNSite(TurSNSite turSNSite, String username, Locale locale) {
		defaultSNUI(turSNSite);
		createSEFields(turSNSite);
		createLocale(turSNSite, username, locale);
		createRankingExpression(turSNSite);
	}

	public void defaultSNUI(TurSNSite turSNSite) {
		turSNSite.setRowsPerPage(10);
		turSNSite.setFacet(1);
		turSNSite.setItemsPerFacet(10);
		turSNSite.setHl(1);
		turSNSite.setHlPre("<mark>");
		turSNSite.setHlPost("</mark>");
		turSNSite.setMlt(1);
		turSNSite.setSpellCheck(1);
		turSNSite.setSpellCheckFixes(1);
		turSNSite.setThesaurus(0);
		turSNSite.setDefaultTitleField("title");
		turSNSite.setDefaultTextField("text");
		turSNSite.setDefaultDescriptionField("abstract");
		turSNSite.setDefaultDateField("publication_date");
		turSNSite.setDefaultImageField("image");
		turSNSite.setDefaultURLField("url");
	}

	public String createSolrCore(TurSNSiteLocale turSNSiteLocale, String username) {
		String coreName;
		if (turConfigProperties.isMultiTenant()) {
			coreName = String.format("%s_%s_%s", username,
					turSNSiteLocale.getTurSNSite().getName().toLowerCase().replace(" ", "_"),
					turSNSiteLocale.getLanguage());
		} else {
			coreName = String.format("%s_%s",
					turSNSiteLocale.getTurSNSite().getName().toLowerCase().replace(" ", "_"),
					turSNSiteLocale.getLanguage());
		}
        Optional<TurSEInstance> turSEInstance = turSEInstanceRepository
				.findById(turSNSiteLocale.getTurSNSite().getTurSEInstance().getId());
		turSEInstance.ifPresent(instance -> {
			String configset = turSNSiteLocale.getLanguage().getLanguage();
			String[] locales = {"en", "es", "pt"};
			if(!Arrays.asList(locales).contains(configset)) {
				configset = "en";
			}
			String solrURL = String.format("http://%s:%s", instance.getHost(), instance.getPort());
			if (turConfigProperties.getSolr().isCloud()) {
				try {
					TurSolrUtils.createCollection(solrURL, coreName,
							resourceloader.getResource(String.format("classpath:solr/configsets/%s.zip", configset)).getInputStream());
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				}
			}
			else {
				TurSolrUtils.createCore(solrURL, coreName, configset);
			}
		});
		return coreName;
	}

	private void createSNSiteField(TurSNSite turSNSite, String name, String description, TurSEFieldType type,
								   int multiValued, String facetName, int hl) {
		TurSNSiteField turSNSiteField = new TurSNSiteField();
		turSNSiteField.setName(name);
		turSNSiteField.setDescription(description);
		turSNSiteField.setType(type);
		turSNSiteField.setMultiValued(multiValued);
		turSNSiteField.setTurSNSite(turSNSite);

		turSNSiteFieldRepository.save(turSNSiteField);
		turSNSiteFieldExtRepository.save(TurSNSiteFieldExt.builder()
				.enabled(1)
				.name(turSNSiteField.getName())
				.description(turSNSiteField.getDescription())
				.facet(0)
				.facetName(facetName)
				.hl(hl)
				.multiValued(turSNSiteField.getMultiValued())
				.mlt(0)
				.externalId(turSNSiteField.getId())
				.snType(TurSNFieldType.SE)
				.type(turSNSiteField.getType())
				.turSNSite(turSNSite).build());
	}

	public void createSEFields(TurSNSite turSNSite) {
		createSNSiteField(turSNSite, "title", "Title Field", TurSEFieldType.STRING, 0, "Titles", 1);
		createSNSiteField(turSNSite, "text", "Text Field", TurSEFieldType.STRING, 0, "Texts", 1);
		createSNSiteField(turSNSite, "abstract", "Short Description Field", TurSEFieldType.STRING, 0, "Abstracts", 1);
		createSNSiteField(turSNSite, "type", "Content Type Field", TurSEFieldType.STRING, 0, "Types", 1);
		createSNSiteField(turSNSite, "image", "Image Field", TurSEFieldType.STRING, 0, "Images", 0);
		createSNSiteField(turSNSite, "url", "URL Field", TurSEFieldType.STRING, 0, "URLs", 0);
		createSNSiteField(turSNSite, "publication_date", "Publication Date", TurSEFieldType.DATE, 0,
				"Publication Dates", 0);
		createSNSiteField(turSNSite, "modification_date", "Modification Date", TurSEFieldType.DATE, 0,
				"Modification Dates", 0);
		createSNSiteField(turSNSite, "site", "Site Name", TurSEFieldType.STRING, 0, "Sites", 0);
		createSNSiteField(turSNSite, "author", "Author", TurSEFieldType.STRING, 0, "Authors", 0);
		createSNSiteField(turSNSite, "section", "Section", TurSEFieldType.STRING, 0, "Sections", 0);
		createSNSiteField(turSNSite, "source_apps", "Source Apps", TurSEFieldType.STRING, 1, "Source Apps", 0);
	}

	public void createLocale(TurSNSite turSNSite, String username, Locale locale) {
		TurSNSiteLocale turSNSiteLocale = new TurSNSiteLocale();
		turSNSiteLocale.setLanguage(locale);
		turSNSiteLocale.setTurNLPInstance(turNLPInstanceRepository.findAll().getFirst());
		turSNSiteLocale.setTurSNSite(turSNSite);
		turSNSiteLocale.setCore(createSolrCore(turSNSiteLocale, username));
		turSNSiteLocaleRepository.save(turSNSiteLocale);

	}

	private void createRankingExpression(TurSNSite turSNSite) {

		TurSNRankingExpression turSNRankingExpression = new TurSNRankingExpression();
		turSNRankingExpression.setName("Rule Sample");
		turSNRankingExpression.setDescription("Rule Sample Description");
		turSNRankingExpression.setWeight(5);
		turSNRankingExpression.setTurSNSite(turSNSite);
		turSNRankingExpressionRepository.save(turSNRankingExpression);

		TurSNRankingCondition turSNRankingCondition1 = new TurSNRankingCondition();
		turSNRankingCondition1.setAttribute("title");
		turSNRankingCondition1.setCondition(1);
		turSNRankingCondition1.setValue("viglet");
		turSNRankingCondition1.setTurSNRankingExpression(turSNRankingExpression);
		turSNRankingConditionRepository.save(turSNRankingCondition1);

		TurSNRankingCondition turSNRankingCondition2 = new TurSNRankingCondition();
		turSNRankingCondition2.setAttribute("type");
		turSNRankingCondition2.setCondition(1);
		turSNRankingCondition2.setValue("News");
		turSNRankingCondition2.setTurSNRankingExpression(turSNRankingExpression);
		turSNRankingConditionRepository.save(turSNRankingCondition2);

	}
}

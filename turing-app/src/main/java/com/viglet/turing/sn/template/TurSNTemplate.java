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
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProvidersField;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingCondition;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersFieldRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightDocumentRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.solr.TurSolrUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
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
	private final TurNLPEntityRepository turNLPEntityRepository;
	private final TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
	private final TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository;
	private final TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository;
	private final TurNLPInstanceRepository turNLPInstanceRepository;
	private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
	private final TurSNSiteMergeProvidersRepository turSNSiteMergeRepository;
	private final TurSNSiteMergeProvidersFieldRepository turSNSiteMergeFieldRepository;
	private final TurSEInstanceRepository turSEInstanceRepository;
	private final TurSNRankingExpressionRepository turSNRankingExpressionRepository;
	private final TurSNRankingConditionRepository turSNRankingConditionRepository;
	private final TurConfigProperties turConfigProperties;

	public TurSNTemplate(ResourceLoader resourceloader,
						 TurSNSiteFieldRepository turSNSiteFieldRepository,
						 TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
						 TurNLPEntityRepository turNLPEntityRepository,
						 TurSNSiteSpotlightRepository turSNSiteSpotlightRepository,
						 TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository,
						 TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository,
						 TurNLPInstanceRepository turNLPInstanceRepository,
						 TurSNSiteLocaleRepository turSNSiteLocaleRepository,
						 TurSNSiteMergeProvidersRepository turSNSiteMergeRepository,
						 TurSNSiteMergeProvidersFieldRepository turSNSiteMergeFieldRepository,
						 TurSEInstanceRepository turSEInstanceRepository,
						 TurSNRankingExpressionRepository turSNRankingExpressionRepository,
						 TurSNRankingConditionRepository turSNRankingConditionRepository,
						 TurConfigProperties turConfigProperties) {
		this.resourceloader = resourceloader;
		this.turSNSiteFieldRepository = turSNSiteFieldRepository;
		this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
		this.turNLPEntityRepository = turNLPEntityRepository;
		this.turSNSiteSpotlightRepository = turSNSiteSpotlightRepository;
		this.turSNSiteSpotlightDocumentRepository = turSNSiteSpotlightDocumentRepository;
		this.turSNSiteSpotlightTermRepository = turSNSiteSpotlightTermRepository;
		this.turNLPInstanceRepository = turNLPInstanceRepository;
		this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
		this.turSNSiteMergeRepository = turSNSiteMergeRepository;
		this.turSNSiteMergeFieldRepository = turSNSiteMergeFieldRepository;
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
			String configset = turSNSiteLocale.getLanguage();
			if (configset.contains("_")) {
				String[] configsetSplit = configset.split("-");
				configset = configsetSplit[0];
			}
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

	private void createNERFields(TurSNSite turSNSite) {
		TurNLPEntity turNLPEntity = turNLPEntityRepository.findByInternalName("PN");
		turSNSiteFieldExtRepository.save(TurSNSiteFieldExt.builder()
				.enabled(1)
				.name(turNLPEntity.getInternalName())
				.description(turNLPEntity.getDescription())
				.facet(1)
				.facetName("People")
				.hl(0)
				.multiValued(1)
				.mlt(0)
				.externalId(turNLPEntity.getInternalName())
				.snType(TurSNFieldType.NER)
				.type(TurSEFieldType.STRING)
				.turSNSite(turSNSite).build());
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

	private void createSpotlight(TurSNSite turSNSite, TurSNSiteLocale turSNSiteLocale) {
		TurSNSiteSpotlight turSNSiteSpotlight = new TurSNSiteSpotlight();
		turSNSiteSpotlight.setDescription("Spotlight Sample");
		turSNSiteSpotlight.setName("Spotlight Sample");
		turSNSiteSpotlight.setModificationDate(new Date());
		turSNSiteSpotlight.setManaged(1);
		turSNSiteSpotlight.setProvider("TURING");
		turSNSiteSpotlight.setTurSNSite(turSNSite);
		turSNSiteSpotlight.setLanguage(turSNSiteLocale.getLanguage());
		turSNSiteSpotlightRepository.save(turSNSiteSpotlight);

		TurSNSiteSpotlightDocument turSNSiteSpotlightDocument = new TurSNSiteSpotlightDocument();
		turSNSiteSpotlightDocument.setPosition(1);
		turSNSiteSpotlightDocument.setTitle("Viglet Docs");
		turSNSiteSpotlightDocument.setTurSNSiteSpotlight(turSNSiteSpotlight);
		turSNSiteSpotlightDocument.setReferenceId("https://docs.viglet.com/");
		turSNSiteSpotlightDocument.setLink("https://docs.viglet.com/");
		turSNSiteSpotlightDocument.setType("Page");
		turSNSiteSpotlightDocumentRepository.save(turSNSiteSpotlightDocument);

		TurSNSiteSpotlightTerm turSNSiteSpotlightTerm = new TurSNSiteSpotlightTerm();
		turSNSiteSpotlightTerm.setName("sample");
		turSNSiteSpotlightTerm.setTurSNSiteSpotlight(turSNSiteSpotlight);
		turSNSiteSpotlightTermRepository.save(turSNSiteSpotlightTerm);

		TurSNSiteSpotlightTerm turSNSiteSpotlightTerm2 = new TurSNSiteSpotlightTerm();
		turSNSiteSpotlightTerm2.setName("sample2");
		turSNSiteSpotlightTerm2.setTurSNSiteSpotlight(turSNSiteSpotlight);
		turSNSiteSpotlightTermRepository.save(turSNSiteSpotlightTerm2);
	}

	public void createLocale(TurSNSite turSNSite, String username, Locale locale) {

		TurSNSiteLocale turSNSiteLocale = new TurSNSiteLocale();
		turSNSiteLocale.setLanguage(locale.toString());
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

	private void createMergeProviders(TurSNSite turSNSite) {

		TurSNSiteMergeProviders turSNSiteMerge = new TurSNSiteMergeProviders();
		turSNSiteMerge.setTurSNSite(turSNSite);
		turSNSiteMerge.setLocale(TurLocaleRepository.EN_US);
		turSNSiteMerge.setProviderFrom("Nutch");
		turSNSiteMerge.setProviderTo("WEM");
		turSNSiteMerge.setRelationFrom("id");
		turSNSiteMerge.setRelationTo("url");
		turSNSiteMerge.setDescription("Merge content from Nutch into existing WEM content.");

		turSNSiteMergeRepository.save(turSNSiteMerge);

		TurSNSiteMergeProvidersField turSNSiteMergeField = new TurSNSiteMergeProvidersField();
		turSNSiteMergeField.setName("text");
		turSNSiteMergeField.setTurSNSiteMergeProviders(turSNSiteMerge);

		turSNSiteMergeFieldRepository.save(turSNSiteMergeField);
	}
}
